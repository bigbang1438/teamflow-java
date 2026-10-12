package com.teamflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.datasource.url=jdbc:h2:mem:task-api;DB_CLOSE_DELAY=-1"})
class TaskApiTest {
    @LocalServerPort int port;
    final HttpClient client = HttpClient.newHttpClient();

    @Test void createTaskPersistsBacklogAndReturnsLocation() throws Exception {
        var response = send("POST", "/api/v1/tasks", """
            {"title":"实现任务看板","description":"课程演示","assignee":"演示用户","priority":"high","dueDate":"2027-01-10"}
            """);
        assertEquals(201, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElse("").startsWith("/api/v1/tasks/"));
        assertTrue(response.body().contains("\"status\":\"backlog\""));
        assertTrue(response.body().contains("\"version\":0"));
        var read = send("GET", response.headers().firstValue("Location").orElseThrow(), null);
        assertEquals(200, read.statusCode());
        assertTrue(read.body().contains("实现任务看板"));
    }

    @Test void updateUsesVersionAndRejectsStaleWriterWithoutOverwriting() throws Exception {
        String path = create("版本测试");
        var first = send("PUT", path, taskBody("更新后的标题", 0));
        assertEquals(200, first.statusCode());
        assertTrue(first.body().contains("\"version\":1"));
        var stale = send("PUT", path, taskBody("过时标题", 0));
        assertEquals(409, stale.statusCode());
        assertTrue(stale.body().contains("version_conflict"));
        assertTrue(send("GET", path, null).body().contains("更新后的标题"));
    }

    @Test void rejectsInvalidJumpAndAllowsCompleteBlockedWorkflow() throws Exception {
        String path = create("状态测试");
        var invalid = transition(path, "done", 0);
        assertEquals(409, invalid.statusCode());
        assertTrue(invalid.body().contains("invalid_transition"));
        assertEquals(200, transition(path, "doing", 0).statusCode());
        assertEquals(200, transition(path, "blocked", 1).statusCode());
        assertEquals(200, transition(path, "doing", 2).statusCode());
        assertEquals(200, transition(path, "done", 3).statusCode());
        assertEquals(200, transition(path, "doing", 4).statusCode());
        assertTrue(send("GET", path, null).body().contains("\"version\":5"));
    }

    @Test void repeatedCurrentStatusDoesNotIncrementVersionOrAppendActivity() throws Exception {
        String path = create("重复状态");
        var response = transition(path, "backlog", 0);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"version\":0"));
        assertEquals(1, occurrences(send("GET", path + "/activities", null).body(), "\"action\":"));
    }

    @Test void invalidPayloadAndDateNeverCreateTasks() throws Exception {
        assertEquals(400, send("POST", "/api/v1/tasks", "{\"title\":\" \",\"priority\":\"high\"}").statusCode());
        assertEquals(400, send("POST", "/api/v1/tasks", "{\"title\":\"任务\",\"priority\":\"urgent\"}").statusCode());
        assertEquals(400, send("POST", "/api/v1/tasks", "{\"title\":\"任务\",\"priority\":\"high\",\"dueDate\":\"bad-date\"}").statusCode());
        assertEquals(400, send("POST", "/api/v1/tasks", "{\"title\":\"" + "a".repeat(121) + "\",\"priority\":\"high\"}").statusCode());
        assertEquals(400, send("POST", "/api/v1/tasks", "{\"title\":\"未知字段\",\"priority\":\"high\",\"admin\":true}").statusCode());
    }

    @Test void filteringPaginationAndLiteralSearchReturnAccurateTotals() throws Exception {
        String marker = "search-" + System.nanoTime();
        create(marker + " 100% literal");
        create(marker + " ordinary");
        var filtered = send("GET", "/api/v1/tasks?q=" + marker + "&status=backlog&priority=medium&page=0&size=1", null);
        assertEquals(200, filtered.statusCode());
        assertTrue(filtered.body().contains("\"total\":2"));
        assertTrue(filtered.body().contains("\"totalPages\":2"));
        assertEquals(1, occurrences(filtered.body(), "\"title\":"));
        var literal = send("GET", "/api/v1/tasks?q=" + marker + "%20100%25", null);
        assertEquals(200, literal.statusCode());
        assertTrue(literal.body().contains("\"total\":1"));
        assertEquals(400, send("GET", "/api/v1/tasks?status=unknown", null).statusCode());
        assertEquals(400, send("GET", "/api/v1/tasks?size=10001", null).statusCode());
        assertEquals(400, send("GET", "/api/v1/tasks?page=-1", null).statusCode());
        assertEquals(200, send("GET", "/api/v1/tasks?q=%27%20OR%201%3D1--", null).statusCode());
        assertTrue(send("GET", "/api/v1/tasks?q=%27%20OR%201%3D1--", null).body().contains("\"total\":0"));
    }

    @Test void activitiesCaptureOnlySuccessfulWritesInReverseOrder() throws Exception {
        String path = create("活动测试");
        assertEquals(200, send("PUT", path, taskBody("修改信息", 0)).statusCode());
        assertEquals(200, transition(path, "doing", 1).statusCode());
        assertEquals(409, transition(path, "blocked", 0).statusCode());
        var activities = send("GET", path + "/activities", null);
        assertEquals(200, activities.statusCode());
        assertEquals(3, occurrences(activities.body(), "\"action\":"));
        assertTrue(activities.body().indexOf("status_changed") < activities.body().indexOf("created"));
    }

    @Test void parallelWritersProduceExactlyOneSuccess() throws Exception {
        String path = create("并发版本测试");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> send("PUT", path, taskBody("并发 A", 0)).statusCode());
            var b = executor.submit(() -> send("PUT", path, taskBody("并发 B", 0)).statusCode());
            var statuses = java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertTrue(statuses.contains(200));
            assertTrue(statuses.contains(409));
            assertTrue(send("GET", path, null).body().contains("\"version\":1"));
            assertEquals(2, occurrences(send("GET", path + "/activities", null).body(), "\"action\":"));
        }
    }

    @Test void summaryCountsOverdueAndCompletedTasks() throws Exception {
        var before = send("GET", "/api/v1/summary", null);
        assertEquals(200, before.statusCode());
        var created = send("POST", "/api/v1/tasks", "{\"title\":\"逾期统计\",\"priority\":\"high\",\"dueDate\":\"2000-01-01\"}");
        assertEquals(201, created.statusCode());
        String path = created.headers().firstValue("Location").orElseThrow();
        var middle = send("GET", "/api/v1/summary", null);
        assertEquals(number(before.body(), "total") + 1, number(middle.body(), "total"));
        assertEquals(number(before.body(), "overdue") + 1, number(middle.body(), "overdue"));
        assertEquals(200, transition(path, "doing", 0).statusCode());
        assertEquals(200, transition(path, "done", 1).statusCode());
        var after = send("GET", "/api/v1/summary", null);
        assertEquals(number(before.body(), "overdue"), number(after.body(), "overdue"));
        assertEquals(number(before.body(), "done") + 1, number(after.body(), "done"));
        assertTrue(after.body().contains("\"completionRate\":"));
    }

    @Test void missingResourceErrorsAreSafeAndSecurityHeadersArePresent() throws Exception {
        var response = send("GET", "/api/v1/tasks/999999999", null);
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("not_found"));
        assertFalse(response.body().contains("Exception"));
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertTrue(response.headers().firstValue("Content-Security-Policy").orElse("").contains("frame-ancestors 'none'"));
    }

    @Test void crossOriginMutationIsRejected() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/tasks"))
            .header("Origin", "https://unrelated.example")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(taskBody("跨源", null))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
    }

    @Test void protocolErrorsUseClientStatusesWithoutInternalDetails() throws Exception {
        assertEquals(405, send("DELETE", "/api/v1/tasks", null).statusCode());
        assertEquals(404, send("GET", "/api/v1/nonexistent", null).statusCode());
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/tasks"))
            .header("Content-Type", "text/plain")
            .POST(HttpRequest.BodyPublishers.ofString("hello")).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(415, response.statusCode());
        assertFalse(response.body().contains("Exception"));
    }

    String create(String title) throws Exception {
        var response = send("POST", "/api/v1/tasks", taskBody(title, null));
        assertEquals(201, response.statusCode(), response.body());
        return response.headers().firstValue("Location").orElseThrow();
    }
    String taskBody(String title, Integer version) {
        return "{\"title\":\"" + title + "\",\"description\":\"测试数据\",\"assignee\":\"演示用户\",\"priority\":\"medium\",\"dueDate\":null" +
            (version == null ? "" : ",\"version\":" + version) + "}";
    }
    HttpResponse<String> transition(String path, String status, int version) throws Exception {
        return send("PATCH", path + "/status", "{\"status\":\"" + status + "\",\"version\":" + version + "}");
    }
    int occurrences(String body, String token) { return (body.length() - body.replace(token, "").length()) / token.length(); }
    long number(String body, String key) {
        var matcher = java.util.regex.Pattern.compile("\\\"" + key + "\\\":(\\d+)").matcher(body);
        assertTrue(matcher.find(), body);
        return Long.parseLong(matcher.group(1));
    }

    HttpResponse<String> send(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
