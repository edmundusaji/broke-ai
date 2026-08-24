package org.edmund.brokeai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.UserDevice;
import org.edmund.brokeai.repository.UserDeviceRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationCaptureAuthenticationFilter extends OncePerRequestFilter {
    public static final String DEVICE_ID_ATTRIBUTE = "broke.notificationCaptureDeviceId";
    private static final String PATH = "/api/v1/expense/notification";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CREDENTIAL_PREFIX = "bcap_";

    private final UserDeviceRepository userDeviceRepository;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (!isCaptureRequest(request, authorization)) {
            filterChain.doFilter(request, response);
            return;
        }

        String credential = authorization.substring(BEARER_PREFIX.length());
        UserDevice device = userDeviceRepository
            .findByNotificationCaptureTokenHashAndNotificationCaptureRevokedAtIsNull(sha256(credential))
            .filter(this::isEligible)
            .orElse(null);
        if (device == null) {
            writeInvalidCredential(response);
            return;
        }

        AppUser user = device.getUser();
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_NOTIFICATION_CAPTURE"))
            );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(DEVICE_ID_ATTRIBUTE, device.getId());
        filterChain.doFilter(request, response);
    }

    private boolean isCaptureRequest(HttpServletRequest request, String authorization) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !PATH.equals(requestPath)) return false;
        if (SecurityContextHolder.getContext().getAuthentication() != null) return false;
        return authorization != null
            && authorization.startsWith(BEARER_PREFIX + CREDENTIAL_PREFIX);
    }

    private boolean isEligible(UserDevice device) {
        AppUser user = device.getUser();
        return "android".equals(device.getPlatform())
            && device.getNotificationCaptureEnabledAt() != null
            && user != null
            && !Boolean.TRUE.equals(user.getIsGuest())
            && "active".equals(user.getStatus())
            && user.getDeletedAt() == null;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void writeInvalidCredential(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
            "{\"error\":{\"code\":\"CAPTURE_CREDENTIAL_INVALID\",\"message\":" +
                "\"Open Broke.AI to reconnect notification capture.\",\"field\":null,\"requestId\":\"" +
                UUID.randomUUID() + "\"}}"
        );
    }
}
