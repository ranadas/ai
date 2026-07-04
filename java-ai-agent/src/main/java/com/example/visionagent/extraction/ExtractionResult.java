package com.example.visionagent.extraction;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Structured result the LLM is asked to populate. Spring AI generates a JSON
 * schema from this record and instructs the model to respond accordingly, then
 * deserializes the model's response back into this type.
 */
@JsonClassDescription("All data points extracted from the supplied image.")
public record ExtractionResult(

        @JsonPropertyDescription("A short human-readable summary of what the image shows.")
        String summary,

        @JsonPropertyDescription("The kind of document or image, e.g. 'invoice', 'receipt', 'form', 'chart', 'photo'.")
        String documentType,

        @JsonPropertyDescription("Every discrete data point found in the image.")
        List<DataPoint> dataPoints
) {

    @JsonClassDescription("A single labelled value extracted from the image.")
    public record DataPoint(

            @JsonPropertyDescription("The label or field name, e.g. 'Total', 'Invoice Number', 'Date'.")
            String label,

            @JsonPropertyDescription("The value associated with the label, as plain text.")
            String value,

            @JsonPropertyDescription("Model confidence from 0.0 to 1.0 for this data point.")
            Double confidence
    ) {
    }
}
