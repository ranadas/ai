package com.example.agentwatcher;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IdDocumentAgentTest {
    @Test
    void folderNameUsesFullNameWhenPresent() {
        assertThat(IdDocumentAgent.folderNameFrom(Map.of("full_name", "John Q. Doe")))
                .isEqualTo("JOHN_Q_DOE");
    }

    @Test
    void folderNameFallsBackToGivenNamesAndSurname() {
        assertThat(IdDocumentAgent.folderNameFrom(Map.of("given_names", "Jane Ann", "surname", "Doe")))
                .isEqualTo("JANE_ANN_DOE");
    }

    @Test
    void folderNameUsesUnknownWhenNoNameExists() {
        assertThat(IdDocumentAgent.folderNameFrom(Map.of()))
                .isEqualTo("UNKNOWN_NAME");
    }
}
