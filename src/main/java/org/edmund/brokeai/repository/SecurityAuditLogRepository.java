package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.SecurityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, UUID> {
}
