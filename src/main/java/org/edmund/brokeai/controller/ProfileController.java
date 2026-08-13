package org.edmund.brokeai.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.ApiEnvelope;
import org.edmund.brokeai.dto.ProfileSettingsApi;
import org.edmund.brokeai.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService profileService;

    @GetMapping("/me")
    public ResponseEntity<ApiEnvelope<ProfileSettingsApi.Profile>> getProfile() {
        ProfileSettingsApi.Profile profile = profileService.getProfile();
        return withRevision(ApiEnvelope.of(profile), profile.revision());
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiEnvelope<ProfileSettingsApi.Profile>> updateProfile(
        @Valid @RequestBody ProfileSettingsApi.ProfileUpdateRequest request,
        @RequestHeader("If-Match") String ifMatch
    ) {
        ProfileSettingsApi.Profile profile = profileService.updateProfile(request, ifMatch);
        return withRevision(ApiEnvelope.of(profile), profile.revision());
    }

    @GetMapping("/usernames/{username}/availability")
    public ApiEnvelope<ProfileSettingsApi.UsernameAvailability> usernameAvailability(@PathVariable String username) {
        return ApiEnvelope.of(profileService.checkUsername(username));
    }

    @PostMapping("/me/avatar/upload-url")
    public ApiEnvelope<ProfileSettingsApi.AvatarUpload> createAvatarUpload(
        @Valid @RequestBody ProfileSettingsApi.AvatarUploadRequest request
    ) {
        return ApiEnvelope.of(profileService.createAvatarUpload(request));
    }

    @PutMapping("/me/avatar/uploads/{uploadId}")
    public ApiEnvelope<ApiEnvelope.ActionResult> uploadAvatar(
        @PathVariable UUID uploadId,
        @RequestParam String token,
        @RequestHeader("Content-Type") String contentType,
        @RequestBody byte[] content
    ) {
        profileService.completeAvatarUpload(uploadId, token, contentType, content);
        return ApiEnvelope.success();
    }

    @GetMapping("/me/avatar/content")
    public ResponseEntity<byte[]> getAvatarContent() {
        ProfileSettingsApi.AvatarContent avatar = profileService.getAvatarContent();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(avatar.contentType())).body(avatar.content());
    }

    @DeleteMapping("/me/avatar")
    public ApiEnvelope<ProfileSettingsApi.Profile> deleteAvatar() {
        return ApiEnvelope.of(profileService.deleteAvatar());
    }

    @PostMapping("/me/email-change")
    public ResponseEntity<ApiEnvelope<ProfileSettingsApi.EmailChange>> requestEmailChange(
        @Valid @RequestBody ProfileSettingsApi.EmailChangeRequest request
    ) {
        return ResponseEntity.accepted().body(ApiEnvelope.of(profileService.requestEmailChange(request)));
    }

    @PostMapping("/me/email-change/verify")
    public ApiEnvelope<ProfileSettingsApi.Profile> verifyEmailChange(
        @Valid @RequestBody ProfileSettingsApi.EmailVerificationRequest request
    ) {
        return ApiEnvelope.of(profileService.verifyEmailChange(request));
    }

    private <T> ResponseEntity<T> withRevision(T body, long revision) {
        return ResponseEntity.ok().header("ETag", "\"" + revision + "\"").body(body);
    }
}
