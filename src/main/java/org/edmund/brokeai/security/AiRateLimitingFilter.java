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

    private final RateLimitingService rateLimitingService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (isAiEndpoint(request)) {
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

    private boolean isAiEndpoint(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        return AI_ENDPOINTS.contains(requestPath);
    }
}
