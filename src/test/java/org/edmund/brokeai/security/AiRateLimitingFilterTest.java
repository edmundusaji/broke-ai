package org.edmund.brokeai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.service.RateLimitingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiRateLimitingFilterTest {

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private AiRateLimitingFilter filter;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_StringPrincipal_SkipsRateLimiting() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/expense/receipt");
        when(request.getContextPath()).thenReturn("");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null)
        );

        filter.doFilterInternal(request, response, filterChain);

        // A non-user principal must pass without consuming a user quota.
        verify(rateLimitingService, never()).tryConsume(anyLong());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WithValidContextPath_StripsContextPathAndAppliesLimit() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/brokeai/api/v1/expense/receipt");
        when(request.getContextPath()).thenReturn("/brokeai");

        AppUser mockUser = new AppUser();
        mockUser.setId(99L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(mockUser, null)
        );

        when(rateLimitingService.tryConsume(99L)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(rateLimitingService).tryConsume(99L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ContextPathNull_EvaluatesNormally() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/expense/receipt");
        when(request.getContextPath()).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ContextPathDoesNotMatchRequestUri_SkipsAiEndpoint() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/expense/receipt");
        when(request.getContextPath()).thenReturn("/random-path");

        filter.doFilterInternal(request, response, filterChain);

        verify(rateLimitingService, never()).tryConsume(anyLong());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_RateLimitExceeded_Returns429() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/expense/receipt");
        when(request.getContextPath()).thenReturn("");

        AppUser mockUser = new AppUser();
        mockUser.setId(99L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(mockUser, null)
        );

        when(rateLimitingService.tryConsume(99L)).thenReturn(false);
        StringWriter body = responseBody();

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        org.junit.jupiter.api.Assertions.assertTrue(body.toString().contains("RATE_LIMITED"));
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_NotAiEndpoint_SkipsRateLimiting() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/users/profile");
        when(request.getContextPath()).thenReturn("");

        filter.doFilterInternal(request, response, filterChain);

        verify(rateLimitingService, never()).tryConsume(anyLong());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_AuthenticationEndpoint_AppliesIpRateLimit() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getContextPath()).thenReturn("");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");
        when(rateLimitingService.tryConsumeAuth("203.0.113.9")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(rateLimitingService).tryConsumeAuth("203.0.113.9");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_AuthenticationLimitExceeded_Returns429() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/register");
        when(request.getContextPath()).thenReturn("");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");
        when(rateLimitingService.tryConsumeAuth("203.0.113.9")).thenReturn(false);
        StringWriter body = responseBody();

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        org.junit.jupiter.api.Assertions.assertTrue(body.toString().contains("RATE_LIMITED"));
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_GuestLoginEndpoint_AppliesIpRateLimit() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/guest-login");
        when(request.getContextPath()).thenReturn("");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(rateLimitingService.tryConsumeAuth("203.0.113.10")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(rateLimitingService).tryConsumeAuth("203.0.113.10");
        verify(filterChain).doFilter(request, response);
    }

    private StringWriter responseBody() throws IOException {
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        return body;
    }
}
