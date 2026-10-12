package com.teamflow;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.util.Set;

/** Local demo boundary: no login; never expose this service on a public interface. */
@Component
public class LocalSecurityFilter extends OncePerRequestFilter {
    private static final Set<String> LOCAL_HOSTS = Set.of("127.0.0.1", "localhost");
    private static final Set<String> MUTATIONS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'");
        if (!LOCAL_HOSTS.contains(request.getServerName())) {
            reject(response, 403, "invalid_host", "仅允许本机访问");
            return;
        }
        if (MUTATIONS.contains(request.getMethod()) && request.getHeader("Origin") != null && !sameOrigin(request)) {
            reject(response, 403, "origin_rejected", "不允许跨来源写入");
            return;
        }
        if (request.getContentLengthLong() > 16384) {
            reject(response, 413, "request_too_large", "请求内容超过大小限制");
            return;
        }
        chain.doFilter(request, response);
    }
    private boolean sameOrigin(HttpServletRequest request) {
        try {
            URI origin = URI.create(request.getHeader("Origin"));
            int originPort = origin.getPort() == -1 ? ("https".equals(origin.getScheme()) ? 443 : 80) : origin.getPort();
            return request.getScheme().equals(origin.getScheme()) && request.getServerName().equals(origin.getHost()) && request.getServerPort() == originPort;
        } catch (IllegalArgumentException exception) { return false; }
    }
    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"details\":[]}}");
    }
}
