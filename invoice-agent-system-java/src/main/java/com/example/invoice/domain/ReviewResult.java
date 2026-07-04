package com.example.invoice.domain;

import java.util.List;

public record ReviewResult(ReviewDecision decision, int reviewersRequired, int reviewersCaptured, List<String> comments) {
    public ReviewResult {
        comments = comments == null ? List.of() : List.copyOf(comments);
    }
}
