package org.edmund.brokeai.serviceImpl;

import org.edmund.brokeai.dto.AiExpenseResponse;
import org.edmund.brokeai.dto.ExpenseRequest;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.repository.TransactionRepository;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.exception.GuestAiTrialLimitException;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.service.GeminiService;
import org.edmund.brokeai.service.UserSyncService;
import org.edmund.brokeai.service.serviceimpl.ExpenseServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceImplTests {

    @InjectMocks
    private ExpenseServiceImpl expenseServiceImpl;

    @Mock
    private GeminiService geminiService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSyncService userSyncService;

    private MultipartFile mockFile;
    private AiExpenseResponse mockAiResponse;
    private Transaction mockTransaction;
    private AppUser mockUser;

    @BeforeEach
    void setUp() {
        mockFile = new MockMultipartFile("file", "receipt.jpg",
                "image/jpeg", "dummy image content".getBytes());

        mockAiResponse = new AiExpenseResponse();
        mockAiResponse.setPaymentMethod("GoPay");
        mockAiResponse.setDescription("Coffee Purchase");
        mockAiResponse.setAmount(55000.0);
        mockAiResponse.setCategory("Food");
        mockAiResponse.setDate("2026-03-28");
        mockAiResponse.setTime("15:30:00");

        mockTransaction = new Transaction();
        mockTransaction.setId(1L);
        mockTransaction.setPaymentMethod("GoPay");
        mockTransaction.setDescription("Coffee Purchase");
        mockTransaction.setAmount(55000.0);

        mockUser = new AppUser();
        mockUser.setId(10L);
        mockUser.setFullName("Rani Test");
        mockUser.setUsername("rani");
        mockUser.setEmail("rani@example.com");

        lenient().when(currentUserService.getCurrentUser()).thenReturn(mockUser);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(transactionRepository);
    }

    @Test
    void saveReceipt_Success_Test() {
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(mockTransaction);

        Transaction result = expenseServiceImpl.saveReceipt(mockFile);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("GoPay", result.getPaymentMethod());
        assertEquals("Coffee Purchase", result.getDescription());

        verify(geminiService, times(1)).receiptProcess(mockFile);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void saveReceipt_FailedDueToNullTotal_Test() {
        mockAiResponse.setAmount(null);
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            expenseServiceImpl.saveReceipt(mockFile);
        });

        assertEquals("Failed to process receipt", exception.getMessage());

        verify(geminiService, times(1)).receiptProcess(mockFile);
        verify(transactionRepository, times(0)).save(any());
    }

    @Test
    void saveNotification_Success_WithNullTimeFallback_Test() {
        mockAiResponse.setDate("2026-03-28");
        mockAiResponse.setTime(null);

        String notifText = "Bayar 55000 di Kopi Kenangan";
        when(geminiService.processNotification(notifText)).thenReturn(mockAiResponse);

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        Transaction result = expenseServiceImpl.saveNotification(notifText);

        assertNotNull(result);
        assertNotNull(result.getDate());
        assertEquals("NOTIFICATION", result.getInputType());
        assertEquals("GoPay", result.getPaymentMethod());
        assertEquals("Coffee Purchase", result.getDescription());

        verify(geminiService, times(1)).processNotification(notifText);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void saveReceipt_FailedDueToNullAiResponse_Test() {
        when(geminiService.receiptProcess(mockFile)).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            expenseServiceImpl.saveReceipt(mockFile);
        });

        assertEquals("Failed to process receipt", exception.getMessage());
    }

    @Test
    void saveNotification_FailedDueToNullAiResponse_Test() {
        when(geminiService.processNotification(anyString())).thenReturn(null);

        assertThrows(RuntimeException.class, () -> {
            expenseServiceImpl.saveNotification("Notif BCA");
        });
    }

    @Test
    void saveNotification_FailedDueToNullTotal_Test() {
        mockAiResponse.setAmount(null);
        when(geminiService.processNotification(anyString())).thenReturn(mockAiResponse);

        assertThrows(RuntimeException.class, () -> {
            expenseServiceImpl.saveNotification("Notif OVO");
        });
    }

    @Test
    void parseDateAndTime_FallbackForInvalidDate_Test() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        mockAiResponse.setDate(null);
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        Transaction result1 = expenseServiceImpl.saveReceipt(mockFile);
        assertNotNull(result1.getDate());

        mockAiResponse.setDate("March 28, 2026");
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        Transaction result2 = expenseServiceImpl.saveReceipt(mockFile);
        assertNotNull(result2.getDate());

        mockAiResponse.setDate("   ");
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        Transaction result3 = expenseServiceImpl.saveReceipt(mockFile);
        assertNotNull(result3.getDate());
    }

    @Test
    void parseDateAndTime_FallbackForInvalidTime_Test() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        mockAiResponse.setDate("2026-03-28");
        mockAiResponse.setTime("null");
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        Transaction result1 = expenseServiceImpl.saveReceipt(mockFile);
        assertNotNull(result1.getDate());

        mockAiResponse.setTime("3 PM");
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        Transaction result2 = expenseServiceImpl.saveReceipt(mockFile);
        assertNotNull(result2.getDate());

        mockAiResponse.setTime("   ");
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        Transaction result3 = expenseServiceImpl.saveReceipt(mockFile);
        assertNotNull(result3.getDate());

        mockAiResponse.setTime(null);
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        Transaction result4 = expenseServiceImpl.saveReceipt(mockFile);
        assertNotNull(result4.getDate());
    }

    @Test
    void createManualExpense_ValidRequest_SavesConfirmedManualTransaction() {
        LocalDateTime beforeCreation = LocalDateTime.now();
        ExpenseRequest request = new ExpenseRequest(
            beforeCreation.toLocalDate(), 75000.0, " Food ", " GoPay ", " Coffee Purchase "
        );
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction result = expenseServiceImpl.createManualExpense(request);
        LocalDateTime afterCreation = LocalDateTime.now();

        assertEquals("MANUAL", result.getInputType());
        assertEquals("CONFIRMED", result.getValidationStatus());
        assertEquals(mockUser, result.getUser());
        assertFalse(result.getDate().isBefore(beforeCreation));
        assertFalse(result.getDate().isAfter(afterCreation));
        assertNotEquals(LocalTime.MIDNIGHT, result.getDate().toLocalTime());
        assertEquals(75000.0, result.getAmount());
        assertEquals("Food", result.getCategory());
        assertEquals("GoPay", result.getPaymentMethod());
        assertEquals("Coffee Purchase", result.getDescription());
        verify(transactionRepository).save(result);
    }

    @Test
    void updateExpense_TransactionBelongsToCurrentUser_UpdatesAndSavesIt() {
        ExpenseRequest request = validExpenseRequest();
        Transaction transaction = new Transaction();
        transaction.setId(99L);
        transaction.setDate(LocalDateTime.of(2026, 3, 30, 14, 15, 16));
        when(transactionRepository.findByIdAndUser(99L, mockUser)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(transaction)).thenReturn(transaction);

        Transaction result = expenseServiceImpl.updateExpense(99L, request);

        assertSame(transaction, result);
        assertEquals(LocalDateTime.of(2026, 4, 1, 14, 15, 16), transaction.getDate());
        assertEquals("Food", transaction.getCategory());
        assertEquals("GoPay", transaction.getPaymentMethod());
        assertEquals("Coffee Purchase", transaction.getDescription());
        verify(transactionRepository).findByIdAndUser(99L, mockUser);
        verify(transactionRepository).save(transaction);
    }

    @Test
    void updateExpense_TransactionNotOwnedOrMissing_ReturnsNotFound() {
        when(transactionRepository.findByIdAndUser(99L, mockUser)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> expenseServiceImpl.updateExpense(99L, validExpenseRequest())
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Transaction not found", exception.getReason());
        verify(transactionRepository).findByIdAndUser(99L, mockUser);
    }

    @Test
    void deleteExpense_TransactionBelongsToCurrentUser_SoftDeletesIt() {
        Transaction transaction = new Transaction();
        when(transactionRepository.findByIdAndUser(99L, mockUser)).thenReturn(Optional.of(transaction));

        expenseServiceImpl.deleteExpense(99L);

        verify(transactionRepository).findByIdAndUser(99L, mockUser);
        assertNotNull(transaction.getDeletedAt());
        verify(transactionRepository).save(transaction);
        verify(transactionRepository, never()).delete(transaction);
    }

    @Test
    void deleteExpense_TransactionNotOwnedOrMissing_ReturnsNotFound() {
        when(transactionRepository.findByIdAndUser(99L, mockUser)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> expenseServiceImpl.deleteExpense(99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Transaction not found", exception.getReason());
        verify(transactionRepository).findByIdAndUser(99L, mockUser);
    }

    @Test
    void getRecentExpenses_ReturnsFiveMostRecentTransactionsForCurrentUser() {
        List<Transaction> recent = List.of(mockTransaction);
        when(transactionRepository.findTop5ByUserOrderByDateDesc(mockUser)).thenReturn(recent);

        List<Transaction> result = expenseServiceImpl.getRecentExpenses();

        assertSame(recent, result);
        verify(transactionRepository).findTop5ByUserOrderByDateDesc(mockUser);
    }

    @Test
    void createManualExpense_InvalidRequest_ReturnsBadRequestForEveryValidationBranch() {
        assertInvalidManualExpense(null);
        assertInvalidManualExpense(new ExpenseRequest(null, 1.0, "Food", "GoPay", "Coffee Purchase"));
        assertInvalidManualExpense(new ExpenseRequest(LocalDate.now(), null, "Food", "GoPay", "Coffee Purchase"));
        assertInvalidManualExpense(new ExpenseRequest(LocalDate.now(), 0.0, "Food", "GoPay", "Coffee Purchase"));
        assertInvalidManualExpense(new ExpenseRequest(LocalDate.now(), 1.0, null, "GoPay", "Coffee Purchase"));
        assertInvalidManualExpense(new ExpenseRequest(LocalDate.now(), 1.0, " ", "GoPay", "Coffee Purchase"));
        assertInvalidManualExpense(new ExpenseRequest(LocalDate.now(), 1.0, "Food", null, "Coffee Purchase"));
        assertInvalidManualExpense(new ExpenseRequest(LocalDate.now(), 1.0, "Food", " ", "Coffee Purchase"));
        assertInvalidManualExpense(new ExpenseRequest(LocalDate.now(), 1.0, "Food", "GoPay", null));
        assertInvalidManualExpense(new ExpenseRequest(LocalDate.now(), 1.0, "Food", "GoPay", " "));
    }

    @Test
    void aiOperations_GuestUser_ConsumesTwoSharedTrialsThenBlocks() {
        mockUser.setIsGuest(true);
        mockUser.setAiTrialCount(2);
        when(userRepository.consumeGuestAiTrial(10L)).thenReturn(1, 1, 0);
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);
        when(geminiService.processNotification("Payment notification")).thenReturn(mockAiResponse);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        expenseServiceImpl.saveReceipt(mockFile);
        assertEquals(1, mockUser.getAiTrialCount());

        expenseServiceImpl.saveNotification("Payment notification");
        assertEquals(0, mockUser.getAiTrialCount());

        GuestAiTrialLimitException exception = assertThrows(
            GuestAiTrialLimitException.class,
            () -> expenseServiceImpl.saveReceipt(mockFile)
        );
        assertEquals(GuestAiTrialLimitException.ERROR_CODE, "GUEST_AI_LIMIT_REACHED");
        assertEquals(GuestAiTrialLimitException.ERROR_MESSAGE, exception.getMessage());
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(userRepository, times(3)).consumeGuestAiTrial(10L);
        verify(geminiService, times(1)).receiptProcess(mockFile);
    }

    @Test
    void saveReceipt_GuestAiProcessingFails_RestoresReservedTrial() {
        mockUser.setIsGuest(true);
        mockUser.setAiTrialCount(2);
        when(userRepository.consumeGuestAiTrial(10L)).thenReturn(1);
        when(userRepository.restoreGuestAiTrial(10L)).thenReturn(1);
        when(geminiService.receiptProcess(mockFile)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> expenseServiceImpl.saveReceipt(mockFile));

        assertEquals(2, mockUser.getAiTrialCount());
        verify(userRepository).consumeGuestAiTrial(10L);
        verify(userRepository).restoreGuestAiTrial(10L);
    }

    @Test
    void saveReceipt_GuestWithNullLocalCounter_UsesReservedTrialSafely() {
        mockUser.setIsGuest(true);
        mockUser.setAiTrialCount(null);
        when(userRepository.consumeGuestAiTrial(10L)).thenReturn(1);
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        expenseServiceImpl.saveReceipt(mockFile);

        assertEquals(0, mockUser.getAiTrialCount());
        verify(transactionRepository).save(any(Transaction.class));
    }

    private ExpenseRequest validExpenseRequest() {
        return new ExpenseRequest(
            LocalDate.of(2026, 4, 1), 75000.0, " Food ", " GoPay ", " Coffee Purchase "
        );
    }

    private void assertInvalidManualExpense(ExpenseRequest request) {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> expenseServiceImpl.createManualExpense(request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(
            "Date, positive amount, category, payment method, and description are required",
            exception.getReason()
        );
    }
}
