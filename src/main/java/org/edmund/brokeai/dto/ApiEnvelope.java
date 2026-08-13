package org.edmund.brokeai.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiEnvelope<T>(T data, Meta meta) {

    public static <T> ApiEnvelope<T> of(T data) {
        return new ApiEnvelope<>(data, new Meta(UUID.randomUUID(), Instant.now()));
    }

    public static ApiEnvelope<ActionResult> success() {
        return of(new ActionResult(true));
    }

    public record Meta(UUID requestId, Instant serverTime) {
    }

    public record ActionResult(boolean success) {
    }
}
