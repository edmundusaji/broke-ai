package org.edmund.brokeai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CurrentUserResponse(
    String username,
    String fullName,
    String email,
    String role,
    @JsonProperty("remaining_ai_trials") int remainingAiTrials
) {
}
