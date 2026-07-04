package com.agent.liquidalts.invoiceagent.rules;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** PO / GR lookup — in production this fronts the procurement service. */
@Component
public class PurchaseOrderRepository {

    private static final List<PurchaseOrder> ORDERS = List.of(
            new PurchaseOrder("PO-88801", "V-1001", new BigDecimal("12500.00"), "EUR", true),
            new PurchaseOrder("PO-88802", "V-1002", new BigDecimal("4300.00"), "EUR", true)
    );

    public Optional<PurchaseOrder> findByNumber(String poNumber) {
        return ORDERS.stream()
                .filter(po -> po.poNumber().equalsIgnoreCase(poNumber.trim()))
                .findFirst();
    }
}
