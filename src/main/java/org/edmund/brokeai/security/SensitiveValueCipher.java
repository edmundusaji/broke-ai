package org.edmund.brokeai.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SensitiveValueCipher {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public SensitiveValueCipher(@Value("${app.device-token.encryption-key:${app.jwt.secret}}") String secret) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize sensitive-value encryption", exception);
        }
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt sensitive value", exception);
        }
    }
}
