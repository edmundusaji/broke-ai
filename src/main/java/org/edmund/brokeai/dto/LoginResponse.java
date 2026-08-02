package org.edmund.brokeai.dto;

public record LoginResponse(
    String token,
    long expiresIn,
    String username,
    boolean isGuest,
    UserInfo user
) {
    public record UserInfo(String name, String email) {
    }
}
