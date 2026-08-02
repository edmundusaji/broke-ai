package org.edmund.brokeai.security;

import org.edmund.brokeai.entity.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurrentUserServiceTest {

    private CurrentUserService currentUserService;

    @BeforeEach
    void setUp() {
        currentUserService = new CurrentUserService();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_AuthenticatedWithAppUser_ReturnsUser() {
        AppUser mockUser = new AppUser();
        mockUser.setUsername("edmundus");
        mockUser.setId(99L);

        Authentication auth = new UsernamePasswordAuthenticationToken(mockUser, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        AppUser result = currentUserService.getCurrentUser();

        assertNotNull(result);
        assertEquals("edmundus", result.getUsername());
        assertEquals(99L, result.getId());
    }

    @Test
    void getCurrentUser_AuthenticationNull_ThrowsException() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> currentUserService.getCurrentUser());

        assertEquals("User is not authenticated", ex.getMessage());
    }

    @Test
    void getCurrentUser_PrincipalNotAppUser_ThrowsException() {
        Authentication auth = new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> currentUserService.getCurrentUser());

        assertEquals("User is not authenticated", ex.getMessage());
    }
}
