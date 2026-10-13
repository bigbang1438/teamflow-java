package com.teamflow;

import com.teamflow.ApiModels.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
public class ProjectService {
    private final JdbcTemplate jdbc;
    public ProjectService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Project> list(String actor) {
        return jdbc.query("SELECT p.*, m.role FROM projects p JOIN project_members m ON m.project_id = p.id JOIN users u ON u.id = m.user_id WHERE u.username = ? ORDER BY p.id DESC",
            (rs, n) -> new Project(rs.getLong("id"), rs.getString("name"), rs.getString("role"), rs.getString("created_at")), actor);
    }
    @Transactional public Project create(String actor, ProjectInput input) {
        long user = userId(actor); String now = Instant.now().toString(); var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO projects(name, owner_id, created_at) VALUES (?, ?, ?)", new String[]{"id"});
            statement.setString(1, input.name().trim()); statement.setLong(2, user); statement.setString(3, now); return statement;
        }, key);
        long project = key.getKey().longValue();
        jdbc.update("INSERT INTO project_members(project_id, user_id, role) VALUES (?, ?, 'owner')", project, user);
        audit(project, null, actor, "project_created", "创建项目", now);
        return new Project(project, input.name().trim(), "owner", now);
    }
    public String require(long project, String actor, boolean write) {
        // Local-scale tradeoff: serialize mutations per project, so role revocation and assignment
        // cannot race. Task versions still reject stale clients and conditional writes prevent lost updates.
        if (write) jdbc.query("SELECT id FROM projects WHERE id = ? FOR UPDATE", (rs, n) -> rs.getLong("id"), project);
        var roles = jdbc.query("SELECT m.role FROM project_members m JOIN users u ON u.id = m.user_id WHERE m.project_id = ? AND u.username = ?" + (write ? " FOR UPDATE" : ""),
            (rs, n) -> rs.getString("role"), project, actor);
        if (roles.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "项目不存在或不可访问");
        String role = roles.getFirst();
        if (write && role.equals("viewer")) throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "只读成员不能修改项目内容");
        return role;
    }
    private void requireOwner(long project, String actor) {
        if (!require(project, actor, true).equals("owner")) throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "仅项目负责人可管理成员");
    }
    public List<Member> members(long project, String actor) {
        require(project, actor, false);
        return jdbc.query("SELECT u.id, u.username, u.display_name, m.role FROM project_members m JOIN users u ON m.user_id = u.id WHERE m.project_id = ? ORDER BY u.id",
            (rs, n) -> new Member(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getString("role")), project);
    }
    @Transactional public Member addMember(long project, String actor, MemberInput input) {
        requireOwner(project, actor); long user = userId(input.username());
        try { jdbc.update("INSERT INTO project_members(project_id, user_id, role) VALUES (?, ?, ?)", project, user, input.role()); }
        catch (DuplicateKeyException exception) { throw new ApiException(HttpStatus.CONFLICT, "member_exists", "该账号已加入项目"); }
        audit(project, null, actor, "member_added", "添加成员 " + input.username() + "（" + input.role() + "）", Instant.now().toString());
        return members(project, actor).stream().filter(member -> member.username().equals(input.username())).findFirst().orElseThrow();
    }
    @Transactional public Member changeRole(long project, String actor, String username, RoleInput input) {
        requireOwner(project, actor); long user = userId(username);
        var roles = jdbc.query("SELECT role FROM project_members WHERE project_id = ? AND user_id = ? FOR UPDATE", (rs, n) -> rs.getString("role"), project, user);
        if (roles.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "成员不存在");
        if (roles.getFirst().equals("owner")) throw new ApiException(HttpStatus.BAD_REQUEST, "owner_required", "项目负责人角色不能修改");
        if (!roles.getFirst().equals(input.role())) {
            jdbc.update("UPDATE project_members SET role = ? WHERE project_id = ? AND user_id = ?", input.role(), project, user);
            // A reader cannot remain responsible for writable work; record both effects atomically.
            if (input.role().equals("viewer")) {
                var taskIds = jdbc.query("SELECT id FROM tasks WHERE project_id = ? AND assignee = ?", (rs, n) -> rs.getLong("id"), project, username);
                String now = Instant.now().toString();
                jdbc.update("UPDATE tasks SET assignee = '', version = version + 1, updated_at = ? WHERE project_id = ? AND assignee = ?", now, project, username);
                for (long task : taskIds) {
                    String detail = "成员 " + username + " 改为只读，清除任务分派";
                    jdbc.update("INSERT INTO activities(task_id, actor, action, detail, created_at) VALUES (?, ?, 'unassigned', ?, ?)", task, actor, detail, now);
                    audit(project, task, actor, "unassigned", detail, now);
                }
            }
            audit(project, null, actor, "member_role_changed", "成员 " + username + "：" + roles.getFirst() + " → " + input.role(), Instant.now().toString());
        }
        return members(project, actor).stream().filter(member -> member.username().equals(username)).findFirst().orElseThrow();
    }
    public void requireAssignee(long project, String assignee) {
        if (assignee == null || assignee.isBlank()) return;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM project_members m JOIN users u ON m.user_id = u.id WHERE m.project_id = ? AND u.username = ? AND m.role IN ('owner', 'editor')", Integer.class, project, assignee.trim());
        if (count == null || count != 1) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_assignee", "负责人必须是当前项目的负责人或编辑者");
    }
    public List<ProjectActivity> activities(long project, String actor) {
        require(project, actor, false);
        return jdbc.query("SELECT * FROM project_activities WHERE project_id = ? ORDER BY id DESC LIMIT 100",
            (rs, n) -> new ProjectActivity(rs.getLong("id"), rs.getObject("task_id", Long.class), rs.getString("actor"), rs.getString("action"), rs.getString("detail"), rs.getString("created_at")), project);
    }
    void audit(long project, Long task, String actor, String action, String detail, String now) {
        jdbc.update("INSERT INTO project_activities(project_id, task_id, actor, action, detail, created_at) VALUES (?, ?, ?, ?, ?, ?)", project, task, actor, action, detail, now);
    }
    private long userId(String username) {
        var ids = jdbc.query("SELECT id FROM users WHERE username = ?", (rs, n) -> rs.getLong("id"), username);
        if (ids.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "unknown_account", "账号不存在，请使用已配置的演示账号");
        return ids.getFirst();
    }
}
