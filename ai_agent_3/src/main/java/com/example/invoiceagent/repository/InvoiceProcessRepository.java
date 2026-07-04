package com.example.invoiceagent.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.invoiceagent.domain.InvoiceProcess;

public interface InvoiceProcessRepository extends JpaRepository<InvoiceProcess, UUID> {
}
