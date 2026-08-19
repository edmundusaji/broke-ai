package org.edmund.brokeai.controller;

import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.repository.SupportTicketRepository;
import org.edmund.brokeai.repository.TransactionRepository;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.security.JwtService;
import org.edmund.brokeai.service.AccountNotificationService;
import org.edmund.brokeai.service.SecurityAuditService;
import org.edmund.brokeai.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GuestProfileBackendIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private SupportTicketRepository supportTicketRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private AccountNotificationService accountNotificationService;
    @MockitoBean private SecurityAuditService securityAuditService;
    @MockitoBean private UserSyncService userSyncService;

    @BeforeEach
    void ensureSyncTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS user_sync_state (
                user_id BIGINT PRIMARY KEY,
                status VARCHAR(30) NOT NULL,
                last_synced_at TIMESTAMP WITH TIME ZONE,
                server_revision BIGINT NOT NULL
            )
            """);
    }

    @Test
    void publicFaqAndGuestTickets_AllowGuestButEnforceTicketOwnership() throws Exception {
        mockMvc.perform(get("/api/v1/support/faqs"))
            .andExpect(status().isOk());

        AppUser owner = saveGuest("guest_ticket_owner");
        AppUser stranger = saveGuest("guest_ticket_other");
        String ownerToken = bearer(owner);
        String strangerToken = bearer(stranger);

        String response = mockMvc.perform(post("/api/v1/support/tickets")
                .with(request -> { request.setRemoteAddr("10.0.0.11"); return request; })
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type":"bug",
                      "subject":"Guest profile issue",
                      "message":"The guest page did not refresh.",
                      "appVersion":"1.2.3",
                      "platform":"android",
                      "osVersion":"15",
                      "deviceModel":"Pixel",
                      "locale":"en-ID",
                      "diagnosticMetadata":{"buildNumber":"42","receipt":"must-not-pass"},
                      "contactEmail":"guest@example.com",
                      "contactConsent":true
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        UUID ticketId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(response).path("data").path("id").asText());
        var stored = supportTicketRepository.findById(ticketId).orElseThrow();
        assertTrue(stored.getContactEmailEncrypted() != null && !stored.getContactEmailEncrypted().contains("@"));
        assertFalse(stored.getDiagnosticMetadata().containsKey("receipt"));

        mockMvc.perform(get("/api/v1/support/tickets/{ticketId}", ticketId)
                .header("Authorization", ownerToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/support/tickets/{ticketId}", ticketId)
                .header("Authorization", strangerToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void guestSummaryAndClear_AreOwnerScopedAndIdempotentWithoutPassword() throws Exception {
        AppUser guest = saveGuest("guest_clear_owner");
        saveTransaction(guest, "Coffee", 25000.0, LocalDateTime.of(2026, 8, 1, 8, 30));
        saveTransaction(guest, "Lunch", 45000.0, LocalDateTime.of(2026, 8, 19, 12, 15));
        AppUser other = saveGuest("guest_clear_other");
        saveTransaction(other, "Other", 1.0, LocalDateTime.of(2026, 8, 2, 9, 0));

        mockMvc.perform(get("/api/v1/guest/data-summary").header("Authorization", bearer(guest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.transactionCount").value(2))
            .andExpect(jsonPath("$.data.remainingAiTrials").value(2));

        String key = UUID.randomUUID().toString();
        for (int requestNumber = 0; requestNumber < 2; requestNumber++) {
            final int suffix = requestNumber;
            mockMvc.perform(post("/api/v1/guest/transactions/clear")
                    .with(request -> { request.setRemoteAddr("10.0.1." + suffix); return request; })
                    .header("Authorization", bearer(guest))
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"confirmation\":\"CLEAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(2));
        }
        assertTrue(transactionRepository.findByUserIdAndDeletedAtIsNullOrderByDateDesc(guest.getId()).isEmpty());
        assertTrue(transactionRepository.findByUserIdAndDeletedAtIsNullOrderByDateDesc(other.getId()).size() == 1);
    }

    @Test
    void registeredUser_CannotUseGuestOnlyDataControls() throws Exception {
        AppUser user = saveRegistered("registered_guest_boundary", "registered-boundary@example.com", "password123");

        mockMvc.perform(get("/api/v1/guest/data-summary").header("Authorization", bearer(user)))
            .andExpect(status().isForbidden());
    }

    @Test
    void guestExport_IsAllowedAndRemainsOwnerScoped() throws Exception {
        AppUser owner = saveGuest("guest_export_owner");
        AppUser other = saveGuest("guest_export_other");

        String response = mockMvc.perform(post("/api/v1/me/data-exports")
                .with(request -> { request.setRemoteAddr("10.0.4.1"); return request; })
                .header("Authorization", bearer(owner))
                .header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status").value("queued"))
            .andReturn().getResponse().getContentAsString();
        String jobId = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(response).path("data").path("jobId").asText();

        mockMvc.perform(get("/api/v1/me/data-exports/{jobId}", jobId)
                .header("Authorization", bearer(owner)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/data-exports/{jobId}", jobId)
                .header("Authorization", bearer(other)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteGuest_RevokesIdentityAndPurgesOwnedTransactions() throws Exception {
        AppUser guest = saveGuest("guest_delete_owner");
        Long guestId = guest.getId();
        String originalUsername = guest.getUsername();
        saveTransaction(guest, "Delete me", 10.0, LocalDateTime.of(2026, 8, 10, 10, 0));

        mockMvc.perform(delete("/api/v1/guest-account")
                .with(request -> { request.setRemoteAddr("10.0.2.1"); return request; })
                .header("Authorization", bearer(guest))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"DELETE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deleted").value(true));

        AppUser tombstone = userRepository.findById(guestId).orElseThrow();
        assertTrue("deleted".equals(tombstone.getStatus()));
        assertNotEquals(originalUsername, tombstone.getUsername());
        assertTrue(transactionRepository.findByUserIdAndDeletedAtIsNullOrderByDateDesc(guestId).isEmpty());
    }

    @Test
    void mergeGuest_MovesUniqueTransactionsAndSkipsDuplicates() throws Exception {
        AppUser guest = saveGuest("guest_merge_source");
        AppUser destination = saveRegistered("merge_destination", "merge@example.com", "destination-password");
        LocalDateTime duplicateDate = LocalDateTime.of(2026, 8, 12, 9, 0);
        saveTransaction(guest, "Duplicate", 15.0, duplicateDate);
        saveTransaction(destination, "Duplicate", 15.0, duplicateDate);
        saveTransaction(guest, "Unique", 30.0, LocalDateTime.of(2026, 8, 13, 10, 0));
        Long guestId = guest.getId();

        mockMvc.perform(post("/api/v1/auth/merge-guest")
                .with(request -> { request.setRemoteAddr("10.0.3.1"); return request; })
                .header("Authorization", bearer(guest))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"merge_destination\",\"password\":\"destination-password\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isGuest").value(false))
            .andExpect(jsonPath("$.mergeResult.transactionsMoved").value(1))
            .andExpect(jsonPath("$.mergeResult.duplicatesSkipped").value(1));

        assertFalse(userRepository.existsById(guestId));
        assertTrue(transactionRepository.findByUserIdAndDeletedAtIsNullOrderByDateDesc(destination.getId()).size() == 2);
    }

    private AppUser saveGuest(String username) {
        AppUser user = new AppUser();
        user.setFullName("Guest User");
        user.setUsername(username);
        user.setIsGuest(true);
        user.setAiTrialCount(2);
        return userRepository.saveAndFlush(user);
    }

    private AppUser saveRegistered(String username, String email, String password) {
        AppUser user = new AppUser();
        user.setFullName("Registered User");
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setIsGuest(false);
        user.setAiTrialCount(0);
        return userRepository.saveAndFlush(user);
    }

    private void saveTransaction(AppUser user, String description, double amount, LocalDateTime date) {
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setDescription(description);
        transaction.setPaymentMethod("cash");
        transaction.setCategory("Other");
        transaction.setAmount(amount);
        transaction.setDate(date);
        transaction.setInputType("MANUAL");
        transaction.setValidationStatus("CONFIRMED");
        transactionRepository.saveAndFlush(transaction);
    }

    private String bearer(AppUser user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
