package org.edmund.brokeai.controller;

import org.edmund.brokeai.dto.PageEnvelope;
import org.edmund.brokeai.dto.ProfileSettingsApi;
import org.edmund.brokeai.exception.ApiExceptionHandler;
import org.edmund.brokeai.service.DataControlService;
import org.edmund.brokeai.service.ProfileSecurityService;
import org.edmund.brokeai.service.ProfileService;
import org.edmund.brokeai.service.SettingsService;
import org.edmund.brokeai.service.SupportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileSettingsControllerTest {
    @Mock private ProfileService profileService;
    @Mock private ProfileSecurityService profileSecurityService;
    @Mock private SettingsService settingsService;
    @Mock private DataControlService dataControlService;
    @Mock private SupportService supportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ProfileController(profileService),
                new ProfileSecurityController(profileSecurityService),
                new SettingsController(settingsService),
                new DataControlController(dataControlService),
                new SupportController(supportService)
            )
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void getProfile_ReturnsEnvelopeAndRevisionEtag() throws Exception {
        when(profileService.getProfile()).thenReturn(profile());

        mockMvc.perform(get("/api/v1/me"))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"4\""))
            .andExpect(jsonPath("$.data.username").value("edmund"))
            .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void updatePreferences_ReturnsCanonicalPreferenceAndEtag() throws Exception {
        ProfileSettingsApi.Preferences preferences = new ProfileSettingsApi.Preferences(
            "USD", "en", "US", "America/New_York", "dark", 3, Instant.now()
        );
        when(settingsService.updatePreferences(any(), anyString())).thenReturn(preferences);

        mockMvc.perform(patch("/api/v1/me/preferences")
                .header("If-Match", "\"2\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currencyCode\":\"USD\",\"regionCode\":\"US\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"3\""))
            .andExpect(jsonPath("$.data.currencyCode").value("USD"));
    }

    @Test
    void changePassword_ReturnsSecurityResult() throws Exception {
        when(profileSecurityService.changePassword(any(), any())).thenReturn(
            new ProfileSettingsApi.PasswordChange(Instant.now(), 2)
        );

        mockMvc.perform(post("/api/v1/me/password/change")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"old password\",\"newPassword\":\"a long secure passphrase\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.revokedSessionCount").value(2));
    }

    @Test
    void requestExport_ReturnsAcceptedJob() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(dataControlService.requestExport("export-key")).thenReturn(
            new ProfileSettingsApi.DataExport(jobId, "queued", "zip_json_csv", Instant.now(), null, null, null, null)
        );

        mockMvc.perform(post("/api/v1/me/data-exports").header("Idempotency-Key", "export-key"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.jobId").value(jobId.toString()))
            .andExpect(jsonPath("$.data.status").value("queued"));
    }

    @Test
    void getFaqs_ReturnsPaginatedContract() throws Exception {
        UUID faqId = UUID.randomUUID();
        when(supportService.getFaqs("en", null, 0, 20)).thenReturn(PageEnvelope.of(
            java.util.List.of(new ProfileSettingsApi.FaqArticle(faqId, "en", "account", "Help", "Body", 1)),
            0, 20, 1
        ));

        mockMvc.perform(get("/api/v1/support/faqs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].title").value("Help"))
            .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    private ProfileSettingsApi.Profile profile() {
        Instant now = Instant.now();
        return new ProfileSettingsApi.Profile(
            1L, "Edmund Aji", "edmund", "edmund@example.com", null, now,
            "+6281234567890", null, "active", 4, now, now
        );
    }
}
