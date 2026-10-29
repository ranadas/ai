package com.example.agentwatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

final class IdDocumentAgent {
    private static final Logger log = LoggerFactory.getLogger(IdDocumentAgent.class);
    private static final DateTimeFormatter CSV_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path inbox;
    private final Path output;
    private final Path rejected;
    private final OpenAiVisionClient openAiClient;
    private final Ledger ledger;
    private final Map<String, Integer> attempts = new HashMap<>();

    IdDocumentAgent(Path inbox, Path output, Path rejected, OpenAiVisionClient openAiClient, Ledger ledger) {
        this.inbox = inbox;
        this.output = output;
        this.rejected = rejected;
        this.openAiClient = openAiClient;
        this.ledger = ledger;
    }

    void process(Path filePath) {
        if (!isProcessableImage(filePath)) {
            return;
        }
        if (!waitUntilStable(filePath)) {
            log.warn("Skipping {}: never became stable/readable", filePath.getFileName());
            return;
        }

        String digest;
        try {
            digest = Ledger.digest(filePath);
        } catch (IOException exc) {
            log.error("Could not hash {}: {}", filePath.getFileName(), exc.getMessage());
            return;
        }

        if (ledger.seen(digest)) {
            log.info("Skipping {}: already processed (duplicate content)", filePath.getFileName());
            return;
        }
        if (attempts.getOrDefault(digest, 0) >= ExtractionFields.MAX_ATTEMPTS) {
            return;
        }
        int attempt = attempts.merge(digest, 1, Integer::sum);

        log.info("Processing {} (attempt {})", filePath.getFileName(), attempt);
        Map<String, Object> data;
        try {
            data = openAiClient.extract(filePath);
        } catch (Exception exc) {
            log.error("Extraction failed for {}: {}", filePath.getFileName(), exc.getMessage());
            return;
        }

        if (!Boolean.TRUE.equals(data.get("is_identity_document"))) {
            reject(filePath, digest);
            return;
        }

        try {
            Path personDir = output.resolve(folderNameFrom(data));
            Path csvPath = writeCsv(data, personDir, filePath);
            Files.move(filePath, uniquePath(personDir.resolve(filePath.getFileName())));
            ledger.record(digest, filePath, "ok", csvPath.toString());

            List<String> missing = ExtractionFields.FIELDS.stream()
                    .filter(field -> data.get(field) == null)
                    .toList();
            log.info("Saved {} ({} fields empty: {})", csvPath, missing.size(),
                    missing.isEmpty() ? "none" : String.join(", ", missing));
        } catch (IOException exc) {
            log.error("Failed to save output for {}: {}", filePath.getFileName(), exc.getMessage());
        }
    }

    void scanInbox() {
        try (Stream<Path> entries = Files.list(inbox)) {
            entries.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(this::process);
        } catch (IOException exc) {
            log.error("Could not scan inbox {}: {}", inbox, exc.getMessage());
        }
    }

    void seedExisting() {
        try (Stream<Path> entries = Files.list(inbox)) {
            entries.filter(path -> Files.isRegularFile(path) && isImageExtension(path) && !isHiddenName(path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(this::seedFile);
        } catch (IOException exc) {
            log.error("Could not seed inbox {}: {}", inbox, exc.getMessage());
        }
    }

    private void seedFile(Path entry) {
        try {
            String digest = Ledger.digest(entry);
            if (!ledger.seen(digest)) {
                ledger.record(digest, entry, "seeded", "present before startup");
                log.info("Seeded existing file {} (will not be sent)", entry.getFileName());
            }
        } catch (IOException exc) {
            log.error("Could not seed {}: {}", entry.getFileName(), exc.getMessage());
        }
    }

    private void reject(Path filePath, String digest) {
        log.warn("{} is not an identity document, moving to rejected/", filePath.getFileName());
        try {
            Files.createDirectories(rejected);
            Files.move(filePath, uniquePath(rejected.resolve(filePath.getFileName())));
            ledger.record(digest, filePath, "rejected", "not an identity document");
        } catch (IOException exc) {
            log.error("Failed to reject {}: {}", filePath.getFileName(), exc.getMessage());
        }
    }

    private static boolean waitUntilStable(Path filePath) {
        long last = -1;
        int stable = 0;
        for (int i = 0; i < 60; i++) {
            try {
                long size = Files.size(filePath);
                if (size == last && size > 0) {
                    stable++;
                    if (stable >= 2) {
                        return true;
                    }
                } else {
                    stable = 0;
                }
                last = size;
                Thread.sleep(1_000);
            } catch (IOException exc) {
                return false;
            } catch (InterruptedException exc) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean isProcessableImage(Path filePath) {
        return Files.isRegularFile(filePath) && isImageExtension(filePath) && !isHiddenName(filePath);
    }

    private static boolean isImageExtension(Path filePath) {
        String name = filePath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && ExtractionFields.IMAGE_EXTENSIONS.contains(name.substring(dot).toLowerCase(Locale.ROOT));
    }

    private static boolean isHiddenName(Path filePath) {
        return filePath.getFileName().toString().startsWith(".");
    }

    static String folderNameFrom(Map<String, Object> data) {
        String name = textValue(data.get("full_name"));
        if (name == null || name.isBlank()) {
            String givenNames = textValue(data.get("given_names"));
            String surname = textValue(data.get("surname"));
            name = Stream.of(givenNames, surname)
                    .filter(value -> value != null && !value.isBlank())
                    .reduce((left, right) -> left + " " + right)
                    .orElse("");
        }
        if (name.isBlank()) {
            return "UNKNOWN_NAME";
        }
        String cleaned = name.replaceAll("[^\\p{L}\\p{N}_ \\-']", "").strip();
        String folder = cleaned.replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
        return folder.isBlank() ? "UNKNOWN_NAME" : folder;
    }

    private static Path writeCsv(Map<String, Object> data, Path personDir, Path source) throws IOException {
        Files.createDirectories(personDir);
        String stamp = LocalDateTime.now().format(CSV_TIMESTAMP);
        Path csvPath = uniquePath(personDir.resolve(stem(source.getFileName().toString()) + "_" + stamp + ".csv"));

        List<String> header = new ArrayList<>();
        header.add("source_file");
        header.addAll(ExtractionFields.FIELDS);

        List<String> values = new ArrayList<>();
        values.add(source.getFileName().toString());
        for (String field : ExtractionFields.FIELDS) {
            String value = textValue(data.get(field));
            values.add(value == null ? "" : value);
        }

        String content = csvRow(header) + System.lineSeparator()
                + csvRow(values) + System.lineSeparator();
        Files.writeString(csvPath, content, StandardCharsets.UTF_8);
        return csvPath;
    }

    private static String csvRow(List<String> values) {
        return values.stream()
                .map(IdDocumentAgent::csvValue)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String csvValue(String value) {
        if (value.contains("\"") || value.contains(",") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String stem(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String textValue(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private static Path uniquePath(Path desired) {
        if (!Files.exists(desired)) {
            return desired;
        }
        String filename = desired.getFileName().toString();
        String base = stem(filename);
        String extension = "";
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            base = filename.substring(0, dot);
            extension = filename.substring(dot);
        }
        Path parent = desired.getParent();
        for (int i = 1; ; i++) {
            Path candidate = parent.resolve(base + "_" + i + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
    }
}
