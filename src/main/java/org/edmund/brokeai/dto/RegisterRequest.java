package org.edmund.brokeai.dto;

public record RegisterRequest(
    String fullName,
    String username,
    String email,
    String password
) {
}
