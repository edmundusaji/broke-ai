package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
    Optional<SupportTicket> findByIdAndUserId(UUID id, Long userId);
}
