package org.edmund.brokeai.controller;

import org.edmund.brokeai.entity.AppUser;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileSettingsFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    @MockitoBean private AccountNotificationService accountNotificationService;
    @MockitoBean private SecurityAuditService securityAuditService;
    @MockitoBean private UserSyncService userSyncService;

    private String authorization;

    @BeforeEach
    void createUser() {
        userRepository.deleteAll();
        AppUser user = new AppUser();
        user.setFullName("Edee");
        user.setUsername("edee");
        user.setEmail("edee@gmail.com");
        user.setPassword(passwordEncoder.encode("edee"));
        user.setIsGuest(false);
        user.setAiTrialCount(0);
        AppUser saved = userRepository.saveAndFlush(user);
        authorization = "Bearer " + jwtService.generateToken(saved);
    }

    @Test
    void authenticatedUser_CanCreateDefaultSettings() throws Exception {
        mockMvc.perform(get("/api/v1/me/preferences")
                .header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currencyCode").value("IDR"))
            .andExpect(jsonPath("$.data.regionCode").value("ID"));

        mockMvc.perform(get("/api/v1/me/notification-preferences")
                .header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.securityAlertsEnabled").value(true));

        mockMvc.perform(get("/api/v1/me/privacy-preferences")
                .header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.personalizedInsights").value(true));
    }

    @Test
    void authenticatedUser_CanRequestEmailChange() throws Exception {
        mockMvc.perform(post("/api/v1/me/email-change")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"ppp@g.com\",\"currentPassword\":\"edee\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.pendingEmail").value("ppp@g.com"));
    }
}
