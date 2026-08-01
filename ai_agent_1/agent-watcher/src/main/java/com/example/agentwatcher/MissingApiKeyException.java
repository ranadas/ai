package com.example.agentwatcher;

import org.springframework.boot.ExitCodeGenerator;

final class MissingApiKeyException extends RuntimeException implements ExitCodeGenerator {
    MissingApiKeyException(String message) {
        super(message);
    }

    @Override
    public int getExitCode() {
        return 1;
    }
}
