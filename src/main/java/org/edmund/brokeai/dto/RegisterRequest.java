package org.edmund.brokeai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Full name is required.")
    @Size(max = 100, message = "Full name must not exceed 100 characters.")
    String fullName,

    @NotBlank(message = "Username is required.")
    @Size(min = 3, max = 30, message = "Username must contain between 3 and 30 characters.")
    String username,

    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be a valid email address.")
    String email,

    @NotBlank(message = "Password is required.")
    @Size(max = 128, message = "Password must not exceed 128 characters.")
    String password
) {
}
