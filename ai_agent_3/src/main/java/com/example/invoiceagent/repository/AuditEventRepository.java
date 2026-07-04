package com.example.invoiceagent.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.invoiceagent.domain.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByProcessIdOrderByCreatedAtAsc(UUID processId);
}
