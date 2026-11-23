package com.perde.infrastructure.web;

import com.perde.infrastructure.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Yazma isteklerini (koltuk tutma, bırakma, satın alma) IP başına dakikalık sabit pencereyle sınırlar.
 * Tek sunucu için yeterli, bellek içi bir korumadır; çok sunuculu kurulumda ortak bir depo gerekir.
 * İstemci IP'si doğrudan bağlantıdan alınır: X-Forwarded-For başlığına güvenilmez, çünkü taklit edilebilir.
 */
@Component
class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final int limit;
    private final Clock clock;
    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private volatile long window = -1;

    RateLimitFilter(AppProperties props, Clock clock) {
        this.limit = props.rateLimit().requestsPerMinute();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        boolean write = method.equals("PUT") || method.equals("POST") || method.equals("DELETE");
        return !write || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long currentWindow = clock.millis() / 60_000;
        if (currentWindow != window) {
            synchronized (this) {
                if (currentWindow != window) {
                    counts.clear();
                    window = currentWindow;
                }
            }
        }
        if (counts.size() > MAX_TRACKED_CLIENTS) {
            counts.clear();   // bellek sınırı: saldırı altında sayaçlar sıfırlanır, sunucu ayakta kalır
        }
        int used = counts.computeIfAbsent(request.getRemoteAddr(), k -> new AtomicInteger()).incrementAndGet();
        if (used > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"status\":429,\"code\":\"RATE_LIMITED\",\"detail\":\"Çok hızlı işlem yapıyorsun. Bir dakika sonra tekrar dene.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
