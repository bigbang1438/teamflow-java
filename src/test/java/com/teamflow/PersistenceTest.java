package com.teamflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @TempDir Path directory;

    @Test void fileDatabaseRetainsTaskAndActivityAfterApplicationRestart() throws Exception {
        String database = "jdbc:h2:file:" + directory.resolve("restart-db").toAbsolutePath().toString().replace('\\', '/');
        String path;
        try (var context = start(database)) {
            var client = new SessionClient(Integer.parseInt(context.getEnvironment().getProperty("local.server.port"))); client.login("demo_owner");
            long project = client.project("重启项目"); client.member(project, "demo_editor", "editor");
            var response = client.send("POST", "/api/v1/projects/" + project + "/tasks", "{\"title\":\"重启后保留的任务\",\"priority\":\"medium\",\"assignee\":\"demo_editor\"}");
            assertEquals(201, response.statusCode());
            path = response.headers().firstValue("Location").orElseThrow();
        }
        try (var context = start(database)) {
            var client = new SessionClient(Integer.parseInt(context.getEnvironment().getProperty("local.server.port"))); client.login("demo_editor");
            var task = client.send("GET", path, null);
            assertEquals(200, task.statusCode());
            assertTrue(task.body().contains("重启后保留的任务"));
            assertTrue(task.body().contains("demo_editor"));
            var activities = client.send("GET", path + "/activities", null);
            assertTrue(activities.body().contains("\"action\":\"created\""));
        }
    }

    org.springframework.context.ConfigurableApplicationContext start(String database) {
        return new SpringApplicationBuilder(TeamFlowApplication.class).run(
            "--server.port=0", "--server.address=127.0.0.1", "--spring.datasource.url=" + database,
            "--teamflow.demo-password=" + SessionClient.PASSWORD,
            "--spring.main.banner-mode=off");
    }
}
