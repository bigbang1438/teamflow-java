package com.teamflow;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP browser-like session helper; no production authentication bypass. */
final class SessionClient {
    static final String PASSWORD = "Test-Only-Collaboration-2026!";
    final String origin;
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
    String csrf;
    SessionClient(int port) { origin = "http://127.0.0.1:" + port; }

    HttpResponse<String> send(String method, String path, String body) throws Exception {
        return send(method, path, body, true, "application/json");
    }
    HttpResponse<String> send(String method, String path, String body, boolean token, String contentType) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(origin + path)).header("Content-Type", contentType);
        if (token && csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    void refreshCsrf() throws Exception {
        var result = send("GET", "/api/v1/auth/session", null);
        assertEquals(200, result.statusCode(), result.body());
        csrf = text(result.body(), "token");
    }
    void login(String username) throws Exception {
        refreshCsrf();
        var result = send("POST", "/api/v1/auth/login", "username=" + username + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8), true, "application/x-www-form-urlencoded");
        assertEquals(200, result.statusCode(), result.body());
        refreshCsrf();
    }
    long project(String name) throws Exception {
        var result = send("POST", "/api/v1/projects", "{\"name\":\"" + name + "\"}");
        assertEquals(201, result.statusCode(), result.body());
        return number(result.body(), "id");
    }
    void member(long project, String username, String role) throws Exception {
        var result = send("POST", "/api/v1/projects/" + project + "/members", "{\"username\":\"" + username + "\",\"role\":\"" + role + "\"}");
        assertEquals(201, result.statusCode(), result.body());
    }
    static String text(String body, String key) {
        var m = Pattern.compile("\\\"" + key + "\\\":\\\"([^\\\"]*)\\\"").matcher(body);
        assertTrue(m.find(), body); return m.group(1);
    }
    static long number(String body, String key) {
        var m = Pattern.compile("\\\"" + key + "\\\":(\\d+)").matcher(body);
        assertTrue(m.find(), body); return Long.parseLong(m.group(1));
    }
}
