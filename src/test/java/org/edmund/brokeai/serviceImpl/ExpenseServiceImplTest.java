package org.edmund.brokeai.serviceImpl;

import org.edmund.brokeai.dto.AiExpenseResponse;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.repository.TransactionRepository;
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
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceImplTest {

    @InjectMocks
    private ExpenseServiceImpl expenseServiceImpl;

    @Mock
    private GeminiService geminiService;

    @Mock
    private TransactionRepository transactionRepository;

    private MultipartFile mockFile;
    private AiExpenseResponse mockAiResponse;
    private Transaction mockTransaction;

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

        assertEquals("⛔ [EXPENSE SERVICE] Failed to process receipt", exception.getMessage());

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

        assertEquals("⛔ [EXPENSE SERVICE] Failed to process receipt", exception.getMessage());
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
}