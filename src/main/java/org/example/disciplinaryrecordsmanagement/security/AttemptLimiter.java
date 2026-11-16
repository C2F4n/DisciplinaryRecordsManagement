package org.example.disciplinaryrecordsmanagement.security;

import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.example.disciplinaryrecordsmanagement.config.BusinessPolicy;
import org.example.disciplinaryrecordsmanagement.exception.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.BIND;
import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.BIND_EMAIL_CODE;
import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.LOGIN;
import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.PASSWORD;
import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;

public final class AttemptLimiter extends OncePerRequestFilter {
    private final Map<String, Integer> counts = new HashMap<>();
    private final SecurityResponseWriter responses;
    private final BusinessPolicy policy;
    private final Clock clock;
    private long windowEnd;

    public AttemptLimiter(SecurityResponseWriter responses, BusinessPolicy policy, Clock clock) {
        this.responses = responses;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        boolean authentication = HttpMethod.POST.matches(req.getMethod())
                && (LOGIN.equals(path) || (ROOT + BIND).equals(path) || (ROOT + BIND_EMAIL_CODE).equals(path));
        boolean passwordChange = HttpMethod.PUT.matches(req.getMethod()) && (ROOT + PASSWORD).equals(path);
        if (authentication || passwordChange) {
            long retryAfter = retryAfterSeconds(req.getRemoteAddr());
            if (retryAfter > 0) {
                res.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
                responses.error(res, ErrorCode.TOO_MANY_REQUESTS);
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private synchronized long retryAfterSeconds(String address) {
        long now = clock.millis();
        if (now >= windowEnd) {
            counts.clear();
            windowEnd = now + policy.loginWindow().toMillis();
        }
        int count = counts.getOrDefault(address, 0);
        if ((count == 0 && counts.size() >= policy.loginMaxTrackedSources()) || count >= policy.loginAttemptsPerIp()) {
            return Math.ceilDiv(windowEnd - now, 1000);
        }
        counts.put(address, count + 1);
        return 0;
    }
}
