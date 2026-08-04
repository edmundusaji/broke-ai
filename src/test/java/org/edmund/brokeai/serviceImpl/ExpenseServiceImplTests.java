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

    private MultipartFile mockFile;
    private AiExpenseResponse mockAiResponse;
    private Transaction mockTransaction;
    private AppUser mockUser;

    @BeforeEach
    void setUp() {
        mockFile = new MockMultipartFile("file", "struk.jpg",
                "image/jpeg", "dummy image content".getBytes());

        mockAiResponse = new AiExpenseResponse();
        mockAiResponse.setMerchant("Kopi Kenangan");
        mockAiResponse.setTotal(55000.0);
        mockAiResponse.setKategori("Makanan");
        mockAiResponse.setTanggal("2026-03-28");
        mockAiResponse.setWaktu("15:30:00");

        mockTransaction = new Transaction();
        mockTransaction.setId(1L);
        mockTransaction.setMerchant("Kopi Kenangan");
        mockTransaction.setJumlah(55000.0);

        mockUser = new AppUser();
        mockUser.setId(10L);
        mockUser.setNamaLengkap("Rani Test");
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
        assertEquals("Kopi Kenangan", result.getMerchant());

        verify(geminiService, times(1)).receiptProcess(mockFile);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void saveReceipt_FailedDueToNullTotal_Test() {
        mockAiResponse.setTotal(null);
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
        mockAiResponse.setTanggal("2026-03-28");
        mockAiResponse.setWaktu(null);

        String notifText = "Bayar 55000 di Kopi Kenangan";
        when(geminiService.prosesNotifikasi(notifText)).thenReturn(mockAiResponse);

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        Transaction result = expenseServiceImpl.saveNotification(notifText);

        assertNotNull(result);
        assertNotNull(result.getTanggal());
        assertEquals("NOTIFICATION", result.getTipeInput());

        verify(geminiService, times(1)).prosesNotifikasi(notifText);
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
        when(geminiService.prosesNotifikasi(anyString())).thenReturn(null);

        assertThrows(RuntimeException.class, () -> {
            expenseServiceImpl.saveNotification("Notif BCA");
        });
    }

    @Test
    void saveNotification_FailedDueToNullTotal_Test() {
        mockAiResponse.setTotal(null);
        when(geminiService.prosesNotifikasi(anyString())).thenReturn(mockAiResponse);

        assertThrows(RuntimeException.class, () -> {
            expenseServiceImpl.saveNotification("Notif OVO");
        });
    }

    @Test
    void parseDateAndTime_FallbackForInvalidDate_Test() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        mockAiResponse.setTanggal(null);
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        Transaction result1 = expenseServiceImpl.saveReceipt(mockFile);
        assertNotNull(result1.getTanggal());

        mockAiResponse.setTanggal("28 Maret 2026");
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        Transaction result2 = expenseServiceImpl.saveReceipt(mockFile);
        assertNotNull(result2.getTanggal());

        mockAiResponse.setTanggal("   ");
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        Transaction result3 = expenseServiceImpl.saveReceipt(mockFile);
        assertNotNull(result3.getTanggal());
    }

    @Test
    void parseDateAndTime_FallbackForInvalidTime_Test() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        mockAiResponse.setTanggal("2026-03-28");
        mockAiResponse.setWaktu("null");
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        Transaction result1 = expenseServiceImpl.saveReceipt(mockFile);
        assertNotNull(result1.getTanggal());

        mockAiResponse.setWaktu("Jam 3 Sore");
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        Transaction result2 = expenseServiceImpl.saveReceipt(mockFile);
        assertNotNull(result2.getTanggal());

        mockAiResponse.setWaktu("   ");
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        Transaction result3 = expenseServiceImpl.saveReceipt(mockFile);
        assertNotNull(result3.getTanggal());

        mockAiResponse.setWaktu(null);
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);

        Transaction result4 = expenseServiceImpl.saveReceipt(mockFile);
        assertNotNull(result4.getTanggal());
    }

    @Test
    void createManualExpense_ValidRequest_SavesConfirmedManualTransaction() {
        ExpenseRequest request = validExpenseRequest();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction result = expenseServiceImpl.createManualExpense(request);

        assertEquals("MANUAL", result.getTipeInput());
        assertEquals("CONFIRMED", result.getStatusValidasi());
        assertEquals(mockUser, result.getUser());
        assertEquals(LocalDate.of(2026, 4, 1).atStartOfDay(), result.getTanggal());
        assertEquals(75000.0, result.getJumlah());
        assertEquals("Food", result.getKategori());
        assertEquals("Cafe", result.getMerchant());
        verify(transactionRepository).save(result);
    }

    @Test
    void updateExpense_TransactionBelongsToCurrentUser_UpdatesAndSavesIt() {
        ExpenseRequest request = validExpenseRequest();
        Transaction transaction = new Transaction();
        transaction.setId(99L);
        when(transactionRepository.findByIdAndUser(99L, mockUser)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(transaction)).thenReturn(transaction);

        Transaction result = expenseServiceImpl.updateExpense(99L, request);

        assertSame(transaction, result);
        assertEquals("Food", transaction.getKategori());
        assertEquals("Cafe", transaction.getMerchant());
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
    void deleteExpense_TransactionBelongsToCurrentUser_DeletesIt() {
        Transaction transaction = new Transaction();
        when(transactionRepository.findByIdAndUser(99L, mockUser)).thenReturn(Optional.of(transaction));

        expenseServiceImpl.deleteExpense(99L);

        verify(transactionRepository).findByIdAndUser(99L, mockUser);
        verify(transactionRepository).delete(transaction);
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
    void createManualExpense_InvalidRequest_ReturnsBadRequestForEveryValidationBranch() {
        assertInvalidManualExpense(null);
        assertInvalidManualExpense(new ExpenseRequest(null, 1.0, "Food", "Cafe"));
        assertInvalidManualExpense(new ExpenseRequest(LocalDate.now(), null, "Food", "Cafe"));
        assertInvalidManualExpense(new ExpenseRequest(LocalDate.now(), 0.0, "Food", "Cafe"));
        assertInvalidManualExpense(new ExpenseRequest(LocalDate.now(), 1.0, null, "Cafe"));
        assertInvalidManualExpense(new ExpenseRequest(LocalDate.now(), 1.0, " ", "Cafe"));
        assertInvalidManualExpense(new ExpenseRequest(LocalDate.now(), 1.0, "Food", null));
        assertInvalidManualExpense(new ExpenseRequest(LocalDate.now(), 1.0, "Food", " "));
    }

    @Test
    void aiOperations_GuestUser_ConsumesTwoSharedTrialsThenBlocks() {
        mockUser.setIsGuest(true);
        mockUser.setAiTrialCount(2);
        when(userRepository.consumeGuestAiTrial(10L)).thenReturn(1, 1, 0);
        when(geminiService.receiptProcess(mockFile)).thenReturn(mockAiResponse);
        when(geminiService.prosesNotifikasi("Payment notification")).thenReturn(mockAiResponse);
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
        return new ExpenseRequest(LocalDate.of(2026, 4, 1), 75000.0, " Food ", " Cafe ");
    }

    private void assertInvalidManualExpense(ExpenseRequest request) {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> expenseServiceImpl.createManualExpense(request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Date, positive amount, category, and merchant are required", exception.getReason());
    }
}
