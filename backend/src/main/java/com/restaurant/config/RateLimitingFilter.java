package com.restaurant.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Map<String, Bucket> BUCKETS = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Limit limit = limitFor(request);
        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getRemoteAddr() + ":" + request.getRequestURI();
        Bucket bucket = BUCKETS.computeIfAbsent(key, ignored -> new Bucket(limit.maxRequests(), Instant.now().toEpochMilli()));
        if (!bucket.tryConsume(limit)) {
            response.sendError(429, "Demasiadas solicitudes. Intenta de nuevo en unos segundos.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Limit limitFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return null;
        String path = request.getRequestURI();
        if ("/api/auth/login".equals(path)) return new Limit(10, 60_000);
        if ("/api/chat/turn".equals(path)) return new Limit(30, 60_000);
        return null;
    }

    private record Limit(int maxRequests, long windowMs) {}

    private static final class Bucket {
        private int remaining;
        private long resetAt;

        private Bucket(int remaining, long createdAt) {
            this.remaining = remaining;
            this.resetAt = createdAt;
        }

        synchronized boolean tryConsume(Limit limit) {
            long now = Instant.now().toEpochMilli();
            if (now >= resetAt) {
                remaining = limit.maxRequests();
                resetAt = now + limit.windowMs();
            }
            if (remaining <= 0) return false;
            remaining--;
            return true;
        }
    }
}
