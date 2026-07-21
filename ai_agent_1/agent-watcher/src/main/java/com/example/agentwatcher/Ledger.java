package com.example.agentwatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

final class Ledger {
    private static final Logger log = LoggerFactory.getLogger(Ledger.class);
    private static final int BUFFER_SIZE = 1 << 20;

    private final Path path;
    private final ObjectMapper objectMapper;
    private final Map<String, LedgerEntry> entries = new LinkedHashMap<>();

    Ledger(Path path, ObjectMapper objectMapper) {
        this.path = path;
        this.objectMapper = objectMapper;
        load();
    }

    static String digest(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream input = Files.newInputStream(filePath)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exc) {
            throw new IllegalStateException("SHA-256 is not available", exc);
        }
    }

    synchronized boolean seen(String digest) {
        return entries.containsKey(digest);
    }

    synchronized void record(String digest, Path filePath, String status, String detail) throws IOException {
        entries.put(digest, new LedgerEntry(
                filePath.getFileName().toString(),
                status,
                detail,
                OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"))
        ));
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(".processed_ledger.tmp");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), entries);
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exc) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void load() {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            Map<String, LedgerEntry> loaded = objectMapper.readValue(
                    path.toFile(),
                    new TypeReference<LinkedHashMap<String, LedgerEntry>>() {
                    }
            );
            entries.putAll(loaded);
        } catch (RuntimeException exc) {
            log.warn("Ledger {} unreadable, starting fresh", path);
        }
    }

    record LedgerEntry(String file, String status, String detail, String processed_at) {
    }
}
