package com.example.agentwatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class DotenvLoader {
    private DotenvLoader() {
    }

    static Map<String, String> load(Path path) {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.isRegularFile(path)) {
            return values;
        }
        try {
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                parseLine(rawLine).ifPresent(entry -> values.put(entry.key(), entry.value()));
            }
        } catch (IOException ignored) {
            return values;
        }
        return values;
    }

    private static java.util.Optional<Entry> parseLine(String rawLine) {
        String line = rawLine.strip();
        if (line.isEmpty() || line.startsWith("#")) {
            return java.util.Optional.empty();
        }
        if (line.startsWith("export ")) {
            line = line.substring("export ".length()).stripLeading();
        }
        int separator = line.indexOf('=');
        if (separator <= 0) {
            return java.util.Optional.empty();
        }
        String key = line.substring(0, separator).strip();
        String value = line.substring(separator + 1).strip();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return java.util.Optional.of(new Entry(key, value));
    }

    private record Entry(String key, String value) {
    }
}
