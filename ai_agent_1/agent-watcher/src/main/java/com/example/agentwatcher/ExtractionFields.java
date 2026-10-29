package com.example.agentwatcher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ExtractionFields {
    static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".tiff", ".tif"
    );

    static final String LEDGER_NAME = ".processed_ledger.json";
    static final int MAX_ATTEMPTS = 3;
    static final int RESCAN_INTERVAL_SECONDS = 60;

    static final List<String> FIELDS = List.of(
            "document_type",
            "issuing_country",
            "issuing_authority",
            "full_name",
            "surname",
            "given_names",
            "document_number",
            "personal_number",
            "nationality",
            "date_of_birth",
            "place_of_birth",
            "sex",
            "date_of_issue",
            "date_of_expiry",
            "address",
            "mrz_line_1",
            "mrz_line_2"
    );

    static final String SYSTEM_PROMPT = """
            You are a meticulous data-entry operator for identity documents. Transcribe ONLY text that is clearly legible in the image, exactly as printed (keep original date formats and spelling). If a field is missing, obscured, ambiguous, or you are not certain, return null for it. Never infer, normalise, translate, or guess any value. If the image is not an identity document, set is_identity_document to false and all other fields to null.
            """.strip();

    private ExtractionFields() {
    }

    static Map<String, Object> jsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("is_identity_document", Map.of(
                "type", "boolean",
                "description", "True only if the image clearly shows an identity document."
        ));
        for (String field : FIELDS) {
            properties.put(field, Map.of("type", List.of("string", "null")));
        }

        List<String> required = new java.util.ArrayList<>();
        required.add("is_identity_document");
        required.addAll(FIELDS);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }
}
