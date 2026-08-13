package org.edmund.brokeai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.service.RateLimitingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AiRateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> AI_ENDPOINTS = Set.of(
        "/api/v1/expense/receipt",
        "/api/v1/expense/notification"
    );
    private static final Set<String> AUTH_ENDPOINTS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/guest-login"
    );
    private static final Set<String> SENSITIVE_ENDPOINTS = Set.of(
        "/api/v1/me/password/change",
        "/api/v1/me/email-change",
        "/api/v1/me/email-change/verify",
        "/api/v1/me/data-exports",
        "/api/v1/me/transactions/clear",
        "/api/v1/me/deletion-request",
        "/api/v1/support/tickets"
    );

    private final RateLimitingService rateLimitingService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String requestPath = getRequestPath(request);
        if (isAuthEndpoint(requestPath, request)) {
            if (!rateLimitingService.tryConsumeAuth(request.getRemoteAddr())) {
                writeRateLimitError(response, "Too many requests. Try again later.");
                return;
            }
        } else if (isSensitiveEndpoint(requestPath, request)) {
            if (!rateLimitingService.tryConsumeSensitive(request.getRemoteAddr(), requestPath)) {
                writeRateLimitError(response, "Too many requests. Try again later.");
                return;
            }
        } else if (AI_ENDPOINTS.contains(requestPath)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AppUser user) {
                if (!rateLimitingService.tryConsume(user.getId())) {
                    writeRateLimitError(response, "AI request limit exceeded.");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthEndpoint(String requestPath, HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && AUTH_ENDPOINTS.contains(requestPath);
    }

    private boolean isSensitiveEndpoint(String requestPath, HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && SENSITIVE_ENDPOINTS.contains(requestPath);
    }

    private String getRequestPath(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        return requestPath;
    }

    private void writeRateLimitError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write(
            "{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"" + message + "\"," +
                "\"field\":null,\"requestId\":\"" + UUID.randomUUID() + "\"}}"
        );
    }
}
