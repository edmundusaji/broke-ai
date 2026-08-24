package org.edmund.brokeai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.UserDevice;
import org.edmund.brokeai.repository.UserDeviceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationCaptureAuthenticationFilterTest {
    @InjectMocks
    private NotificationCaptureAuthenticationFilter filter;

    @Mock
    private UserDeviceRepository userDeviceRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validCredential_AuthenticatesOnlyCaptureRoleAndAttachesDeviceId() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UserDevice device = activeDevice(deviceId);
        captureRequest("Bearer bcap_valid");
        when(userDeviceRepository.findByNotificationCaptureTokenHashAndNotificationCaptureRevokedAtIsNull(anyString()))
            .thenReturn(Optional.of(device));

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(device.getUser(), SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals("ROLE_NOTIFICATION_CAPTURE", SecurityContextHolder.getContext().getAuthentication()
            .getAuthorities().iterator().next().getAuthority());
        verify(request).setAttribute(NotificationCaptureAuthenticationFilter.DEVICE_ID_ATTRIBUTE, deviceId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void revokedOrUnknownCredential_ReturnsReconnectCodeWithoutCallingController() throws Exception {
        captureRequest("Bearer bcap_revoked");
        when(userDeviceRepository.findByNotificationCaptureTokenHashAndNotificationCaptureRevokedAtIsNull(anyString()))
            .thenReturn(Optional.empty());
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertTrue(body.toString().contains("CAPTURE_CREDENTIAL_INVALID"));
        verifyNoInteractions(filterChain);
    }

    private void captureRequest(String authorization) {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/expense/notification");
        when(request.getContextPath()).thenReturn("");
        when(request.getHeader("Authorization")).thenReturn(authorization);
    }

    private UserDevice activeDevice(UUID deviceId) {
        AppUser user = new AppUser();
        user.setId(9L);
        user.setIsGuest(false);
        user.setStatus("active");
        UserDevice device = new UserDevice();
        device.setId(deviceId);
        device.setUser(user);
        device.setPlatform("android");
        device.setNotificationCaptureEnabledAt(Instant.now());
        return device;
    }
}
