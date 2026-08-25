package org.edmund.brokeai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.edmund.brokeai.entity.Transaction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class SyncApi {
    private SyncApi() {
    }

    public record PushRequest(
        @NotNull UUID deviceId,
        @NotEmpty @Size(max = 100) List<@Valid Mutation> operations
    ) {
    }

    public record Mutation(
        @NotNull UUID operationId,
        @NotNull MutationType type,
        @NotNull UUID clientTransactionId,
        Long baseRevision,
        @Valid TransactionInput transaction
    ) {
    }

    public enum MutationType {
        CREATE,
        UPDATE,
        DELETE
    }

    public record TransactionInput(
        LocalDate date,
        Double amount,
        String category,
        String paymentMethod,
        String description
    ) {
    }

    public record PushResponse(
        List<OperationResult> results,
        long serverRevision
    ) {
    }

    public record OperationResult(
        UUID operationId,
        OperationStatus status,
        String errorCode,
        String message,
        TransactionView transaction
    ) {
    }

    public enum OperationStatus {
        APPLIED,
        DUPLICATE,
        CONFLICT,
        REJECTED,
        RETRYABLE_FAILURE
    }

    public record PullResponse(
        List<PullChange> changes,
        String nextCursor,
        boolean hasMore,
        long serverRevision
    ) {
    }

    public record PullChange(
        long sequence,
        ChangeType type,
        UUID clientTransactionId,
        long revision,
        TransactionView transaction,
        Instant deletedAt
    ) {
    }

    public enum ChangeType {
        TRANSACTION_UPSERT,
        TRANSACTION_DELETE
    }

    public record TransactionView(
        Long id,
        UUID clientTransactionId,
        LocalDateTime date,
        Double amount,
        String category,
        String paymentMethod,
        String description,
        String inputType,
        String validationStatus,
        UUID captureId,
        String captureMode,
        String sourcePackage,
        Instant sourceNotificationPostedAt,
        Long revision,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
    ) {
        public static TransactionView from(Transaction transaction) {
            return new TransactionView(
                transaction.getId(),
                transaction.getClientTransactionId(),
                transaction.getDate(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getPaymentMethod(),
                transaction.getDescription(),
                transaction.getInputType(),
                transaction.getValidationStatus(),
                transaction.getCaptureId(),
                transaction.getCaptureMode(),
                transaction.getSourcePackage(),
                transaction.getSourceNotificationPostedAt(),
                transaction.getRevision(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt(),
                transaction.getDeletedAt()
            );
        }
    }
}
