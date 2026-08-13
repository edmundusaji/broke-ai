package org.edmund.brokeai.service.serviceimpl;

import org.edmund.brokeai.exception.ApiException;
import org.edmund.brokeai.service.PasswordPolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class PasswordPolicyServiceImpl implements PasswordPolicyService {

    private static final Set<String> BLOCKLIST = Set.of(
        "password",
        "password123",
        "123456789",
        "qwertyuiop",
        "letmein",
        "adminadmin",
        "brokebroke",
        "iloveyou"
    );

    @Override
    public void validate(String password) {
        String normalized = password == null ? "" : password.toLowerCase(Locale.ROOT);
        if (password == null || password.length() < 15 || password.length() > 128) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Password must contain between 15 and 128 characters.",
                "newPassword"
            );
        }
        if (BLOCKLIST.contains(normalized)) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PASSWORD_COMPROMISED",
                "Choose a password that is not commonly used or compromised.",
                "newPassword"
            );
        }
    }
}
