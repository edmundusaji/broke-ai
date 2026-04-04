package org.edmund.brokeai.controller;

import org.edmund.brokeai.dto.GeminiRequest;
import org.edmund.brokeai.dto.GeminiResponse;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.repository.TransactionRepository;
import org.edmund.brokeai.service.GeminiOutboundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * INTEGRATION TEST
 * Mengetes aliran data dari: Controller -> ExpenseService -> GeminiService
 * Yang di-mock HANYA: Database (Repository) & Koneksi Internet (OutboundService)
 */
@SpringBootTest // Menyalakan seluruh mesin Spring Boot
@AutoConfigureMockMvc // Menyiapkan "Postman" virtual di dalam kode
class ExpenseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc; // Ini "Postman" kita

    // Kita cegat koneksi ke Google API
    @MockBean
    private GeminiOutboundService geminiOutboundService;

    // Kita cegat koneksi ke PostgreSQL
    @MockBean
    private TransactionRepository transactionRepository;

    private GeminiResponse mockGeminiResponse;

    @BeforeEach
    void setUp() {
        // Kita siapkan balasan palsu dari Google Gemini
        String fakeJsonResponse = "{\n" +
                "  \"tanggal\": \"2026-03-28\",\n" +
                "  \"total\": 75000.0,\n" +
                "  \"kategori\": \"Transportasi\",\n" +
                "  \"merchant\": \"Gojek\",\n" +
                "  \"waktu\": \"09:15:00\"\n" +
                "}";

        mockGeminiResponse = createMockGeminiResponse(fakeJsonResponse);
    }

    // ========================================================================
    // 1. INTEGRATION TEST: ENDPOINT /RECEIPT (GAMBAR)
    // ========================================================================
    @Test
    void processReceipt_Success_IntegrationTest() throws Exception {
        // 1. SETUP MOCKING
        // Saat GeminiService memanggil Outbound, kembalikan balasan palsu kita
        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(mockGeminiResponse);

        // Saat ExpenseService mau nge-save ke DB, pura-pura berhasil dan set ID = 100
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedData = invocation.getArgument(0);
            savedData.setId(100L);
            return savedData;
        });

        // 2. SIAPKAN FILE PALSU
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "struk_gojek.jpg", "image/jpeg", "fake image data".getBytes());

        // 3. EKSEKUSI & VERIFIKASI (Simulasi tembakan HTTP dari Postman)
        mockMvc.perform(multipart("/api/v1/expense/receipt")
                        .file(mockFile))
                .andExpect(status().isOk()) // Harus mengembalikan HTTP 200 OK
                .andExpect(jsonPath("$.id").value(100L)) // ID dari DB Mock
                .andExpect(jsonPath("$.merchant").value("Gojek"))
                .andExpect(jsonPath("$.jumlah").value(75000.0))
                .andExpect(jsonPath("$.tipeInput").value("RECEIPT"))
                .andExpect(jsonPath("$.statusValidasi").value("PENDING"));
    }

    // ========================================================================
    // 2. INTEGRATION TEST: ENDPOINT /NOTIFICATION (TEKS)
    // ========================================================================
    @Test
    void processNotification_Success_IntegrationTest() throws Exception {
        // 1. SETUP MOCKING
        when(geminiOutboundService.sendToGemini(any(GeminiRequest.class))).thenReturn(mockGeminiResponse);

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedData = invocation.getArgument(0);
            savedData.setId(200L);
            return savedData;
        });

        // 2. SIAPKAN REQUEST BODY (JSON)
        String requestBody = "{ \"text\": \"Kamu telah membayar Gojek sebesar Rp 75.000\" }";

        // 3. EKSEKUSI & VERIFIKASI
        mockMvc.perform(post("/api/v1/expense/notification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk()) // HTTP 200 OK
                .andExpect(jsonPath("$.id").value(200L))
                .andExpect(jsonPath("$.merchant").value("Gojek"))
                .andExpect(jsonPath("$.tipeInput").value("NOTIFICATION"));
    }

    // ========================================================================
    // 3. INTEGRATION TEST: JALUR ERROR (BAD REQUEST)
    // ========================================================================
    @Test
    void processNotification_EmptyText_ReturnsBadRequest() throws Exception {
        // Jika text kosong, Controller harus langsung menolak sebelum memanggil Service
        String badRequestBody = "{ \"text\": \"\" }";

        mockMvc.perform(post("/api/v1/expense/notification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequestBody))
                .andExpect(status().isBadRequest()); // HTTP 400 Bad Request
    }
    
    private GeminiResponse createMockGeminiResponse(String textContent) {
        GeminiResponse.Part part = new GeminiResponse.Part(textContent);
        GeminiResponse.Content content = new GeminiResponse.Content(List.of(part));
        GeminiResponse.Candidate candidate = new GeminiResponse.Candidate(content);
        return new GeminiResponse(List.of(candidate));
    }
}