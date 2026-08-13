package org.edmund.brokeai.security;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AiRateLimitingFilter aiRateLimitingFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedEntryPoint()))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/guest-login"
                ).permitAll()
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/openapi/**",
                    "/error"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/me/data-exports/*/download").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/v1/me/avatar/uploads/*").permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/expense/receipt",
                    "/api/v1/expense/notification"
                ).hasAnyRole("GUEST", "USER")
                .requestMatchers("/api/v1/expense/**").hasAnyRole("GUEST", "USER")
                .requestMatchers(
                    "/api/v1/me/**",
                    "/api/v1/usernames/**",
                    "/api/v1/support/**"
                ).hasRole("USER")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(aiRateLimitingFilter, JwtAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2idMigratingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Broke.AI uses JWT authentication only");
        };
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\":{\"code\":\"UNAUTHENTICATED\",\"message\":\"Authentication is required.\"," +
                    "\"field\":null,\"requestId\":\"" + UUID.randomUUID() + "\"}}"
            );
        };
    }
}
