package org.edmund.brokeai.dto;

import java.util.UUID;

public record MergeGuestResponse(
    UUID accountId,
    String token,
    long expiresIn,
    String username,
    boolean isGuest,
    MergeResult mergeResult
) {
    public MergeGuestResponse(
        String token,
        long expiresIn,
        String username,
        boolean isGuest,
        MergeResult mergeResult
    ) {
        this(null, token, expiresIn, username, isGuest, mergeResult);
    }

    public record MergeResult(long transactionsMoved, long duplicatesSkipped) {
    }
}
