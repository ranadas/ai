package com.agent.liquidalts.invoiceagent.rules;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Vendor master lookup — in production this fronts the vendor master service. */
@Component
public class VendorMasterRepository {

    private static final List<Vendor> VENDORS = List.of(
            new Vendor("V-1001", "Acme Fund Services Ltd", "IE29AIBK93115212345678", true),
            new Vendor("V-1002", "Dublin Data Systems", "IE64BOFI90583812345678", true),
            new Vendor("V-1003", "Meridian Consulting GmbH", "DE89370400440532013000", false)
    );

    public Optional<Vendor> findByNameOrId(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        return VENDORS.stream()
                .filter(v -> v.vendorId().toLowerCase(Locale.ROOT).equals(q)
                        || v.name().toLowerCase(Locale.ROOT).contains(q))
                .findFirst();
    }
}
