package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.ProfileSettingsApi;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.AvatarUpload;
import org.edmund.brokeai.entity.EmailChangeRequest;
import org.edmund.brokeai.exception.ApiException;
import org.edmund.brokeai.repository.AvatarUploadRepository;
import org.edmund.brokeai.repository.EmailChangeRequestRepository;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.security.SignedDownloadService;
import org.edmund.brokeai.service.AccountNotificationService;
import org.edmund.brokeai.service.ProfileService;
import org.edmund.brokeai.service.PrivateObjectStorage;
import org.edmund.brokeai.service.SecurityAuditService;
import org.edmund.brokeai.service.UserSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private static final Set<String> AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final AvatarUploadRepository avatarUploadRepository;
    private final EmailChangeRequestRepository emailChangeRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService auditService;
    private final PrivateObjectStorage objectStorage;
    private final AccountNotificationService notificationService;
    private final SignedDownloadService signedResourceService;
    private final UserSyncService userSyncService;

    @Override
    public ProfileSettingsApi.Profile getProfile() {
        return map(currentUserService.getCurrentUser());
    }

    @Override
    @Transactional
    public ProfileSettingsApi.Profile updateProfile(ProfileSettingsApi.ProfileUpdateRequest request, String ifMatch) {
        AppUser user = currentUserService.getCurrentUser();
        ServiceSupport.requireRevision(user.getProfileRevision(), ifMatch);
        String previousUsername = user.getUsername();
        if (request == null || (request.fullName() == null && request.username() == null && request.phone() == null)) {
            throw ServiceSupport.validation(null, "At least one profile field is required.");
        }

        if (request.fullName() != null) {
            String fullName = request.fullName().trim();
            if (fullName.isEmpty()) {
                throw ServiceSupport.validation("fullName", "Full name is required.");
            }
            user.setFullName(fullName);
        }
        if (request.username() != null) {
            String username = request.username().trim();
            if (userRepository.existsByUsernameIgnoreCaseAndIdNot(username, user.getId())) {
                throw new ApiException(
                    HttpStatus.CONFLICT,
                    "USERNAME_ALREADY_USED",
                    "That username is unavailable.",
                    "username"
                );
            }
            user.setUsername(username);
        }
        if (request.phone() != null) user.setPhone(request.phone());
        user.setUpdatedAt(Instant.now());
        AppUser saved = userRepository.saveAndFlush(user);
        auditService.record(saved, "PROFILE_CHANGED", null, Map.of());
        if (!previousUsername.equals(saved.getUsername())) {
            auditService.record(saved, "USERNAME_CHANGED", null, Map.of(
                "previousUsername", previousUsername,
                "newUsername", saved.getUsername()
            ));
        }
        userSyncService.markChanged(saved.getId());
        return map(saved);
    }

    @Override
    public ProfileSettingsApi.UsernameAvailability checkUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.length() < 3 || normalized.length() > 30) {
            throw ServiceSupport.validation("username", "Username must contain between 3 and 30 characters.");
        }
        AppUser user = currentUserService.getCurrentUser();
        boolean used = userRepository.existsByUsernameIgnoreCaseAndIdNot(normalized, user.getId());
        return new ProfileSettingsApi.UsernameAvailability(normalized, !used);
    }

    @Override
    @Transactional
    public ProfileSettingsApi.AvatarUpload createAvatarUpload(ProfileSettingsApi.AvatarUploadRequest request) {
        if (!AVATAR_TYPES.contains(request.contentType())) {
            throw ServiceSupport.validation("contentType", "Only JPEG, PNG, and WebP avatars are supported.");
        }

        AppUser user = currentUserService.getCurrentUser();
        AvatarUpload upload = new AvatarUpload();
        upload.setUser(user);
        upload.setContentType(request.contentType());
        upload.setMaximumSizeBytes(request.sizeBytes());
        upload.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        upload.setObjectKey("avatars/" + user.getId() + "/" + UUID.randomUUID());
        AvatarUpload saved = avatarUploadRepository.save(upload);

        return new ProfileSettingsApi.AvatarUpload(
            saved.getId(),
            "/api/v1/me/avatar/uploads/" + saved.getId() + "?token=" +
                signedResourceService.create("avatar-upload", saved.getId(), user.getId(), saved.getExpiresAt()),
            saved.getExpiresAt(),
            Map.of("Content-Type", saved.getContentType())
        );
    }

    @Override
    @Transactional
    public ProfileSettingsApi.Profile completeAvatarUpload(
        UUID uploadId, String token, String contentType, byte[] content
    ) {
        AvatarUpload upload = avatarUploadRepository.findById(uploadId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Avatar upload not found."));
        AppUser user = upload.getUser();
        signedResourceService.validate("avatar-upload", token, uploadId, user.getId());
        if (upload.getCompletedAt() != null || upload.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICTING_UPDATE",
                "The avatar upload URL has expired or was already used.");
        }
        if (!upload.getContentType().equalsIgnoreCase(contentType)) {
            throw ServiceSupport.validation("Content-Type", "The uploaded content type does not match the request.");
        }
        if (content == null || content.length == 0 || content.length > upload.getMaximumSizeBytes()) {
            throw ServiceSupport.validation("file", "Avatar files must not be empty or exceed the requested size.");
        }
        if (!hasExpectedImageSignature(upload.getContentType(), content)) {
            throw ServiceSupport.validation("file", "The file content does not match its declared image type.");
        }

        objectStorage.put(upload.getObjectKey(), content);
        String previousObject = user.getAvatarObjectKey();
        user.setAvatarObjectKey(upload.getObjectKey());
        user.setUpdatedAt(Instant.now());
        upload.setCompletedAt(Instant.now());
        avatarUploadRepository.save(upload);
        AppUser saved = userRepository.saveAndFlush(user);
        if (previousObject != null && !previousObject.equals(upload.getObjectKey())) objectStorage.delete(previousObject);
        auditService.record(saved, "AVATAR_CHANGED", null, Map.of());
        userSyncService.markChanged(saved.getId());
        return map(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileSettingsApi.AvatarContent getAvatarContent() {
        AppUser user = currentUserService.getCurrentUser();
        if (user.getAvatarObjectKey() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Avatar not found.");
        }
        AvatarUpload upload = avatarUploadRepository.findByObjectKey(user.getAvatarObjectKey())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Avatar not found."));
        return new ProfileSettingsApi.AvatarContent(upload.getContentType(), objectStorage.get(user.getAvatarObjectKey()));
    }

    @Override
    @Transactional
    public ProfileSettingsApi.Profile deleteAvatar() {
        AppUser user = currentUserService.getCurrentUser();
        String previousObject = user.getAvatarObjectKey();
        user.setAvatarObjectKey(null);
        user.setUpdatedAt(Instant.now());
        AppUser saved = userRepository.saveAndFlush(user);
        if (previousObject != null) objectStorage.delete(previousObject);
        auditService.record(saved, "AVATAR_REMOVED", null, Map.of());
        userSyncService.markChanged(saved.getId());
        return map(saved);
    }

    @Override
    @Transactional
    public ProfileSettingsApi.EmailChange requestEmailChange(ProfileSettingsApi.EmailChangeRequest request) {
        AppUser user = currentUserService.getCurrentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "CURRENT_PASSWORD_INVALID",
                "The current password is invalid.",
                "currentPassword"
            );
        }

        String newEmail = request.newEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(newEmail, user.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED", "That email is already in use.", "newEmail");
        }

        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        emailChangeRequestRepository
            .findFirstByUserIdAndVerifiedAtIsNullAndCancelledAtIsNullOrderByRequestedAtDesc(user.getId())
            .ifPresent(previous -> {
                previous.setCancelledAt(Instant.now());
                emailChangeRequestRepository.saveAndFlush(previous);
            });
        EmailChangeRequest change = new EmailChangeRequest();
        change.setUser(user);
        change.setNewEmail(newEmail);
        change.setVerificationTokenHash(ServiceSupport.sha256(token));
        change.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        emailChangeRequestRepository.save(change);

        notificationService.queueEmail(user, newEmail, "EMAIL_CHANGE_VERIFICATION", token);

        user.setPendingEmail(newEmail);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        auditService.record(user, "EMAIL_CHANGE_REQUESTED", null, Map.of("pendingEmail", newEmail));
        userSyncService.markChanged(user.getId());
        return new ProfileSettingsApi.EmailChange(newEmail, change.getExpiresAt());
    }

    @Override
    @Transactional
    public ProfileSettingsApi.Profile verifyEmailChange(ProfileSettingsApi.EmailVerificationRequest request) {
        AppUser authenticated = currentUserService.getCurrentUser();
        EmailChangeRequest change = emailChangeRequestRepository
            .findByVerificationTokenHashAndVerifiedAtIsNullAndCancelledAtIsNull(
                ServiceSupport.sha256(request.verificationToken())
            )
            .filter(candidate -> candidate.getUser().getId().equals(authenticated.getId()))
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The email verification request was not found."
            ));
        if (change.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_VERIFICATION_REQUIRED", "The verification token has expired.");
        }
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(change.getNewEmail(), authenticated.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED", "That email is already in use.", "newEmail");
        }

        Instant now = Instant.now();
        authenticated.setEmail(change.getNewEmail());
        authenticated.setPendingEmail(null);
        authenticated.setEmailVerifiedAt(now);
        authenticated.setUpdatedAt(now);
        change.setVerifiedAt(now);
        emailChangeRequestRepository.save(change);
        AppUser saved = userRepository.saveAndFlush(authenticated);
        notificationService.queueEmail(saved, saved.getEmail(), "EMAIL_CHANGE_COMPLETED", "Email change completed");
        auditService.record(saved, "EMAIL_CHANGE_COMPLETED", null, Map.of());
        userSyncService.markChanged(saved.getId());
        return map(saved);
    }

    private ProfileSettingsApi.Profile map(AppUser user) {
        String avatarUrl = user.getAvatarObjectKey() == null
            ? null
            : "/api/v1/me/avatar/content";
        return new ProfileSettingsApi.Profile(
            user.getId(),
            user.getFullName(),
            user.getUsername(),
            user.getEmail(),
            user.getPendingEmail(),
            user.getEmailVerifiedAt(),
            user.getPhone(),
            avatarUrl,
            user.getStatus(),
            user.getProfileRevision(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    private boolean hasExpectedImageSignature(String contentType, byte[] content) {
        return switch (contentType) {
            case "image/jpeg" -> content.length >= 3
                && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff;
            case "image/png" -> content.length >= 8
                && (content[0] & 0xff) == 0x89 && content[1] == 0x50 && content[2] == 0x4e
                && content[3] == 0x47 && content[4] == 0x0d && content[5] == 0x0a
                && content[6] == 0x1a && content[7] == 0x0a;
            case "image/webp" -> content.length >= 12
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
            default -> false;
        };
    }
}
