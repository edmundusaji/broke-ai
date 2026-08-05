package org.edmund.brokeai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.edmund.brokeai.dto.LoginRequest;
import org.edmund.brokeai.dto.LoginResponse;
import org.edmund.brokeai.dto.RegisterRequest;
import org.edmund.brokeai.dto.UpgradeGuestRequest;
import org.edmund.brokeai.dto.CurrentUserResponse;
import org.edmund.brokeai.service.AuthService;
import org.edmund.brokeai.service.RateLimitingService;
import org.edmund.brokeai.security.JwtService;
import org.edmund.brokeai.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RateLimitingService rateLimitingService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void register_Success_ReturnsOk() throws Exception {
        RegisterRequest request = new RegisterRequest("Edmundus Aji", "edmundus", "aji@mail.com", "password123");
        Mockito.doNothing().when(authService).register(any(RegisterRequest.class));
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

    @Test
    void login_Success_ReturnsOk() throws Exception {
        LoginRequest request = new LoginRequest("edmundus", "password123");
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo("Edmundus Aji", "aji@mail.com");
        LoginResponse response = new LoginResponse("mock-jwt-token", 3600, "edmundus", false, 0, userInfo);

        Mockito.when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.username").value("edmundus"))
                .andExpect(jsonPath("$.isGuest").value(false));
    }

    @Test
    void guestLogin_Success_ReturnsGuestSession() throws Exception {
        LoginResponse response = new LoginResponse(
            "guest-token",
            3600,
            "guest_123",
            true,
            2,
            new LoginResponse.UserInfo("Guest User", null)
        );
        Mockito.when(authService.guestLogin()).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/guest-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("guest-token"))
                .andExpect(jsonPath("$.username").value("guest_123"))
                .andExpect(jsonPath("$.isGuest").value(true))
                .andExpect(jsonPath("$.remaining_ai_trials").value(2))
                .andExpect(jsonPath("$.user.fullName").value("Guest User"));
    }

    @Test
    void upgradeGuest_Success_ReturnsRegisteredSession() throws Exception {
        UpgradeGuestRequest request = new UpgradeGuestRequest("Edmundus Aji", "aji@mail.com", "password123");
        LoginResponse response = new LoginResponse(
            "upgraded-token",
            3600,
            "guest_123",
            false,
            0,
            new LoginResponse.UserInfo("Edmundus Aji", "aji@mail.com")
        );
        Mockito.when(authService.upgradeGuest(any(UpgradeGuestRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/upgrade-guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("upgraded-token"))
                .andExpect(jsonPath("$.isGuest").value(false))
                .andExpect(jsonPath("$.user.fullName").value("Edmundus Aji"))
                .andExpect(jsonPath("$.user.email").value("aji@mail.com"));
    }

    @Test
    void getCurrentUser_Guest_ReturnsRemainingTrials() throws Exception {
        CurrentUserResponse response = new CurrentUserResponse(
            "guest_123", "Guest User", null, "ROLE_GUEST", 1
        );
        Mockito.when(authService.getCurrentUser()).thenReturn(response);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Guest User"))
                .andExpect(jsonPath("$.role").value("ROLE_GUEST"))
                .andExpect(jsonPath("$.remaining_ai_trials").value(1));
    }
}
