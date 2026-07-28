package org.edmund.brokeai.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityConfigTest {

    private SecurityConfig securityConfig;
    private JwtAuthenticationFilter jwtFilter;
    private AiRateLimitingFilter aiFilter;

    @BeforeEach
    void setUp() {
        jwtFilter = mock(JwtAuthenticationFilter.class);
        aiFilter = mock(AiRateLimitingFilter.class);

        securityConfig = new SecurityConfig(jwtFilter, aiFilter);
    }

    @Test
    void passwordEncoder_ReturnsBCryptPasswordEncoder() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        assertNotNull(encoder);
        assertTrue(encoder instanceof BCryptPasswordEncoder);
    }

    @Test
    void userDetailsService_AlwaysThrowsUsernameNotFoundException() {
        UserDetailsService uds = securityConfig.userDetailsService();

        UsernameNotFoundException ex = assertThrows(
                UsernameNotFoundException.class,
                () -> uds.loadUserByUsername("randomUser")
        );

        assertEquals("Broke.AI uses JWT authentication only", ex.getMessage());
    }

    @Test
    void unauthorizedEntryPoint_Sends401Error() throws Exception {
        AuthenticationEntryPoint entryPoint = ReflectionTestUtils.invokeMethod(securityConfig, "unauthorizedEntryPoint");
        assertNotNull(entryPoint);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AuthenticationException authException = mock(AuthenticationException.class);

        entryPoint.commence(request, response, authException);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
}