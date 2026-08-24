package org.edmund.brokeai.dto;

import org.edmund.brokeai.entity.Transaction;

import java.util.UUID;

public record NotificationIngestionResponse(
    Status status,
    Transaction transaction,
    UUID captureId
) {
    public enum Status {
        SAVED,
        IGNORED,
        DUPLICATE,
        NEEDS_REVIEW
    }
}

