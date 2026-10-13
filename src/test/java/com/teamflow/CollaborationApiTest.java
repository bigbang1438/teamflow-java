package com.teamflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.datasource.url=jdbc:h2:mem:collaboration;DB_CLOSE_DELAY=-1", "teamflow.demo-password=" + SessionClient.PASSWORD})
class CollaborationApiTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    @Test void anonymousCannotReadProjectsOrLegacyGlobalTasks() throws Exception {
        var anon = new SessionClient(port);
        assertEquals(401, anon.send("GET", "/api/v1/projects", null).statusCode());
        assertEquals(401, anon.send("GET", "/api/v1/tasks", null).statusCode());
        assertEquals(401, anon.send("GET", "/api/v1/summary", null).statusCode());
    }

    @Test void loginRotatesSessionAndLogoutInvalidatesAccess() throws Exception {
        var owner = new SessionClient(port); owner.refreshCsrf();
        String before = owner.cookies.getCookieStore().getCookies().toString();
        owner.login("demo_owner");
        assertNotEquals(before, owner.cookies.getCookieStore().getCookies().toString());
        var session = owner.send("GET", "/api/v1/auth/session", null);
        assertTrue(session.body().contains("demo_owner"));
        assertFalse(session.body().contains("password"));
        assertEquals(200, owner.send("GET", "/api/v1/projects", null).statusCode());
        assertEquals(404, owner.send("GET", "/api/v1/tasks", null).statusCode());
        assertEquals(404, owner.send("GET", "/api/v1/summary", null).statusCode());
        assertEquals(200, owner.send("POST", "/api/v1/auth/logout", null).statusCode());
        assertEquals(401, owner.send("GET", "/api/v1/projects", null).statusCode());
        assertEquals(403, owner.send("POST", "/api/v1/projects", "{\"name\":\"旧会话\"}").statusCode());
    }

    @Test void missingCsrfCannotLoginOrCreateResourcesAndWrongPasswordIsGeneric() throws Exception {
        var owner = new SessionClient(port); owner.refreshCsrf();
        assertEquals(403, owner.send("POST", "/api/v1/auth/login", "username=demo_owner&password=wrong", false, "application/x-www-form-urlencoded").statusCode());
        var wrong = owner.send("POST", "/api/v1/auth/login", "username=demo_owner&password=wrong", true, "application/x-www-form-urlencoded");
        assertEquals(401, wrong.statusCode());
        assertTrue(wrong.body().contains("invalid_credentials"));
        assertFalse(wrong.body().contains("Exception"));
        owner.login("demo_owner");
        assertEquals(403, owner.send("POST", "/api/v1/projects", "{\"name\":\"无令牌\"}", false, "application/json").statusCode());
    }

    @Test void ownerInvitesMembersAndViewerCannotWriteOrEscalatePrivilege() throws Exception {
        var owner = logged("demo_owner"); long project = owner.project("权限演示");
        owner.member(project, "demo_editor", "editor"); owner.member(project, "demo_viewer", "viewer");
        String base = "/api/v1/projects/" + project;
        var viewer = logged("demo_viewer"); var editor = logged("demo_editor");
        assertEquals(200, viewer.send("GET", base + "/tasks", null).statusCode());
        assertEquals(403, viewer.send("POST", base + "/tasks", task("禁止写入", "")).statusCode());
        assertEquals(403, viewer.send("POST", base + "/members", "{\"username\":\"demo_guest\",\"role\":\"editor\"}").statusCode());
        assertEquals(403, editor.send("PUT", base + "/members/demo_viewer", "{\"role\":\"editor\"}").statusCode());
        assertEquals(400, owner.send("PUT", base + "/members/demo_owner", "{\"role\":\"viewer\"}").statusCode());
        assertEquals(400, owner.send("POST", base + "/members", "{\"username\":\"demo_guest\",\"role\":\"owner\"}").statusCode());
        assertEquals(201, editor.send("POST", base + "/tasks", task("协作任务", "demo_editor")).statusCode());
        assertEquals(200, owner.send("PUT", base + "/members/demo_editor", "{\"role\":\"viewer\"}").statusCode());
        assertEquals(403, editor.send("POST", base + "/tasks", task("权限已撤销", "")).statusCode());
    }

    @Test void outsiderCannotGuessTaskMemberSummaryCommentOrAuditIds() throws Exception {
        var owner = logged("demo_owner"); long project = owner.project("隔离项目");
        String base = "/api/v1/projects/" + project;
        var created = owner.send("POST", base + "/tasks", task("私有任务", "demo_owner"));
        assertEquals(201, created.statusCode()); String path = created.headers().firstValue("Location").orElseThrow();
        var outsider = logged("demo_guest");
        assertFalse(outsider.send("GET", "/api/v1/projects", null).body().contains("隔离项目"));
        for (String suffix : new String[]{"/tasks", "/members", "/summary", "/activities"}) assertEquals(404, outsider.send("GET", base + suffix, null).statusCode(), suffix);
        for (String suffix : new String[]{"", "/activities", "/comments"}) assertEquals(404, outsider.send("GET", path + suffix, null).statusCode(), suffix);
        assertEquals(404, outsider.send("PATCH", path + "/status", "{\"status\":\"doing\",\"version\":0}").statusCode());
        long other = owner.project("第二项目");
        assertEquals(404, owner.send("GET", path.replace("/" + project + "/", "/" + other + "/"), null).statusCode());
    }

    @Test void assignmentsRequireWritableMembersAndAuditUsesAuthenticatedActor() throws Exception {
        var owner = logged("demo_owner"); long project = owner.project("分派与审计");
        owner.member(project, "demo_editor", "editor"); owner.member(project, "demo_viewer", "viewer");
        String base = "/api/v1/projects/" + project;
        assertEquals(400, owner.send("POST", base + "/tasks", task("外部负责人", "demo_guest")).statusCode());
        assertEquals(400, owner.send("POST", base + "/tasks", task("只读负责人", "demo_viewer")).statusCode());
        var editor = logged("demo_editor"); var created = editor.send("POST", base + "/tasks", task("有依据的分派", "demo_editor"));
        assertEquals(201, created.statusCode()); String path = created.headers().firstValue("Location").orElseThrow();
        assertEquals(201, editor.send("POST", path + "/comments", "{\"body\":\"联调通过，等待验收\"}").statusCode());
        assertEquals(400, editor.send("POST", path + "/comments", "{\"body\":\"   \"}").statusCode());
        var viewer = logged("demo_viewer");
        assertEquals(403, viewer.send("POST", path + "/comments", "{\"body\":\"不能写入\"}").statusCode());
        var comments = viewer.send("GET", path + "/comments", null);
        assertTrue(comments.body().contains("联调通过")); assertTrue(comments.body().contains("demo_editor"));
        var audit = owner.send("GET", base + "/activities", null);
        assertTrue(audit.body().contains("member_added")); assertTrue(audit.body().contains("commented")); assertTrue(audit.body().contains("demo_editor"));
        assertEquals(1, SessionClient.number(owner.send("GET", base + "/summary", null).body(), "total"));
        long empty = owner.project("空项目");
        assertEquals(0, SessionClient.number(owner.send("GET", "/api/v1/projects/" + empty + "/summary", null).body(), "total"));
    }

    @Test void databaseStoresHashesAndRejectsDuplicateMembersWithoutExtraAudit() throws Exception {
        var owner = logged("demo_owner"); long project = owner.project("哈希和重复请求");
        var hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'demo_owner'", String.class);
        assertNotEquals(SessionClient.PASSWORD, hash); assertTrue(hash.startsWith("$2"));
        owner.member(project, "demo_editor", "editor");
        assertEquals(409, owner.send("POST", "/api/v1/projects/" + project + "/members", "{\"username\":\"demo_editor\",\"role\":\"viewer\"}").statusCode());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM project_activities WHERE project_id = ?", Integer.class, project));
    }

    @Test void demotingAssignedEditorClearsResponsibilityAndRecordsTaskAuditAtomically() throws Exception {
        var owner = logged("demo_owner"); long project = owner.project("角色与分派闭环"); owner.member(project, "demo_editor", "editor");
        var created = owner.send("POST", "/api/v1/projects/" + project + "/tasks", task("已分派任务", "demo_editor"));
        String path = created.headers().firstValue("Location").orElseThrow();
        assertEquals(200, owner.send("PUT", "/api/v1/projects/" + project + "/members/demo_editor", "{\"role\":\"viewer\"}").statusCode());
        var after = owner.send("GET", path, null);
        assertTrue(after.body().contains("\"assignee\":\"\"")); assertTrue(after.body().contains("\"version\":1"));
        assertTrue(owner.send("GET", path + "/activities", null).body().contains("unassigned"));
        assertEquals(409, owner.send("PUT", path, "{\"title\":\"旧版本提交\",\"priority\":\"medium\",\"version\":0}").statusCode());
    }

    SessionClient logged(String name) throws Exception { var client = new SessionClient(port); client.login(name); return client; }
    String task(String title, String assignee) { return "{\"title\":\"" + title + "\",\"priority\":\"medium\",\"assignee\":\"" + assignee + "\"}"; }
}
