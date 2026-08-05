package org.edmund.brokeai.serviceImpl;

import org.edmund.brokeai.dto.AiExpenseResponse;
import org.edmund.brokeai.dto.GeminiRequest;
import org.edmund.brokeai.dto.GeminiResponse;
import org.edmund.brokeai.service.GeminiOutboundService;
import org.edmund.brokeai.service.serviceimpl.GeminiServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
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
                "  \"date\": \"2026-03-28\",\n" +
                "  \"time\": \"15:30:00\",\n" +
                "  \"amount\": 55000.0,\n" +
                "  \"category\": \"Food\",\n" +
                "  \"paymentMethod\": \"GoPay\",\n" +
                "  \"description\": \"Coffee Purchase\"\n" +
                "}\n" +
                "```";
    }


    @Test
    void receiptProcess_Success_WithMarkdownAndMimeType_Test() {
        MockMultipartFile mockFile = new MockMultipartFile("file", "test.png", "image/png", "dummy".getBytes());
        GeminiResponse mockResponse = createMockGeminiResponse(validJsonResponseString);
        ArgumentCaptor<GeminiRequest> requestCaptor = ArgumentCaptor.forClass(GeminiRequest.class);

        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(mockResponse);

        AiExpenseResponse result = geminiService.receiptProcess(mockFile);

        assertNotNull(result);
        assertEquals("GoPay", result.getPaymentMethod());
        assertEquals("Coffee Purchase", result.getDescription());
        assertEquals(55000.0, result.getAmount());

        verify(geminiOutboundService).sendToGemini(requestCaptor.capture());
        String promptText = requestCaptor.getValue().contents().getFirst().parts().getFirst().text();
        assertTrue(promptText.contains("paymentMethod (the payment provider or rail"));
        assertTrue(promptText.contains("description (a concise transaction purpose or purchased item"));
        assertTrue(promptText.contains("Do not use the storefront or merchant name as paymentMethod"));
    }

    @Test
    void receiptProcess_FallbackMimeTypeNull_Test() {
        MockMultipartFile mockFile = new MockMultipartFile("file", "test.bin", null, "dummy".getBytes());
        GeminiResponse mockResponse = createMockGeminiResponse("{\"paymentMethod\": \"Unknown\", \"amount\": 10000}");

        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(mockResponse);

        AiExpenseResponse result = geminiService.receiptProcess(mockFile);

        assertNotNull(result);
        assertEquals("Unknown", result.getPaymentMethod());
    }

    @Test
    void receiptProcess_ExceptionThrown_Test() throws IOException {
        MultipartFile errorFile = mock(MultipartFile.class);
        when(errorFile.getBytes()).thenThrow(new IOException("Simulated disk error"));

        AiExpenseResponse result = geminiService.receiptProcess(errorFile);

        assertNotNull(result);
        assertNull(result.getPaymentMethod());

        verify(geminiOutboundService, never()).sendToGemini(any());
    }

    @Test
    void processNotification_Success_Test() {
        GeminiResponse mockResponse = createMockGeminiResponse(validJsonResponseString);
        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(mockResponse);

        AiExpenseResponse result = geminiService.processNotification("Notification Text");

        assertNotNull(result);
        assertEquals("GoPay", result.getPaymentMethod());
        assertEquals("Coffee Purchase", result.getDescription());
    }

    @Test
    void processNotification_PromptIncludesCurrentDateFallbackInstruction_Test() {
        GeminiResponse mockResponse = createMockGeminiResponse(validJsonResponseString);
        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(mockResponse);
        ArgumentCaptor<GeminiRequest> requestCaptor = ArgumentCaptor.forClass(GeminiRequest.class);

        geminiService.processNotification("Payment completed for IDR 25,000");

        verify(geminiOutboundService).sendToGemini(requestCaptor.capture());
        String promptText = requestCaptor.getValue().contents().getFirst().parts().getFirst().text();

        assertTrue(promptText.contains("Today's date is " + LocalDate.now()));
        assertTrue(promptText.contains("If the provided text does not contain any explicit date"));
        assertTrue(promptText.contains("strictly return today's date as date"));
        assertTrue(promptText.contains("paymentMethod (the payment provider or rail"));
        assertTrue(promptText.contains("description (a concise transaction purpose or purchased item"));
        assertTrue(promptText.contains("Do not use the storefront or merchant name as paymentMethod"));
    }

    @Test
    void processNotification_ExceptionThrown_Test() {
        GeminiResponse invalidJsonResponse = createMockGeminiResponse("{JSON_CACAT}");
        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(invalidJsonResponse);

        AiExpenseResponse result = geminiService.processNotification("Notification Text");

        assertNotNull(result);
        assertNull(result.getPaymentMethod());
    }

    @Test
    void executeAndParse_ResponseIsNull_Test() {
        MockMultipartFile mockFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", "dummy".getBytes());
        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(null);

        AiExpenseResponse result = geminiService.receiptProcess(mockFile);

        assertNotNull(result);
        assertNull(result.getPaymentMethod());
    }

    @Test
    void executeAndParse_CandidatesIsEmpty_Test() {
        MockMultipartFile mockFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", "dummy".getBytes());
        GeminiResponse emptyCandidatesResponse = new GeminiResponse(List.of()); // List kosong

        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(emptyCandidatesResponse);

        AiExpenseResponse result = geminiService.receiptProcess(mockFile);

        assertNotNull(result);
        assertNull(result.getPaymentMethod());
    }

    // Mock Response Gemini
    private GeminiResponse createMockGeminiResponse(String textContent) {
        GeminiResponse.Part part = new GeminiResponse.Part(textContent);
        GeminiResponse.Content content = new GeminiResponse.Content(List.of(part));
        GeminiResponse.Candidate candidate = new GeminiResponse.Candidate(content);
        return new GeminiResponse(List.of(candidate));
    }
}
