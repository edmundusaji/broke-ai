package org.edmund.brokeai.service;

import org.edmund.brokeai.entity.AppUser;

import java.util.Map;
import java.util.UUID;

public interface SecurityAuditService {
    void record(AppUser user, String eventType, UUID sessionId, Map<String, Object> metadata);
}
