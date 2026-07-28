package org.edmund.brokeai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.edmund.brokeai.dto.LoginRequest;
import org.edmund.brokeai.dto.LoginResponse;
import org.edmund.brokeai.dto.RegisterRequest;
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
                .andExpect(jsonPath("$.message").value("User berhasil didaftarkan"));
    }

    @Test
    void login_Success_ReturnsOk() throws Exception {
        LoginRequest request = new LoginRequest("edmundus", "password123");
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo("Edmundus Aji", "aji@mail.com");
        LoginResponse response = new LoginResponse("mock-jwt-token", 3600, userInfo);

        Mockito.when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }
}