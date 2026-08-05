package org.edmund.brokeai.dto;

public record UpgradeGuestRequest(
    String fullName,
    String email,
    String password
) {
}
