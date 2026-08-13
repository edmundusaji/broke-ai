package org.edmund.brokeai.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PageEnvelope<T>(List<T> data, PageMeta meta) {

    public static <T> PageEnvelope<T> of(List<T> data, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageEnvelope<>(
            data,
            new PageMeta(UUID.randomUUID(), Instant.now(), page, size, totalElements, totalPages)
        );
    }

    public record PageMeta(
        UUID requestId,
        Instant serverTime,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }
}
