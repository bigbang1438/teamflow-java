package com.teamflow;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/** Fictitious local accounts only; never embeds a usable password in source. */
@Component
public class DemoSeed implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String password;
    public DemoSeed(JdbcTemplate jdbc, PasswordEncoder encoder, @Value("${teamflow.demo-password:}") String password) {
        this.jdbc = jdbc; this.encoder = encoder; this.password = password;
    }
    @Override @Transactional public void run(ApplicationArguments ignored) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class) == 0) {
            if (password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalStateException("首次启动需要 TEAMFLOW_DEMO_PASSWORD（12–72 字符，仅用于虚拟本机账号）");
            for (String[] account : new String[][]{{"demo_owner", "演示负责人"}, {"demo_editor", "演示编辑者"}, {"demo_viewer", "演示只读成员"}, {"demo_guest", "演示外部账号"}})
                jdbc.update("INSERT INTO users(username, display_name, password_hash) VALUES (?, ?, ?)", account[0], account[1], encoder.encode(password));
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM projects", Integer.class) == 0) {
            long owner = jdbc.queryForObject("SELECT id FROM users WHERE username = 'demo_owner'", Long.class);
            jdbc.update("INSERT INTO projects(name, owner_id, created_at) VALUES (?, ?, ?)", "演示协作项目", owner, Instant.now().toString());
            long project = jdbc.queryForObject("SELECT id FROM projects", Long.class);
            for (String[] member : new String[][]{{"demo_owner", "owner"}, {"demo_editor", "editor"}, {"demo_viewer", "viewer"}})
                jdbc.update("INSERT INTO project_members(project_id, user_id, role) SELECT ?, id, ? FROM users WHERE username = ?", project, member[1], member[0]);
        }
        // Additive migration: preserve original local tasks but do not reinterpret free-text assignees as identities.
        long initialProject = jdbc.queryForObject("SELECT MIN(id) FROM projects", Long.class);
        jdbc.update("UPDATE tasks SET project_id = ?, assignee = '' WHERE project_id IS NULL", initialProject);
    }
}
