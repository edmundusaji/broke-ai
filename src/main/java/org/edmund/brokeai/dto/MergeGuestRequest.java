package org.edmund.brokeai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MergeGuestRequest(
    @NotBlank @Size(max = 30) String username,
    @NotBlank @Size(max = 1024) String password
) {
}
