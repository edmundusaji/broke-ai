package org.edmund.brokeai.security;

import org.edmund.brokeai.entity.AppUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {

    public AppUser getCurrentUser() {
        return getCurrentUserIfAuthenticated()
            .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
    }

    public Optional<AppUser> getCurrentUserIfAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }
}
