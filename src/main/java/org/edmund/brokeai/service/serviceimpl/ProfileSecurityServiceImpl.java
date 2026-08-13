package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.PageEnvelope;
import org.edmund.brokeai.dto.ProfileSettingsApi;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.UserSession;
import org.edmund.brokeai.exception.ApiException;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.repository.UserSessionRepository;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.service.PasswordPolicyService;
import org.edmund.brokeai.service.ProfileSecurityService;
import org.edmund.brokeai.service.SecurityAuditService;
import org.edmund.brokeai.service.AccountNotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileSecurityServiceImpl implements ProfileSecurityService {
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final SecurityAuditService auditService;
    private final AccountNotificationService notificationService;

    @Override
    @Transactional
    public ProfileSettingsApi.PasswordChange changePassword(
        ProfileSettingsApi.PasswordChangeRequest request,
        UUID currentSessionId
    ) {
        AppUser user = currentUserService.getCurrentUser();
        if (user.getPassword() == null || !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CURRENT_PASSWORD_INVALID",
                "The current password is invalid.", "currentPassword");
        }
        passwordPolicyService.validate(request.newPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw ServiceSupport.validation("newPassword", "The new password must be different from the current password.");
        }

        Instant now = Instant.now();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(now);
        userRepository.save(user);
        int revoked = Boolean.TRUE.equals(request.retainCurrentSession())
            ? userSessionRepository.revokeOthers(user.getId(), currentSessionId, now)
            : userSessionRepository.revokeAll(user.getId(), now);
        auditService.record(user, "PASSWORD_CHANGED", currentSessionId, Map.of("revokedSessionCount", revoked));
        notificationService.queueEmail(user, user.getEmail(), "PASSWORD_CHANGED", "Password changed");
        return new ProfileSettingsApi.PasswordChange(now, revoked);
    }

    @Override
    @Transactional(readOnly = true)
    public PageEnvelope<ProfileSettingsApi.Session> getSessions(int page, int size, UUID currentSessionId) {
        AppUser user = currentUserService.getCurrentUser();
        var sessions = userSessionRepository.findByUserIdOrderByCreatedAtDesc(
            user.getId(), PageRequest.of(page, Math.min(size, 100))
        );
        var data = sessions.stream().map(value -> map(value, currentSessionId)).toList();
        return PageEnvelope.of(data, page, Math.min(size, 100), sessions.getTotalElements());
    }

    @Override
    @Transactional
    public void revokeSession(UUID sessionId) {
        AppUser user = currentUserService.getCurrentUser();
        UserSession session = userSessionRepository.findByIdAndUserId(sessionId, user.getId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Session not found."));
        session.setRevokedAt(Instant.now());
        userSessionRepository.save(session);
        auditService.record(user, "SESSION_REVOKED", sessionId, Map.of());
    }

    @Override
    @Transactional
    public void revokeOtherSessions(UUID currentSessionId) {
        AppUser user = currentUserService.getCurrentUser();
        int revoked = userSessionRepository.revokeOthers(user.getId(), currentSessionId, Instant.now());
        auditService.record(user, "OTHER_SESSIONS_REVOKED", currentSessionId, Map.of("count", revoked));
    }

    private ProfileSettingsApi.Session map(UserSession value, UUID currentSessionId) {
        return new ProfileSettingsApi.Session(
            value.getId(), value.getId().equals(currentSessionId),
            value.getDevice() == null ? null : value.getDevice().getId(),
            value.getDevice() == null ? null : value.getDevice().getDeviceName(), value.getUserAgent(),
            value.getLastActiveAt(), value.getExpiresAt(), value.getRevokedAt(), value.getCreatedAt()
        );
    }
}
