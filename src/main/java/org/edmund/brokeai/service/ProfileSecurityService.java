package org.edmund.brokeai.service;

import org.edmund.brokeai.dto.PageEnvelope;
import org.edmund.brokeai.dto.ProfileSettingsApi;

import java.util.UUID;

public interface ProfileSecurityService {
    ProfileSettingsApi.PasswordChange changePassword(
        ProfileSettingsApi.PasswordChangeRequest request,
        UUID currentSessionId
    );

    PageEnvelope<ProfileSettingsApi.Session> getSessions(int page, int size, UUID currentSessionId);

    void revokeSession(UUID sessionId);

    void revokeOtherSessions(UUID currentSessionId);
}
