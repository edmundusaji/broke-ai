package org.edmund.brokeai.controller;

import org.edmund.brokeai.dto.GeminiResponse;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.repository.TransactionRepository;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.security.JwtService;
import org.edmund.brokeai.service.RateLimitingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ExpenseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TransactionRepository transactionRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RateLimitingService rateLimitingService;

    private GeminiResponse mockGeminiResponse;
    private AppUser mockUser;

    @BeforeEach
    void setUp() {
        String fakeJsonResponse = "{\n" +
                "  \"date\": \"2026-03-28\",\n" +
                "  \"time\": \"09:15:00\",\n" +
                "  \"amount\": 75000.0,\n" +
                "  \"category\": \"Transportation\",\n" +
                "  \"paymentMethod\": \"GoPay\",\n" +
                "  \"description\": \"Grab Ride\"\n" +
                "}";

        mockGeminiResponse = createMockGeminiResponse(fakeJsonResponse);

        mockUser = new AppUser();
        mockUser.setId(1L);
        mockUser.setFullName("Rani Test");
        mockUser.setUsername("rani");
        mockUser.setEmail("rani@example.com");
        mockUser.setPassword("hashed-password");

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(rateLimitingService.tryConsume(anyLong())).thenReturn(true);
    }

    @Test
    void getSummary_WithoutParams_UsesCurrentDateAndReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/expense/summary")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }

    @Test
    void getSummary_WithParams_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/expense/summary")
                        .header("Authorization", authHeader())
                        .param("month", "5")
                        .param("year", "2026"))
                .andExpect(status().isOk());
    }

    @Test
    void getSummary_ServiceThrowsException_ReturnsInternalServerError() throws Exception {
        when(transactionRepository.getExpenseSummaryByUserAndDateRange(any(), any(), any()))
                .thenThrow(new RuntimeException("Simulated DB Error For Summary"));

        mockMvc.perform(get("/api/v1/expense/summary")
                        .header("Authorization", authHeader()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getHistory_WithoutParams_UsesCurrentDate_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/expense/history")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }

    @Test
    void getHistory_ServiceThrowsException_ReturnsInternalServerError() throws Exception {
        when(transactionRepository.findByUserAndDateBetweenOrderByDateDesc(any(), any(), any()))
                .thenThrow(new RuntimeException("Simulated DB Error For History"));

        mockMvc.perform(get("/api/v1/expense/history")
                        .header("Authorization", authHeader()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getHistory_Success_IntegrationTest() throws Exception {
        Transaction transaction = new Transaction();
        transaction.setId(300L);
        transaction.setPaymentMethod("GoPay");
        transaction.setDescription("Grab Ride");
        transaction.setCategory("Transportation");
        transaction.setAmount(75000.0);
        transaction.setDate(LocalDateTime.of(2026, 3, 28, 9, 15));
        transaction.setInputType("NOTIFICATION");
        transaction.setValidationStatus("PENDING");

        when(transactionRepository.findByUserAndDateBetweenOrderByDateDesc(
                any(AppUser.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        ))
                .thenReturn(List.of(transaction));

        mockMvc.perform(get("/api/v1/expense/history")
                        .header("Authorization", authHeader())
                        .param("month", "3")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(300L))
                .andExpect(jsonPath("$[0].paymentMethod").value("GoPay"))
                .andExpect(jsonPath("$[0].description").value("Grab Ride"))
                .andExpect(jsonPath("$[0].amount").value(75000.0));
    }

    @Test
    void getRecentExpenses_ReturnsFiveMostRecentTransactions() throws Exception {
        Transaction transaction = new Transaction();
        transaction.setId(301L);
        transaction.setPaymentMethod("Latest Payment Method");
        transaction.setDescription("Latest transaction");
        transaction.setDate(LocalDateTime.of(2026, 4, 1, 12, 0));
        when(transactionRepository.findTop5ByUserOrderByDateDesc(mockUser))
            .thenReturn(List.of(transaction));

        mockMvc.perform(get("/api/v1/expense/recent")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(301L))
                .andExpect(jsonPath("$[0].paymentMethod").value("Latest Payment Method"))
                .andExpect(jsonPath("$[0].description").value("Latest transaction"));
    }

    @Test
    void getRecentExpenses_ServiceThrowsException_ReturnsInternalServerError() throws Exception {
        when(transactionRepository.findTop5ByUserOrderByDateDesc(any()))
            .thenThrow(new RuntimeException("Simulated DB Error For Recent Expenses"));

        mockMvc.perform(get("/api/v1/expense/recent")
                        .header("Authorization", authHeader()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void processReceipt_Success_IntegrationTest() throws Exception {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedData = invocation.getArgument(0);
            savedData.setId(100L);
            return savedData;
        });

        try (MockedConstruction<RestTemplate> mockedRestTemplate = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForObject(anyString(), any(), eq(GeminiResponse.class)))
                            .thenReturn(mockGeminiResponse);
                })) {

            MockMultipartFile mockFile = new MockMultipartFile(
                    "file", "grab_receipt.jpg", "image/jpeg", "fake image data".getBytes());

            mockMvc.perform(multipart("/api/v1/expense/receipt")
                            .file(mockFile)
                            .header("Authorization", authHeader())
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(100L))
                    .andExpect(jsonPath("$.paymentMethod").value("GoPay"))
                    .andExpect(jsonPath("$.description").value("Grab Ride"))
                    .andExpect(jsonPath("$.amount").value(75000.0))
                    .andExpect(jsonPath("$.inputType").value("RECEIPT"))
                    .andExpect(jsonPath("$.validationStatus").value("PENDING"));
        }
    }

    @Test
    void processNotification_Success_IntegrationTest() throws Exception {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedData = invocation.getArgument(0);
            savedData.setId(200L);
            return savedData;
        });

        try (MockedConstruction<RestTemplate> mockedRestTemplate = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForObject(anyString(), any(), eq(GeminiResponse.class)))
                            .thenReturn(mockGeminiResponse);
                })) {

            String requestBody = "{ \"text\": \"Kamu telah membayar Grab sebesar Rp 75.000\" }";

            mockMvc.perform(post("/api/v1/expense/notification")
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(200L))
                    .andExpect(jsonPath("$.paymentMethod").value("GoPay"))
                    .andExpect(jsonPath("$.description").value("Grab Ride"))
                    .andExpect(jsonPath("$.inputType").value("NOTIFICATION"));
        }
    }

    @Test
    void processNotification_EmptyText_ReturnsBadRequest() throws Exception {
        String badRequestBody = "{ \"text\": \"\" }";

        mockMvc.perform(post("/api/v1/expense/notification")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processReceipt_EmptyFile_ReturnsBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/v1/expense/receipt")
                        .file(emptyFile)
                        .header("Authorization", authHeader())
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void processNotification_NullTextInsideBody_ReturnsBadRequest() throws Exception {
        String requestBody = "{ \"text\": null }";

        mockMvc.perform(post("/api/v1/expense/notification")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createManualExpense_ValidRequest_ReturnsCreatedTransaction() throws Exception {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            saved.setId(400L);
            return saved;
        });

        mockMvc.perform(post("/api/v1/expense/manual")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-03-28\",\"amount\":75000,\"category\":\"Transport\",\"paymentMethod\":\"GoPay\",\"description\":\"Grab Ride\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(400L))
                .andExpect(jsonPath("$.date", matchesPattern(
                    "2026-03-28T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?"
                )))
                .andExpect(jsonPath("$.paymentMethod").value("GoPay"))
                .andExpect(jsonPath("$.description").value("Grab Ride"))
                .andExpect(jsonPath("$.merchant").doesNotExist())
                .andExpect(jsonPath("$.inputType").value("MANUAL"))
                .andExpect(jsonPath("$.validationStatus").value("CONFIRMED"));
    }

    @Test
    void createManualExpense_GuestUser_ReturnsOk() throws Exception {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/expense/manual")
                        .header("Authorization", guestAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-03-28\",\"amount\":25000,\"category\":\"Food\",\"paymentMethod\":\"Cash\",\"description\":\"Coffee Purchase\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod").value("Cash"))
                .andExpect(jsonPath("$.description").value("Coffee Purchase"))
                .andExpect(jsonPath("$.inputType").value("MANUAL"));
    }

    @Test
    void processNotification_GuestWithNoTrials_ReturnsStructuredForbidden() throws Exception {
        when(userRepository.consumeGuestAiTrial(2L)).thenReturn(0);

        mockMvc.perform(post("/api/v1/expense/notification")
                        .header("Authorization", guestAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Payment of 25000 at Cafe\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FORBIDDEN"))
                .andExpect(jsonPath("$.code").value("GUEST_AI_LIMIT_REACHED"))
                .andExpect(jsonPath("$.message").value(
                    "You have used all 2 free AI scan trials. Please sign in to continue."
                ));
    }

    @Test
    void updateExpense_TransactionBelongsToUser_ReturnsUpdatedTransaction() throws Exception {
        Transaction transaction = new Transaction();
        transaction.setId(500L);
        transaction.setUser(mockUser);
        when(transactionRepository.findByIdAndUser(500L, mockUser)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(transaction)).thenReturn(transaction);

        mockMvc.perform(put("/api/v1/expense/500")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-04-01\",\"amount\":50000,\"category\":\"Food\",\"paymentMethod\":\"OVO\",\"description\":\"Lunch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod").value("OVO"))
                .andExpect(jsonPath("$.description").value("Lunch"))
                .andExpect(jsonPath("$.amount").value(50000));
    }

    @Test
    void deleteExpense_TransactionBelongsToUser_ReturnsNoContent() throws Exception {
        Transaction transaction = new Transaction();
        transaction.setId(600L);
        when(transactionRepository.findByIdAndUser(600L, mockUser)).thenReturn(Optional.of(transaction));

        mockMvc.perform(delete("/api/v1/expense/600")
                        .header("Authorization", authHeader()))
                .andExpect(status().isNoContent());
    }

    @Test
    void processNotification_MissingToken_ReturnsUnauthorized() throws Exception {
        String requestBody = "{ \"text\": \"Kamu telah membayar Grab sebesar Rp 75.000\" }";

        mockMvc.perform(post("/api/v1/expense/notification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void processNotification_RateLimitExceeded_ReturnsTooManyRequests() throws Exception {
        when(rateLimitingService.tryConsume(1L)).thenReturn(false);
        String requestBody = "{ \"text\": \"Kamu telah membayar Grab sebesar Rp 75.000\" }";

        mockMvc.perform(post("/api/v1/expense/notification")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void processReceipt_ServiceThrowsException_ReturnsInternalServerError() throws Exception {
        GeminiResponse badGeminiResponse = createMockGeminiResponse("{\"paymentMethod\": \"GoPay\"}");

        try (MockedConstruction<RestTemplate> mockedRestTemplate = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForObject(anyString(), any(), eq(GeminiResponse.class)))
                            .thenReturn(badGeminiResponse);
                })) {

            when(transactionRepository.save(any())).thenThrow(new RuntimeException("DB Error"));

            MockMultipartFile mockFile = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "data".getBytes());

            mockMvc.perform(multipart("/api/v1/expense/receipt")
                            .file(mockFile)
                            .header("Authorization", authHeader())
                    )
                    .andExpect(status().isInternalServerError());
        }
    }

    @Test
    void processNotification_ServiceThrowsException_ReturnsInternalServerError() throws Exception {
        GeminiResponse badGeminiResponse = createMockGeminiResponse("{\"paymentMethod\": \"GoPay\"}");

        try (MockedConstruction<RestTemplate> mockedRestTemplate = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForObject(anyString(), any(), eq(GeminiResponse.class)))
                            .thenReturn(badGeminiResponse);
                })) {

            when(transactionRepository.save(any())).thenThrow(new RuntimeException("DB Error"));

            String requestBody = "{ \"text\": \"Notification\" }";

            mockMvc.perform(post("/api/v1/expense/notification")
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isInternalServerError());
        }
    }

    private GeminiResponse createMockGeminiResponse(String textContent) {
        GeminiResponse.Part part = new GeminiResponse.Part(textContent);
        GeminiResponse.Content content = new GeminiResponse.Content(List.of(part));
        GeminiResponse.Candidate candidate = new GeminiResponse.Candidate(content);
        return new GeminiResponse(List.of(candidate));
    }

    private String authHeader() {
        return "Bearer " + jwtService.generateToken(mockUser);
    }

    private String guestAuthHeader() {
        AppUser guest = new AppUser();
        guest.setId(2L);
        guest.setFullName("Guest User");
        guest.setUsername("guest_123");
        guest.setIsGuest(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(guest));
        return "Bearer " + jwtService.generateToken(guest);
    }
}
