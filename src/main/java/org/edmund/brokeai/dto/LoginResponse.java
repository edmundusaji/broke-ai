package org.edmund.brokeai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record LoginResponse(
    UUID accountId,
    String token,
    long expiresIn,
    String username,
    boolean isGuest,
    @JsonProperty("refresh_available") boolean refreshAvailable,
    @JsonProperty("remaining_ai_trials") int remainingAiTrials,
    UserInfo user
) {
    public LoginResponse(
        String token,
        long expiresIn,
        String username,
        boolean isGuest,
        int remainingAiTrials,
        UserInfo user
    ) {
        this(null, token, expiresIn, username, isGuest, false, remainingAiTrials, user);
    }

    public record UserInfo(String fullName, String email) {
    }
}
