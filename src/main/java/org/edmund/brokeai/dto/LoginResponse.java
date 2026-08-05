package org.edmund.brokeai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResponse(
    String token,
    long expiresIn,
    String username,
    boolean isGuest,
    @JsonProperty("remaining_ai_trials") int remainingAiTrials,
    UserInfo user
) {
    public record UserInfo(String fullName, String email) {
    }
}
