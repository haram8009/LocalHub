package com.enjoytrip;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * 프론트(Vue)를 jar 의 static/ 에 담아 함께 배포할 때만 활성화됩니다.
 * 파일이 아닌 화면 경로(/posts/1 등)는 index.html 로 넘기고(Vue Router history 모드),
 * 해시가 붙은 정적 자원은 오래 캐시합니다. /api, /actuator 는 건드리지 않습니다.
 */
@Component
@ConditionalOnResource(resources = "classpath:/static/index.html")
public class SpaFallbackFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/assets/")) {
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        } else if (isSpaRoute(request.getMethod(), path)) {
            response.setHeader("Cache-Control", "no-cache");
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean isSpaRoute(String method, String path) {
        if (!"GET".equals(method) && !"HEAD".equals(method)) return false;
        if (path.equals("/api") || path.startsWith("/api/") || path.startsWith("/actuator")) return false;
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !last.contains(".");
    }
}
