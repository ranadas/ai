package com.example.invoiceagent.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "invoice_process")
@Getter
@Setter
public class InvoiceProcess {

    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(nullable = false)
    private String clientId;

    private String invoiceReference;
    private String originalFilename;
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessStatus status;

    private String invoiceNumber;
    private String supplierName;
    private String currency;
    private BigDecimal amount;
    private LocalDate dueDate;

    @Column(length = 4000)
    private String message;

    @Lob
    private String extractedJson;

    @Lob
    private String reviewJson;

    private String genevaCorrelationId;
    private String navControlPackLocation;
    private String clientCallbackStatus;

    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = ProcessStatus.RECEIVED;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
