package org.edmund.brokeai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record GuestBootstrapRequest(
    @NotNull UUID clientGuestId,
    @NotBlank @Size(min = 32, max = 512) String installationCredential
) {
}
