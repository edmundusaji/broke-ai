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
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too many authentication attempts");
                return;
            }
        } else if (AI_ENDPOINTS.contains(requestPath)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AppUser user) {
                if (!rateLimitingService.tryConsume(user.getId())) {
                    response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "AI request limit exceeded");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthEndpoint(String requestPath, HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && AUTH_ENDPOINTS.contains(requestPath);
    }

    private String getRequestPath(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        return requestPath;
    }
}
