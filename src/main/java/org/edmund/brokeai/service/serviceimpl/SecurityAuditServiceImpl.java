package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.SecurityAuditLog;
import org.edmund.brokeai.repository.SecurityAuditLogRepository;
import org.edmund.brokeai.service.SecurityAuditService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityAuditServiceImpl implements SecurityAuditService {
    private final SecurityAuditLogRepository repository;

    @Override
    public void record(AppUser user, String eventType, UUID sessionId, Map<String, Object> metadata) {
        SecurityAuditLog audit = new SecurityAuditLog();
        audit.setUser(user);
        audit.setEventType(eventType);
        audit.setSessionId(sessionId);
        audit.setMetadata(metadata == null ? Map.of() : Map.copyOf(metadata));
        repository.save(audit);
    }
}
