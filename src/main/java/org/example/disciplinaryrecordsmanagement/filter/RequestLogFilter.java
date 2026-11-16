package org.example.disciplinaryrecordsmanagement.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import org.springframework.web.servlet.HandlerMapping;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.ROOT;
import static org.example.disciplinaryrecordsmanagement.constant.RequestConstants.REQUEST_ID_HEADER;
import static org.example.disciplinaryrecordsmanagement.constant.RequestConstants.REQUEST_ID_KEY;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RequestLogFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        long start = System.nanoTime();
        MDC.put(REQUEST_ID_KEY, id);
        response.setHeader(REQUEST_ID_HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            if (request.getRequestURI().startsWith(ROOT + "/")) {
                Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                LOG.info("method={} route={} status={} durationMs={}", request.getMethod(),
                        route == null ? "(filter)" : route, response.getStatus(), (System.nanoTime() - start) / 1_000_000);
            }
            MDC.remove(REQUEST_ID_KEY);
        }
    }
}
