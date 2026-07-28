package org.edmund.brokeai.serviceImpl;

import org.edmund.brokeai.dto.LoginRequest;
import org.edmund.brokeai.dto.LoginResponse;
import org.edmund.brokeai.dto.RegisterRequest;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.security.JwtService;
import org.edmund.brokeai.service.serviceimpl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void register_Success_SavesUser() {
        RegisterRequest request = new RegisterRequest(" Edmundus Aji ", " edmundus ", " AJI@mail.com ", "password123");

        when(userRepository.existsByUsername("edmundus")).thenReturn(false);
        when(userRepository.existsByEmail("aji@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");

        authService.register(request);

        verify(userRepository, times(1)).save(argThat(user ->
                user.getNamaLengkap().equals("Edmundus Aji") &&
                        user.getUsername().equals("edmundus") &&
                        user.getEmail().equals("aji@mail.com") &&
                        user.getPassword().equals("encodedPassword")
        ));
    }

    @Test
    void register_IncompleteData_ThrowsBadRequest() {
        RegisterRequest requestNull = null;
        RegisterRequest requestBlankName = new RegisterRequest(" ", "edmundus", "aji@mail.com", "pass");

        assertThrows(ResponseStatusException.class, () -> authService.register(requestNull));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.register(requestBlankName));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Data register belum lengkap"));
    }

    @Test
    void register_UsernameExists_ThrowsBadRequest() {
        RegisterRequest request = new RegisterRequest("Aji", "edmundus", "aji@mail.com", "pass");
        when(userRepository.existsByUsername("edmundus")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.register(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Username sudah dipakai"));
        verify(userRepository, never()).save(any()); // Pastikan tidak ada data yang disimpan
    }

    @Test
    void register_EmailExists_ThrowsBadRequest() {
        RegisterRequest request = new RegisterRequest("Aji", "edmundus", "aji@mail.com", "pass");
        when(userRepository.existsByUsername("edmundus")).thenReturn(false);
        when(userRepository.existsByEmail("aji@mail.com")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.register(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Email sudah dipakai"));
        verify(userRepository, never()).save(any());
    }

    // ==========================================
    // TEST UNTUK LOGIN
    // ==========================================

    @Test
    void login_Success_ReturnsToken() {
        LoginRequest request = new LoginRequest(" edmundus ", "password123");
        AppUser mockUser = new AppUser();
        mockUser.setNamaLengkap("Edmundus Aji");
        mockUser.setEmail("aji@mail.com");
        mockUser.setPassword("encodedPassword");

        when(userRepository.findByUsername("edmundus")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
        when(jwtService.generateToken(mockUser)).thenReturn("mock-jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.token());
        assertEquals(3600L, response.expiresIn());
        assertEquals("Edmundus Aji", response.user().name());
    }

    @Test
    void login_IncompleteData_ThrowsUnauthorized() {
        LoginRequest requestNull = null;
        LoginRequest requestBlankUsername = new LoginRequest(" ", "pass");

        assertThrows(ResponseStatusException.class, () -> authService.login(requestNull));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.login(requestBlankUsername));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void login_UserNotFound_ThrowsUnauthorized() {
        LoginRequest request = new LoginRequest("unknown_user", "pass");
        when(userRepository.findByUsername("unknown_user")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.login(request));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void login_WrongPassword_ThrowsUnauthorized() {
        LoginRequest request = new LoginRequest("edmundus", "wrong_password");
        AppUser mockUser = new AppUser();
        mockUser.setPassword("encodedPassword");

        when(userRepository.findByUsername("edmundus")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrong_password", "encodedPassword")).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.login(request));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void register_IncompleteData_ThrowsBadRequest_AllBranches() {
        assertThrows(ResponseStatusException.class, () -> authService.register(null));

        RegisterRequest blankName = new RegisterRequest(" ", "edmundus", "aji@mail.com", "pass123");
        assertThrows(ResponseStatusException.class, () -> authService.register(blankName));

        RegisterRequest blankUsername = new RegisterRequest("Edmundus", "", "aji@mail.com", "pass123");
        assertThrows(ResponseStatusException.class, () -> authService.register(blankUsername));

        RegisterRequest blankEmail = new RegisterRequest("Edmundus", "edmundus", "   ", "pass123");
        assertThrows(ResponseStatusException.class, () -> authService.register(blankEmail));

        RegisterRequest nullPassword = new RegisterRequest("Edmundus", "edmundus", "aji@mail.com", null);
        assertThrows(ResponseStatusException.class, () -> authService.register(nullPassword));
    }

    @Test
    void login_IncompleteData_ThrowsUnauthorized_AllBranches() {
        assertThrows(ResponseStatusException.class, () -> authService.login(null));

        LoginRequest blankUsername = new LoginRequest(" ", "pass123");
        assertThrows(ResponseStatusException.class, () -> authService.login(blankUsername));

        LoginRequest nullPassword = new LoginRequest("edmundus", null);
        assertThrows(ResponseStatusException.class, () -> authService.login(nullPassword));
    }
}