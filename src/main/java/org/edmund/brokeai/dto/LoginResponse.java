package org.edmund.brokeai.dto;

public record LoginResponse(
    String token,
    long expiresIn,
    UserInfo user
) {
    public record UserInfo(String name, String email) {
    }
}
