package org.edmund.brokeai.service;

import org.edmund.brokeai.dto.ProfileSettingsApi;

import java.util.UUID;

public interface ProfileService {
    ProfileSettingsApi.Profile getProfile();

    ProfileSettingsApi.Profile updateProfile(ProfileSettingsApi.ProfileUpdateRequest request, String ifMatch);

    ProfileSettingsApi.UsernameAvailability checkUsername(String username);

    ProfileSettingsApi.AvatarUpload createAvatarUpload(ProfileSettingsApi.AvatarUploadRequest request);

    ProfileSettingsApi.Profile completeAvatarUpload(UUID uploadId, String token, String contentType, byte[] content);

    ProfileSettingsApi.AvatarContent getAvatarContent();

    ProfileSettingsApi.Profile deleteAvatar();

    ProfileSettingsApi.EmailChange requestEmailChange(ProfileSettingsApi.EmailChangeRequest request);

    ProfileSettingsApi.Profile verifyEmailChange(ProfileSettingsApi.EmailVerificationRequest request);
}
