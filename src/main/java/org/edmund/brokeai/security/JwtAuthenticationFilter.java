package org.edmund.brokeai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.repository.UserSessionRepository;
import org.edmund.brokeai.entity.UserSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");

        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            try {
                String token = authorization.substring(BEARER_PREFIX.length());
                Long userId = jwtService.extractUserId(token);
                AppUser user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User for token was not found"));
                if (user.getDeletedAt() != null
                    || (!"active".equals(user.getStatus()) && !"pending_deletion".equals(user.getStatus()))) {
                    throw new IllegalArgumentException("User account is not active");
                }

                UserSession session = resolveSession(token, user, request);
                if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(Instant.now())) {
                    throw new IllegalArgumentException("Session has expired or was revoked");
                }
                if (session.getLastActiveAt() == null
                    || session.getLastActiveAt().isBefore(Instant.now().minusSeconds(60))) {
                    session.setLastActiveAt(Instant.now());
                    session.setUpdatedAt(Instant.now());
                    userSessionRepository.save(session);
                }
                request.setAttribute("broke.sessionId", session.getId());

                String role = Boolean.TRUE.equals(user.getIsGuest()) ? "ROLE_GUEST" : "ROLE_USER";
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                    );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private UserSession resolveSession(String token, AppUser user, HttpServletRequest request) {
        String tokenHash = sha256(token);
        return userSessionRepository.findByRefreshTokenHash(tokenHash).orElseGet(() -> {
            UserSession session = new UserSession();
            session.setUser(user);
            session.setRefreshTokenHash(tokenHash);
            session.setIpHash(sha256(request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr()));
            session.setUserAgent(truncate(request.getHeader("User-Agent"), 255));
            session.setLastActiveAt(Instant.now());
            session.setExpiresAt(jwtService.extractExpiration(token));
            return userSessionRepository.save(session);
        });
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String truncate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) return value;
        return value.substring(0, maximumLength);
    }
}
