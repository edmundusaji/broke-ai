package org.edmund.brokeai.controller;

import org.edmund.brokeai.dto.GeminiResponse;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * INTEGRATION TEST
 * Flow: Controller -> ExpenseService -> GeminiService
 * Mock : Database (Repository) & HTTP Connection (OutboundService)
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ExpenseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExpenseController expenseController;

    @MockitoBean
    private TransactionRepository transactionRepository;

    private GeminiResponse mockGeminiResponse;

    @BeforeEach
    void setUp() {
        String fakeJsonResponse = "{\n" +
                "  \"tanggal\": \"2026-03-28\",\n" +
                "  \"total\": 75000.0,\n" +
                "  \"kategori\": \"Transportasi\",\n" +
                "  \"merchant\": \"Grab\",\n" +
                "  \"waktu\": \"09:15:00\"\n" +
                "}";

        mockGeminiResponse = createMockGeminiResponse(fakeJsonResponse);
    }

    @Test
    void processReceipt_Success_IntegrationTest() throws Exception {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedData = invocation.getArgument(0);
            savedData.setId(100L); // Mock postgre gives ID
            return savedData;
        });

        // Setup Mock HTTP Connection
        // Because GeminiOutboundServiceImpl is using "new RestTemplate()", we have to intercept the constructor
        try (MockedConstruction<RestTemplate> mockedRestTemplate = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForObject(anyString(), any(), eq(GeminiResponse.class)))
                            .thenReturn(mockGeminiResponse);
                })) {

            MockMultipartFile mockFile = new MockMultipartFile(
                    "file", "struk_grab.jpg", "image/jpeg", "fake image data".getBytes());

            mockMvc.perform(multipart("/api/v1/expense/receipt")
                            .file(mockFile))
                    .andExpect(status().isOk()) // HTTP 200 OK
                    .andExpect(jsonPath("$.id").value(100L))
                    .andExpect(jsonPath("$.merchant").value("Grab"))
                    .andExpect(jsonPath("$.jumlah").value(75000.0))
                    .andExpect(jsonPath("$.tipeInput").value("RECEIPT"))
                    .andExpect(jsonPath("$.statusValidasi").value("PENDING"));
        }
    }

    @Test
    void processNotification_Success_IntegrationTest() throws Exception {

        // Mock Database
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedData = invocation.getArgument(0);
            savedData.setId(200L);
            return savedData;
        });

        // Setup Mock HTTP Connection
        try (MockedConstruction<RestTemplate> mockedRestTemplate = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForObject(anyString(), any(), eq(GeminiResponse.class)))
                            .thenReturn(mockGeminiResponse);
                })) {

            String requestBody = "{ \"text\": \"Kamu telah membayar Grab sebesar Rp 75.000\" }";

            mockMvc.perform(post("/api/v1/expense/notification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk()) // HTTP 200 OK
                    .andExpect(jsonPath("$.id").value(200L))
                    .andExpect(jsonPath("$.merchant").value("Grab"))
                    .andExpect(jsonPath("$.tipeInput").value("NOTIFICATION"));
        }
    }

    @Test
    void processNotification_EmptyText_ReturnsBadRequest() throws Exception {
        String badRequestBody = "{ \"text\": \"\" }";

        mockMvc.perform(post("/api/v1/expense/notification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processReceipt_EmptyFile_ReturnsBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/v1/expense/receipt").file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processNotification_NullTextInsideBody_ReturnsBadRequest() throws Exception {
        String requestBody = "{ \"text\": null }";

        mockMvc.perform(post("/api/v1/expense/notification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void triggerDeadCodeNullChecks_DirectlyForCoverage() {
        ResponseEntity<Transaction> r1 = expenseController.processReceipt(null);
        assertEquals(400, r1.getStatusCode().value());

        ResponseEntity<Transaction> r2 = expenseController.processNotification(null);
        assertEquals(400, r2.getStatusCode().value());
    }

    @Test
    void processReceipt_ServiceThrowsException_ReturnsInternalServerError() throws Exception {
        GeminiResponse badGeminiResponse = createMockGeminiResponse("{\"merchant\": \"Grab\"}");

        try (MockedConstruction<RestTemplate> mockedRestTemplate = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForObject(anyString(), any(), eq(GeminiResponse.class)))
                            .thenReturn(badGeminiResponse);
                })) {

            MockMultipartFile mockFile = new MockMultipartFile("file", "struk.jpg", "image/jpeg", "data".getBytes());

            mockMvc.perform(multipart("/api/v1/expense/receipt").file(mockFile))
                    .andExpect(status().isInternalServerError()); // 500
        }
    }

    @Test
    void processNotification_ServiceThrowsException_ReturnsInternalServerError() throws Exception {
        GeminiResponse badGeminiResponse = createMockGeminiResponse("{\"merchant\": \"Grab\"}");

        try (MockedConstruction<RestTemplate> mockedRestTemplate = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForObject(anyString(), any(), eq(GeminiResponse.class)))
                            .thenReturn(badGeminiResponse);
                })) {

            String requestBody = "{ \"text\": \"Notifikasi\" }";

            mockMvc.perform(post("/api/v1/expense/notification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isInternalServerError()); // 500
        }
    }

    private GeminiResponse createMockGeminiResponse(String textContent) {
        GeminiResponse.Part part = new GeminiResponse.Part(textContent);
        GeminiResponse.Content content = new GeminiResponse.Content(List.of(part));
        GeminiResponse.Candidate candidate = new GeminiResponse.Candidate(content);
        return new GeminiResponse(List.of(candidate));
    }
}