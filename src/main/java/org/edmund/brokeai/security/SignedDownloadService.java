package org.edmund.brokeai.security;

import org.edmund.brokeai.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class SignedDownloadService {
    private static final String ALGORITHM = "HmacSHA256";
    private final byte[] secret;

    public SignedDownloadService(@Value("${app.jwt.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String create(UUID jobId, Long userId, Instant expiresAt) {
        return create("data-export", jobId, userId, expiresAt);
    }

    public String create(String purpose, UUID resourceId, Long userId, Instant expiresAt) {
        String payload = purpose + ":" + resourceId + ":" + userId + ":" + expiresAt.getEpochSecond();
        return expiresAt.getEpochSecond() + "." + sign(payload);
    }

    public void validate(String token, UUID jobId, Long userId) {
        validate("data-export", token, jobId, userId);
    }

    public void validate(String purpose, String token, UUID resourceId, Long userId) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", 2);
            long expiresAt = parts.length == 2 ? Long.parseLong(parts[0]) : 0;
            String expected = sign(purpose + ":" + resourceId + ":" + userId + ":" + expiresAt);
            if (expiresAt < Instant.now().getEpochSecond() || parts.length != 2 || !constantTimeEquals(expected, parts[1])) {
                throw invalidToken();
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidToken();
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign download URL", exception);
        }
    }

    private boolean constantTimeEquals(String first, String second) {
        return java.security.MessageDigest.isEqual(
            first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8)
        );
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "The download URL is invalid or expired.");
    }
}
