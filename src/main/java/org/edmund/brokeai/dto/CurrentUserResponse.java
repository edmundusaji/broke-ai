package org.edmund.brokeai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record CurrentUserResponse(
    UUID accountId,
    String username,
    String fullName,
    String email,
    String role,
    @JsonProperty("remaining_ai_trials") int remainingAiTrials
) {
    public CurrentUserResponse(
        String username,
        String fullName,
        String email,
        String role,
        int remainingAiTrials
    ) {
        this(null, username, fullName, email, role, remainingAiTrials);
    }
}
