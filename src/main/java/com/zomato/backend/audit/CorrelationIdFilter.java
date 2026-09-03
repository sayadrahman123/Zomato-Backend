package com.zomato.backend.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that manages request correlation tracing and MDC diagnostics.
 * <p>
 * Ensures every incoming HTTP request:
 * <ul>
 *   <li>Carries or receives a unique {@code X-Correlation-ID}.</li>
 *   <li>Populates SLF4J's {@link MDC} so all downstream logs include the correlation ID and client IP.</li>
 *   <li>Emits structured access logs tracking request URI, HTTP status, and duration in ms.</li>
 *   <li>Cleans up MDC in a {@code finally} block to prevent Tomcat worker thread leakage.</li>
 * </ul>
 */
@Slf4j
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_CORRELATION_ID    = "correlationId";
    public static final String MDC_CLIENT_IP         = "clientIp";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // ── 1. Resolve Correlation ID ─────────────────────────────────────────
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        String clientIp = extractClientIp(request);

        // ── 2. Populate MDC ───────────────────────────────────────────────────
        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_CLIENT_IP, clientIp);

        // ── 3. Attach header to outgoing response ─────────────────────────────
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // Avoid noisy access logs on health check polls
        boolean isHealthCheck = uri.startsWith("/actuator/health");
        if (!isHealthCheck) {
            log.info("HTTP IN  {} {}", method, uri);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            if (!isHealthCheck) {
                log.info("HTTP OUT {} {} status={} ({}ms)", method, uri, response.getStatus(), duration);
            }
            // Hygiene: clear thread-local MDC
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_CLIENT_IP);
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
