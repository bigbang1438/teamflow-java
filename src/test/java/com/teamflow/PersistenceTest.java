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
        var client = HttpClient.newHttpClient();
        String database = "jdbc:h2:file:" + directory.resolve("restart-db").toAbsolutePath().toString().replace('\\', '/');
        String path;
        try (var context = start(database)) {
            String origin = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
            var request = HttpRequest.newBuilder(URI.create(origin + "/api/v1/tasks"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"重启后保留的任务\",\"priority\":\"medium\"}"))
                .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, response.statusCode());
            path = response.headers().firstValue("Location").orElseThrow();
        }
        try (var context = start(database)) {
            String origin = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
            var task = client.send(HttpRequest.newBuilder(URI.create(origin + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, task.statusCode());
            assertTrue(task.body().contains("重启后保留的任务"));
            var activities = client.send(HttpRequest.newBuilder(URI.create(origin + path + "/activities")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertTrue(activities.body().contains("\"action\":\"created\""));
        }
    }

    org.springframework.context.ConfigurableApplicationContext start(String database) {
        return new SpringApplicationBuilder(TeamFlowApplication.class).run(
            "--server.port=0", "--server.address=127.0.0.1", "--spring.datasource.url=" + database,
            "--spring.main.banner-mode=off");
    }
}
