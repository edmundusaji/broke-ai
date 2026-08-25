package org.edmund.brokeai.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.edmund.brokeai.dto.SyncApi;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.exception.ApiException;
import org.edmund.brokeai.repository.SyncMutationReceiptRepository;
import org.edmund.brokeai.repository.TransactionRepository;
import org.edmund.brokeai.repository.UserChangeLogRepository;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.service.RateLimitingService;
import org.edmund.brokeai.service.serviceimpl.SyncServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncServiceImplTest {
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final UserChangeLogRepository changeLogRepository = mock(UserChangeLogRepository.class);
    private final SyncMutationReceiptRepository mutationReceiptRepository =
        mock(SyncMutationReceiptRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final RateLimitingService rateLimitingService = mock(RateLimitingService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private SyncServiceImpl service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setId(42L);
        user.setUsername("sync_owner");
        user.setFullName("Sync Owner");
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(mutationReceiptRepository.reserve(
            eq(42L), any(UUID.class), any(String.class), any(String.class), any(Instant.class)
        )).thenReturn(new SyncMutationReceiptRepository.Reservation(true, null));
        when(changeLogRepository.currentRevision(42L)).thenReturn(9L);
        when(rateLimitingService.tryConsumeSync(eq(42L), anyInt())).thenReturn(true);

        service = new SyncServiceImpl(
            currentUserService,
            userRepository,
            transactionRepository,
            changeLogRepository,
            mutationReceiptRepository,
            objectMapper,
            rateLimitingService,
            transactionManager
        );
    }

    @Test
    void pushCreate_AppliesAndReturnsStableClientIdentity() {
        UUID clientId = UUID.randomUUID();
        SyncApi.Mutation mutation = mutation(SyncApi.MutationType.CREATE, clientId, null);
        when(transactionRepository.findByClientTransactionIdForUpdate(42L, clientId))
            .thenReturn(Optional.empty());
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            saved.setId(81L);
            saved.setRevision(1L);
            return saved;
        });

        SyncApi.PushResponse response = service.push(push(mutation));

        assertEquals(9L, response.serverRevision());
        assertEquals(SyncApi.OperationStatus.APPLIED, response.results().getFirst().status());
        assertEquals(clientId, response.results().getFirst().transaction().clientTransactionId());
        verify(mutationReceiptRepository).complete(eq(42L), eq(mutation.operationId()), any(String.class));
    }

    @Test
    void pushRepeatedOperation_ReturnsStoredTerminalResponseWithoutApplyingAgain() throws Exception {
        SyncApi.Mutation mutation = mutation(SyncApi.MutationType.CREATE, UUID.randomUUID(), null);
        SyncApi.OperationResult stored = new SyncApi.OperationResult(
            mutation.operationId(),
            SyncApi.OperationStatus.APPLIED,
            null,
            null,
            null
        );
        when(mutationReceiptRepository.reserve(
            eq(42L), eq(mutation.operationId()), any(String.class), any(String.class), any(Instant.class)
        )).thenReturn(new SyncMutationReceiptRepository.Reservation(
            false,
            objectMapper.writeValueAsString(stored)
        ));

        SyncApi.PushResponse response = service.push(push(mutation));

        assertEquals(SyncApi.OperationStatus.APPLIED, response.results().getFirst().status());
        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
        verify(mutationReceiptRepository, never()).complete(anyLong(), any(UUID.class), any(String.class));
    }

    @Test
    void pushStaleUpdate_ReturnsCurrentServerVersionAsConflict() {
        UUID clientId = UUID.randomUUID();
        SyncApi.Mutation mutation = mutation(SyncApi.MutationType.UPDATE, clientId, 1L);
        Transaction current = transaction(clientId, 2L);
        when(transactionRepository.findByClientTransactionIdForUpdate(42L, clientId))
            .thenReturn(Optional.of(current));

        SyncApi.OperationResult result = service.push(push(mutation)).results().getFirst();

        assertEquals(SyncApi.OperationStatus.CONFLICT, result.status());
        assertEquals("TRANSACTION_REVISION_CONFLICT", result.errorCode());
        assertEquals(2L, result.transaction().revision());
        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void pull_PaginatesOrderedOwnerScopedChangesAndReturnsOpaqueCursor() throws Exception {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        String firstSnapshot = objectMapper.writeValueAsString(
            SyncApi.TransactionView.from(transaction(firstId, 1L))
        );
        Transaction deleted = transaction(secondId, 3L);
        deleted.setDeletedAt(Instant.parse("2026-08-25T09:15:00Z"));
        String deletedSnapshot = objectMapper.writeValueAsString(SyncApi.TransactionView.from(deleted));
        when(changeLogRepository.findAfter(42L, 0L, 3)).thenReturn(List.of(
            new UserChangeLogRepository.ChangeRow(10L, firstId, "UPSERT", 1L, firstSnapshot, Instant.now()),
            new UserChangeLogRepository.ChangeRow(20L, secondId, "DELETE", 3L, deletedSnapshot, Instant.now()),
            new UserChangeLogRepository.ChangeRow(30L, UUID.randomUUID(), "UPSERT", 1L, firstSnapshot, Instant.now())
        ));
        when(changeLogRepository.currentRevision(42L)).thenReturn(30L);

        SyncApi.PullResponse response = service.pull(null, 2);

        assertTrue(response.hasMore());
        assertEquals(2, response.changes().size());
        assertEquals(SyncApi.ChangeType.TRANSACTION_UPSERT, response.changes().get(0).type());
        assertEquals(SyncApi.ChangeType.TRANSACTION_DELETE, response.changes().get(1).type());
        assertEquals("v1:20", new String(Base64.getUrlDecoder().decode(response.nextCursor())));
        assertEquals(30L, response.serverRevision());
    }

    @Test
    void pull_InvalidCursorIsRejectedDeterministically() {
        ApiException exception = assertThrows(ApiException.class, () -> service.pull("not-a-cursor", 100));
        assertEquals("INVALID_CURSOR", exception.getCode());
    }

    @Test
    void push_RateLimitIncludesRetryAfterContract() {
        when(rateLimitingService.tryConsumeSync(eq(42L), anyInt())).thenReturn(false);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> service.push(push(mutation(SyncApi.MutationType.CREATE, UUID.randomUUID(), null)))
        );

        assertEquals("SYNC_RATE_LIMITED", exception.getCode());
        assertEquals(60, exception.getRetryAfterSeconds());
    }

    private SyncApi.PushRequest push(SyncApi.Mutation mutation) {
        return new SyncApi.PushRequest(UUID.randomUUID(), List.of(mutation));
    }

    private SyncApi.Mutation mutation(SyncApi.MutationType type, UUID clientId, Long baseRevision) {
        return new SyncApi.Mutation(
            UUID.randomUUID(),
            type,
            clientId,
            baseRevision,
            type == SyncApi.MutationType.DELETE ? null : new SyncApi.TransactionInput(
                LocalDate.of(2026, 8, 25),
                75_000.0,
                "Food",
                "GoPay",
                "Dinner"
            )
        );
    }

    private Transaction transaction(UUID clientId, long revision) {
        Transaction transaction = new Transaction();
        transaction.setId(50L);
        transaction.setClientTransactionId(clientId);
        transaction.setUser(user);
        transaction.setDate(LocalDateTime.of(2026, 8, 25, 19, 0));
        transaction.setAmount(75_000.0);
        transaction.setCategory("Food");
        transaction.setPaymentMethod("GoPay");
        transaction.setDescription("Dinner");
        transaction.setInputType("MANUAL");
        transaction.setValidationStatus("CONFIRMED");
        transaction.setRevision(revision);
        return transaction;
    }
}
