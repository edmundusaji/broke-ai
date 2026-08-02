package org.edmund.brokeai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.edmund.brokeai.entity.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private static final String SECRET = "thisIsAVerySecureSecretKeyForTestingJwtService123";
    private static final long EXPIRATION = 3600L;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationSeconds", EXPIRATION);
    }

    // Helper to mock JWT
    private String createCustomToken(Map<String, Object> payloadMap) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");

        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(header));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(payloadMap));

        String unsigned = encodedHeader + "." + encodedPayload;
        String signature = ReflectionTestUtils.invokeMethod(jwtService, "sign", unsigned);

        return unsigned + "." + signature;
    }

    @Test
    void generateAndExtract_Success() {
        AppUser user = new AppUser();
        user.setId(99L);
        user.setUsername("edmundus");

        String token = jwtService.generateToken(user);
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);

        Long userId = jwtService.extractUserId(token);
        assertEquals(99L, userId);
    }

    @Test
    void generateToken_GuestUser_ContainsGuestRoleClaim() throws Exception {
        AppUser guest = new AppUser();
        guest.setId(100L);
        guest.setUsername("guest_123");
        guest.setIsGuest(true);

        String token = jwtService.generateToken(guest);
        String encodedPayload = token.split("\\.")[1];
        Map<?, ?> claims = new ObjectMapper().readValue(
            Base64.getUrlDecoder().decode(encodedPayload),
            Map.class
        );

        assertEquals("ROLE_GUEST", claims.get("role"));
        assertEquals(100, claims.get("userId"));
    }

    @Test
    void extractUserId_InvalidFormat_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> jwtService.extractUserId("header.payload"));
        assertEquals("Invalid JWT format", ex.getMessage());
    }

    @Test
    void extractUserId_InvalidSignatureLength_ThrowsException() {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("test");
        String token = jwtService.generateToken(user);

        String manipulatedToken = token + "A";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> jwtService.extractUserId(manipulatedToken));
        assertEquals("Invalid JWT signature", ex.getMessage());
    }

    @Test
    void extractUserId_InvalidSignatureContent_ThrowsException() {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("test");
        String token = jwtService.generateToken(user);

        String[] parts = token.split("\\.");
        String originalSig = parts[2];
        char lastChar = originalSig.charAt(originalSig.length() - 1);
        char newChar = lastChar == 'A' ? 'B' : 'A';
        String newSig = originalSig.substring(0, originalSig.length() - 1) + newChar;

        String manipulatedToken = parts[0] + "." + parts[1] + "." + newSig;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> jwtService.extractUserId(manipulatedToken));
        assertEquals("Invalid JWT signature", ex.getMessage());
    }

    @Test
    void extractUserId_InvalidBase64Payload_ThrowsException() {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("test");
        String token = jwtService.generateToken(user);

        String[] parts = token.split("\\.");
        String unsigned = parts[0] + ".{" + parts[1];
        String sig = ReflectionTestUtils.invokeMethod(jwtService, "sign", unsigned);

        String manipulatedToken = unsigned + "." + sig;

        assertThrows(IllegalArgumentException.class, () -> jwtService.extractUserId(manipulatedToken));
    }

    @Test
    void extractUserId_InvalidJsonPayload_ThrowsException() {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("test");
        String token = jwtService.generateToken(user);

        String[] parts = token.split("\\.");
        String invalidJsonPayload = Base64.getUrlEncoder().withoutPadding().encodeToString("\"bukan-json-map\"".getBytes());
        String unsigned = parts[0] + "." + invalidJsonPayload;
        String sig = ReflectionTestUtils.invokeMethod(jwtService, "sign", unsigned);

        String manipulatedToken = unsigned + "." + sig;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> jwtService.extractUserId(manipulatedToken));
        assertTrue(ex.getMessage().contains("Invalid JWT payload"));
    }

    @Test
    void extractUserId_ExpNotNumber_ThrowsException() throws Exception {
        Map<String, Object> payload = Map.of("userId", 1L, "exp", "waktu-salah");
        String token = createCustomToken(payload);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> jwtService.extractUserId(token));
        assertEquals("JWT expired", ex.getMessage());
    }

    @Test
    void extractUserId_Expired_ThrowsException() throws Exception {
        long pastTime = (System.currentTimeMillis() / 1000) - 3600; // 1 hour ago
        Map<String, Object> payload = Map.of("userId", 1L, "exp", pastTime);
        String token = createCustomToken(payload);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> jwtService.extractUserId(token));
        assertEquals("JWT expired", ex.getMessage());
    }

    @Test
    void extractUserId_UserIdNotNumber_ThrowsException() throws Exception {
        long validExp = (System.currentTimeMillis() / 1000) + 3600;
        Map<String, Object> payload = Map.of("userId", "bukan-angka", "exp", validExp);
        String token = createCustomToken(payload);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> jwtService.extractUserId(token));
        assertEquals("Token does not contain a valid userId", ex.getMessage());
    }

    @Test
    void sign_InternalError_ThrowsException() {
        ReflectionTestUtils.setField(jwtService, "jwtSecret", null);

        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("test");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> jwtService.generateToken(user));
        assertEquals("Failed to sign JWT", ex.getMessage());
    }

    @Test
    void base64UrlJson_SerializationError_ThrowsException() {
        java.util.Map<String, Object> maliciousMap = new java.util.HashMap<>();
        maliciousMap.put("self", maliciousMap);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(jwtService, "base64UrlJson", maliciousMap));

        assertEquals("Failed to serialize JWT part", ex.getMessage());
    }
}
