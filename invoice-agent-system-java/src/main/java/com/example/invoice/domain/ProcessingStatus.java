package com.example.invoice.domain;

public enum ProcessingStatus {
    RECEIVED,
    EXTRACTED,
    VALIDATED,
    REVIEW_REQUIRED,
    POSTED,
    CONTROL_PACK_CREATED,
    CLIENT_NOTIFIED,
    FAILED
}
