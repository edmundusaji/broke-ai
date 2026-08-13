package org.edmund.brokeai.service;

import org.edmund.brokeai.entity.AppUser;

public interface AccountNotificationService {
    void queueEmail(AppUser user, String recipientEmail, String messageType, String payload);
}
