package org.edmund.brokeai.dto;

public record MergeGuestResponse(
    String token,
    long expiresIn,
    String username,
    boolean isGuest,
    MergeResult mergeResult
) {
    public record MergeResult(long transactionsMoved, long duplicatesSkipped) {
    }
}
