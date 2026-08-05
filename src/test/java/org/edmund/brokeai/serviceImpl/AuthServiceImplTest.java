package org.edmund.brokeai.serviceImpl;

import org.edmund.brokeai.dto.LoginRequest;
import org.edmund.brokeai.dto.LoginResponse;
import org.edmund.brokeai.dto.RegisterRequest;
import org.edmund.brokeai.dto.UpgradeGuestRequest;
import org.edmund.brokeai.dto.CurrentUserResponse;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.security.JwtService;
import org.edmund.brokeai.security.CurrentUserService;
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

    @Mock
    private CurrentUserService currentUserService;

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
                user.getFullName().equals("Edmundus Aji") &&
                        user.getUsername().equals("edmundus") &&
                        user.getEmail().equals("aji@mail.com") &&
                        user.getPassword().equals("encodedPassword") &&
                        user.getAiTrialCount() == 0
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
        assertTrue(exception.getReason().contains("Registration data is incomplete"));
    }

    @Test
    void register_UsernameExists_ThrowsBadRequest() {
        RegisterRequest request = new RegisterRequest("Aji", "edmundus", "aji@mail.com", "pass");
        when(userRepository.existsByUsername("edmundus")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.register(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Username is already in use"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_EmailExists_ThrowsBadRequest() {
        RegisterRequest request = new RegisterRequest("Aji", "edmundus", "aji@mail.com", "pass");
        when(userRepository.existsByUsername("edmundus")).thenReturn(false);
        when(userRepository.existsByEmail("aji@mail.com")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.register(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Email is already in use"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_Success_ReturnsToken() {
        LoginRequest request = new LoginRequest(" edmundus ", "password123");
        AppUser mockUser = new AppUser();
        mockUser.setFullName("Edmundus Aji");
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
        assertFalse(response.isGuest());
        assertEquals("Edmundus Aji", response.user().fullName());
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

    @Test
    void login_GuestAccount_ThrowsUnauthorized() {
        AppUser guest = new AppUser();
        guest.setIsGuest(true);
        when(userRepository.findByUsername("guest_123")).thenReturn(Optional.of(guest));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> authService.login(new LoginRequest("guest_123", "password"))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(passwordEncoder, never()).matches(anyString(), any());
    }

    @Test
    void guestLogin_CreatesAnonymousUserAndReturnsSession() {
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser guest = invocation.getArgument(0);
            guest.setId(42L);
            return guest;
        });
        when(jwtService.generateToken(any(AppUser.class))).thenReturn("guest-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.guestLogin();

        assertEquals("guest-token", response.token());
        assertTrue(response.isGuest());
        assertEquals(2, response.remainingAiTrials());
        assertTrue(response.username().startsWith("guest_"));
        assertEquals("Guest User", response.user().fullName());
        assertNull(response.user().email());
        verify(userRepository).save(argThat(user ->
            Boolean.TRUE.equals(user.getIsGuest())
                && user.getEmail() == null
                && user.getPassword() == null
        ));
    }

    @Test
    void register_AuthenticatedGuest_UpdatesSameUserAndPreservesIdentity() {
        RegisterRequest request = new RegisterRequest(" Edmundus Aji ", " edmundus ", " AJI@mail.com ", "password123");
        AppUser guest = new AppUser();
        guest.setId(42L);
        guest.setFullName("Guest User");
        guest.setUsername("guest_123");
        guest.setIsGuest(true);
        guest.setAiTrialCount(1);

        when(currentUserService.getCurrentUserIfAuthenticated()).thenReturn(Optional.of(guest));
        when(userRepository.existsByUsernameAndIdNot("edmundus", 42L)).thenReturn(false);
        when(userRepository.existsByEmailAndIdNot("aji@mail.com", 42L)).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");

        authService.register(request);

        assertEquals(42L, guest.getId());
        assertEquals("Edmundus Aji", guest.getFullName());
        assertEquals("edmundus", guest.getUsername());
        assertEquals("aji@mail.com", guest.getEmail());
        assertEquals("encodedPassword", guest.getPassword());
        assertFalse(guest.getIsGuest());
        assertEquals(0, guest.getAiTrialCount());
        verify(userRepository).save(guest);
        verify(userRepository, never()).existsByUsername(anyString());
        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    void register_AuthenticatedGuestWithConflictingUsername_ThrowsBadRequest() {
        AppUser guest = guestUser();
        when(currentUserService.getCurrentUserIfAuthenticated()).thenReturn(Optional.of(guest));
        when(userRepository.existsByUsernameAndIdNot("edmundus", 42L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(new RegisterRequest("Aji", "edmundus", "aji@mail.com", "password123"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Username is already in use", exception.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_AuthenticatedGuestWithConflictingEmail_ThrowsBadRequest() {
        AppUser guest = guestUser();
        when(currentUserService.getCurrentUserIfAuthenticated()).thenReturn(Optional.of(guest));
        when(userRepository.existsByUsernameAndIdNot("edmundus", 42L)).thenReturn(false);
        when(userRepository.existsByEmailAndIdNot("aji@mail.com", 42L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(new RegisterRequest("Aji", "edmundus", "aji@mail.com", "password123"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Email is already in use", exception.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_AuthenticatedNonGuest_CreatesNewUser() {
        AppUser currentUser = new AppUser();
        currentUser.setId(7L);
        currentUser.setIsGuest(false);
        when(currentUserService.getCurrentUserIfAuthenticated()).thenReturn(Optional.of(currentUser));
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");

        authService.register(new RegisterRequest("Aji", "edmundus", "aji@mail.com", "password123"));

        verify(userRepository).save(argThat(saved -> saved != currentUser && saved.getId() == null));
    }

    @Test
    void upgradeGuest_ValidRequest_UpdatesSameUserAndPreservesIdentity() {
        AppUser guest = new AppUser();
        guest.setId(42L);
        guest.setUsername("guest_123");
        guest.setIsGuest(true);
        when(currentUserService.getCurrentUser()).thenReturn(guest);
        when(userRepository.existsByEmail("aji@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(guest)).thenReturn(guest);
        when(jwtService.generateToken(guest)).thenReturn("upgraded-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.upgradeGuest(
            new UpgradeGuestRequest(" Edmundus Aji ", " AJI@mail.com ", "password123")
        );

        assertEquals(42L, guest.getId());
        assertEquals("guest_123", guest.getUsername());
        assertEquals("Edmundus Aji", guest.getFullName());
        assertEquals("aji@mail.com", guest.getEmail());
        assertEquals("encoded-password", guest.getPassword());
        assertFalse(guest.getIsGuest());
        assertEquals(0, guest.getAiTrialCount());
        assertFalse(response.isGuest());
        assertEquals(0, response.remainingAiTrials());
        assertEquals("upgraded-token", response.token());
        verify(userRepository).save(guest);
    }

    @Test
    void upgradeGuest_NonGuestUser_ThrowsBadRequest() {
        AppUser user = new AppUser();
        user.setIsGuest(false);
        when(currentUserService.getCurrentUser()).thenReturn(user);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> authService.upgradeGuest(validUpgradeRequest())
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Current user is not a guest", exception.getReason());
    }

    @Test
    void upgradeGuest_EmailAlreadyUsed_ThrowsBadRequest() {
        AppUser guest = new AppUser();
        guest.setIsGuest(true);
        when(currentUserService.getCurrentUser()).thenReturn(guest);
        when(userRepository.existsByEmail("aji@mail.com")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> authService.upgradeGuest(validUpgradeRequest())
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Email is already in use", exception.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    void upgradeGuest_IncompletePayload_ThrowsBadRequestForAllBranches() {
        assertInvalidUpgrade(null);
        assertInvalidUpgrade(new UpgradeGuestRequest(" ", "aji@mail.com", "password123"));
        assertInvalidUpgrade(new UpgradeGuestRequest("Aji", null, "password123"));
        assertInvalidUpgrade(new UpgradeGuestRequest("Aji", "aji@mail.com", " "));
    }

    @Test
    void getCurrentUser_Guest_ReturnsRoleAndRemainingTrials() {
        AppUser guest = new AppUser();
        guest.setUsername("guest_123");
        guest.setFullName("Guest User");
        guest.setIsGuest(true);
        guest.setAiTrialCount(1);
        when(currentUserService.getCurrentUser()).thenReturn(guest);

        CurrentUserResponse response = authService.getCurrentUser();

        assertEquals("ROLE_GUEST", response.role());
        assertEquals(1, response.remainingAiTrials());
    }

    @Test
    void getCurrentUser_RegisteredUser_ReturnsUserRoleAndNoTrialQuota() {
        AppUser user = new AppUser();
        user.setUsername("edmundus");
        user.setFullName("Edmundus");
        user.setIsGuest(false);
        user.setAiTrialCount(null);
        when(currentUserService.getCurrentUser()).thenReturn(user);

        CurrentUserResponse response = authService.getCurrentUser();

        assertEquals("ROLE_USER", response.role());
        assertEquals(0, response.remainingAiTrials());
    }

    @Test
    void getCurrentUser_GuestWithNullTrialCount_ReturnsZero() {
        AppUser guest = new AppUser();
        guest.setIsGuest(true);
        guest.setAiTrialCount(null);
        when(currentUserService.getCurrentUser()).thenReturn(guest);

        CurrentUserResponse response = authService.getCurrentUser();

        assertEquals(0, response.remainingAiTrials());
    }

    private UpgradeGuestRequest validUpgradeRequest() {
        return new UpgradeGuestRequest("Aji", "aji@mail.com", "password123");
    }

    private AppUser guestUser() {
        AppUser guest = new AppUser();
        guest.setId(42L);
        guest.setIsGuest(true);
        return guest;
    }

    private void assertInvalidUpgrade(UpgradeGuestRequest request) {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> authService.upgradeGuest(request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Guest upgrade data is incomplete", exception.getReason());
    }
}
