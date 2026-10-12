package com.teamflow;

import com.teamflow.ApiModels.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class TaskService {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "backlog", Set.of("doing"),
        "doing", Set.of("backlog", "blocked", "done"),
        "blocked", Set.of("doing", "backlog"),
        "done", Set.of("doing"));
    private static final Map<String, String> LABELS = Map.of(
        "backlog", "待办", "doing", "进行中", "blocked", "受阻", "done", "已完成");
    private static final RowMapper<Task> TASK_MAPPER = (rs, n) -> new Task(
        rs.getLong("id"), rs.getString("title"), rs.getString("description"), rs.getString("assignee"),
        rs.getString("priority"), rs.getString("status"), rs.getLong("version"),
        rs.getObject("due_date", LocalDate.class), rs.getString("created_at"), rs.getString("updated_at"));
    private final JdbcTemplate jdbc;
    public TaskService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Task get(long id) {
        try { return jdbc.queryForObject("SELECT * FROM tasks WHERE id = ?", TASK_MAPPER, id); }
        catch (EmptyResultDataAccessException e) { throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "任务不存在"); }
    }

    public TaskPage list(String q, String status, String priority, int page, int size) {
        String where = " WHERE 1 = 1";
        List<Object> arguments = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            where += " AND (LOWER(title) LIKE ? ESCAPE '!' OR LOWER(description) LIKE ? ESCAPE '!' OR LOWER(assignee) LIKE ? ESCAPE '!')";
            String search = "%" + q.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            arguments.addAll(List.of(search, search, search));
        }
        if (status != null) { where += " AND status = ?"; arguments.add(status); }
        if (priority != null) { where += " AND priority = ?"; arguments.add(priority); }
        long total = Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM tasks" + where, Long.class, arguments.toArray()));
        arguments.add(size);
        arguments.add((long) page * size);
        var tasks = jdbc.query("SELECT * FROM tasks" + where + " ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?", TASK_MAPPER, arguments.toArray());
        return new TaskPage(tasks, new Meta(page, size, total, (total + size - 1) / size));
    }

    @Transactional
    public Task create(TaskInput input) {
        if (input.version() != null) throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error", "新建任务不需要版本号");
        String now = Instant.now().toString();
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO tasks (title, description, assignee, priority, status, version, due_date, created_at, updated_at) VALUES (?, ?, ?, ?, 'backlog', 0, ?, ?, ?)", new String[]{"id"});
            statement.setString(1, input.title().trim());
            statement.setString(2, clean(input.description()));
            statement.setString(3, clean(input.assignee()));
            statement.setString(4, input.priority());
            statement.setObject(5, input.dueDate());
            statement.setString(6, now);
            statement.setString(7, now);
            return statement;
        }, key);
        long id = Objects.requireNonNull(key.getKey()).longValue();
        activity(id, "created", "创建任务", now);
        return get(id);
    }

    @Transactional
    public Task update(long id, TaskInput input) {
        if (input.version() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error", "编辑任务必须提供版本号");
        Task task = get(id);
        requireVersion(task, input.version());
        String now = Instant.now().toString();
        int count = jdbc.update("UPDATE tasks SET title = ?, description = ?, assignee = ?, priority = ?, due_date = ?, updated_at = ?, version = version + 1 WHERE id = ? AND version = ?",
            input.title().trim(), clean(input.description()), clean(input.assignee()), input.priority(), input.dueDate(), now, id, input.version());
        requireWritten(count);
        activity(id, "updated", "更新任务信息", now);
        return get(id);
    }

    @Transactional
    public Task changeStatus(long id, StatusInput input) {
        Task task = get(id);
        requireVersion(task, input.version());
        if (task.status().equals(input.status())) return task;
        if (!TRANSITIONS.get(task.status()).contains(input.status()))
            throw new ApiException(HttpStatus.CONFLICT, "invalid_transition", "当前状态不能直接切换到目标状态");
        String now = Instant.now().toString();
        int count = jdbc.update("UPDATE tasks SET status = ?, updated_at = ?, version = version + 1 WHERE id = ? AND version = ?", input.status(), now, id, input.version());
        requireWritten(count);
        activity(id, "status_changed", LABELS.get(task.status()) + " → " + LABELS.get(input.status()), now);
        return get(id);
    }

    public List<Activity> activities(long id) {
        get(id);
        return jdbc.query("SELECT * FROM activities WHERE task_id = ? ORDER BY id DESC LIMIT 100",
            (rs, n) -> new Activity(rs.getLong("id"), rs.getLong("task_id"), rs.getString("action"), rs.getString("detail"), rs.getString("created_at")), id);
    }

    public Summary summary() {
        var counts = new LinkedHashMap<String, Long>();
        for (String status : List.of("backlog", "doing", "blocked", "done")) counts.put(status, 0L);
        jdbc.query("SELECT status, COUNT(*) AS amount FROM tasks GROUP BY status", rs -> { counts.put(rs.getString("status"), rs.getLong("amount")); });
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long overdue = Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE due_date < ? AND status <> 'done'", Long.class, LocalDate.now(ZoneOffset.UTC)));
        double rate = total == 0 ? 0 : Math.round(counts.get("done") * 10000.0 / total) / 100.0;
        return new Summary(total, counts, overdue, rate);
    }

    private void activity(long id, String action, String detail, String now) {
        jdbc.update("INSERT INTO activities(task_id, action, detail, created_at) VALUES (?, ?, ?, ?)", id, action, detail, now);
    }
    private static String clean(String text) { return text == null ? "" : text.trim(); }
    private static void requireVersion(Task task, long expected) {
        if (task.version() != expected) conflict();
    }
    private static void requireWritten(int count) { if (count != 1) conflict(); }
    private static void conflict() { throw new ApiException(HttpStatus.CONFLICT, "version_conflict", "任务已被其他页面更新，请刷新后重试"); }
}
