package com.example.invoiceagent.domain;

public enum ProcessStatus {
    RECEIVED,
    EXTRACTED,
    REVIEWED,
    GENEVA_UPDATED,
    NAV_CONTROL_PACK_CREATED,
    CLIENT_RESPONDED,
    PAYMENT_CONFIRMED,
    PAYMENT_REJECTED,
    FAILED
}
