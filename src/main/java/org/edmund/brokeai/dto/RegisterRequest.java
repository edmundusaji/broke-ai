package org.edmund.brokeai.dto;

public record RegisterRequest(
    String namaLengkap,
    String username,
    String email,
    String password
) {
}
