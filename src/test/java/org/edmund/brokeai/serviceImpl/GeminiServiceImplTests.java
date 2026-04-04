package org.edmund.brokeai.service.serviceimpl;

import org.edmund.brokeai.dto.AiExpenseResponse;
import org.edmund.brokeai.dto.GeminiRequest;
import org.edmund.brokeai.dto.GeminiResponse;
import org.edmund.brokeai.service.GeminiOutboundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiServiceImplTests {

    @InjectMocks
    private GeminiServiceImpl geminiService;

    @Mock
    private GeminiOutboundService geminiOutboundService;

    private String validJsonResponseString;

    @BeforeEach
    void setUp() {
        validJsonResponseString = "```json\n" +
                "{\n" +
                "  \"tanggal\": \"2026-03-28\",\n" +
                "  \"total\": 55000.0,\n" +
                "  \"kategori\": \"Makanan\",\n" +
                "  \"merchant\": \"Kopi Kenangan\",\n" +
                "  \"waktu\": \"15:30:00\"\n" +
                "}\n" +
                "```";
    }


    @Test
    void receiptProcess_Success_WithMarkdownAndMimeType_Test() {
        MockMultipartFile mockFile = new MockMultipartFile("file", "test.png", "image/png", "dummy".getBytes());
        GeminiResponse mockResponse = createMockGeminiResponse(validJsonResponseString);

        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(mockResponse);

        AiExpenseResponse result = geminiService.receiptProcess(mockFile);

        assertNotNull(result);
        assertEquals("Kopi Kenangan", result.getMerchant());
        assertEquals(55000.0, result.getTotal());

        verify(geminiOutboundService, times(1)).sendToGemini(any(GeminiRequest.class));
    }

    @Test
    void receiptProcess_FallbackMimeTypeNull_Test() {
        MockMultipartFile mockFile = new MockMultipartFile("file", "test.bin", null, "dummy".getBytes());
        GeminiResponse mockResponse = createMockGeminiResponse("{\"merchant\": \"Unknown\", \"total\": 10000}");

        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(mockResponse);

        AiExpenseResponse result = geminiService.receiptProcess(mockFile);

        assertNotNull(result);
        assertEquals("Unknown", result.getMerchant());
    }

    @Test
    void receiptProcess_ExceptionThrown_Test() throws IOException {
        MultipartFile errorFile = mock(MultipartFile.class);
        when(errorFile.getBytes()).thenThrow(new IOException("Simulasi disk error"));

        AiExpenseResponse result = geminiService.receiptProcess(errorFile);

        assertNotNull(result);
        assertNull(result.getMerchant());

        verify(geminiOutboundService, never()).sendToGemini(any());
    }

    @Test
    void prosesNotifikasi_Success_Test() {
        GeminiResponse mockResponse = createMockGeminiResponse(validJsonResponseString);
        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(mockResponse);

        AiExpenseResponse result = geminiService.prosesNotifikasi("Notifikasi Text");

        assertNotNull(result);
        assertEquals("Kopi Kenangan", result.getMerchant());
    }

    @Test
    void prosesNotifikasi_ExceptionThrown_Test() {
        GeminiResponse invalidJsonResponse = createMockGeminiResponse("{JSON_CACAT}");
        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(invalidJsonResponse);

        AiExpenseResponse result = geminiService.prosesNotifikasi("Notifikasi Text");

        assertNotNull(result);
        assertNull(result.getMerchant());
    }

    @Test
    void executeAndParse_ResponseIsNull_Test() {
        MockMultipartFile mockFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", "dummy".getBytes());
        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(null);

        AiExpenseResponse result = geminiService.receiptProcess(mockFile);

        assertNotNull(result);
        assertNull(result.getMerchant());
    }

    @Test
    void executeAndParse_CandidatesIsEmpty_Test() {
        MockMultipartFile mockFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", "dummy".getBytes());
        GeminiResponse emptyCandidatesResponse = new GeminiResponse(List.of()); // List kosong

        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(emptyCandidatesResponse);

        AiExpenseResponse result = geminiService.receiptProcess(mockFile);

        assertNotNull(result);
        assertNull(result.getMerchant());
    }

    // Mock Response Gemini
    private GeminiResponse createMockGeminiResponse(String textContent) {
        GeminiResponse.Part part = new GeminiResponse.Part(textContent);
        GeminiResponse.Content content = new GeminiResponse.Content(List.of(part));
        GeminiResponse.Candidate candidate = new GeminiResponse.Candidate(content);
        return new GeminiResponse(List.of(candidate));
    }
}