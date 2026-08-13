package org.edmund.brokeai.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.edmund.brokeai.entity.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Getter
    @Value("${app.jwt.expiration-seconds}")
    private long expirationSeconds;

    public String generateToken(AppUser user) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getUsername());
        payload.put("userId", user.getId());
        payload.put("username", user.getUsername());
        payload.put("role", Boolean.TRUE.equals(user.getIsGuest()) ? "ROLE_GUEST" : "ROLE_USER");
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iat", now);
        payload.put("exp", now + expirationSeconds);

        String unsignedToken = base64UrlJson(header) + "." + base64UrlJson(payload);
        return unsignedToken + "." + sign(unsignedToken);
    }

    public Long extractUserId(String token) {
        Map<String, Object> claims = parseAndValidate(token);
        Object userId = claims.get("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("Token does not contain a valid userId");
    }

    public Instant extractExpiration(String token) {
        Object expiration = parseAndValidate(token).get("exp");
        if (expiration instanceof Number number) return Instant.ofEpochSecond(number.longValue());
        throw new IllegalArgumentException("Token does not contain a valid expiration");
    }

    private Map<String, Object> parseAndValidate(String token) {
        String[] chunks = token.split("\\.");
        if (chunks.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        String unsignedToken = chunks[0] + "." + chunks[1];
        String expectedSignature = sign(unsignedToken);
        if (!constantTimeEquals(expectedSignature, chunks[2])) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(chunks[1]);
            Map<String, Object> claims = OBJECT_MAPPER.readValue(payloadBytes, new TypeReference<>() {});
            Object exp = claims.get("exp");
            if (!(exp instanceof Number number) || number.longValue() < Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("JWT expired");
            }
            return claims;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT payload", e);
        }
    }

    private String base64UrlJson(Map<String, Object> value) {
        try {
            byte[] json = OBJECT_MAPPER.writeValueAsBytes(value);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JWT part", e);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    private boolean constantTimeEquals(String first, String second) {
        byte[] firstBytes = first.getBytes(StandardCharsets.UTF_8);
        byte[] secondBytes = second.getBytes(StandardCharsets.UTF_8);
        if (firstBytes.length != secondBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < firstBytes.length; i++) {
            result |= firstBytes[i] ^ secondBytes[i];
        }
        return result == 0;
    }
}
