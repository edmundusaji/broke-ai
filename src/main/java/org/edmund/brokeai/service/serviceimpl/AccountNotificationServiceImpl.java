package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.security.SensitiveValueCipher;
import org.edmund.brokeai.service.AccountNotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountNotificationServiceImpl implements AccountNotificationService {
    private final JdbcTemplate jdbcTemplate;
    private final SensitiveValueCipher sensitiveValueCipher;

    @Override
    public void queueEmail(AppUser user, String recipientEmail, String messageType, String payload) {
        if (recipientEmail == null || recipientEmail.isBlank()) return;
        jdbcTemplate.update(
            "INSERT INTO outbound_email_jobs " +
                "(user_id, message_type, recipient_email, encrypted_payload) VALUES (?, ?, ?, ?)",
            user.getId(), messageType, recipientEmail, sensitiveValueCipher.encrypt(payload)
        );
    }
}
