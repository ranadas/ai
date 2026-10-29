package com.example.invoice.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record GenevaPostResult(boolean posted, String downstreamReference, String message, Map<String, Object> requestPayload) {
    public GenevaPostResult {
        requestPayload = requestPayload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(requestPayload));
    }
}
