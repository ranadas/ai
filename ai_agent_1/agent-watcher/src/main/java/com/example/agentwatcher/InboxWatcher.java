package com.example.agentwatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

final class InboxWatcher {
    private static final Logger log = LoggerFactory.getLogger(InboxWatcher.class);

    private final Path inbox;
    private final IdDocumentAgent agent;
    private final Duration rescanInterval;

    InboxWatcher(Path inbox, IdDocumentAgent agent, Duration rescanInterval) {
        this.inbox = inbox;
        this.agent = agent;
        this.rescanInterval = rescanInterval;
    }

    void watch() throws IOException, InterruptedException {
        try (WatchService watchService = inbox.getFileSystem().newWatchService()) {
            inbox.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY
            );
            Instant nextRescan = Instant.now().plus(rescanInterval);
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key != null) {
                    handleKey(key);
                }
                if (!Instant.now().isBefore(nextRescan)) {
                    agent.scanInbox();
                    nextRescan = Instant.now().plus(rescanInterval);
                }
            }
        } catch (ClosedWatchServiceException ignored) {
            log.info("Watch service closed.");
        }
    }

    @SuppressWarnings("unchecked")
    private void handleKey(WatchKey key) {
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                agent.scanInbox();
                continue;
            }
            WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
            agent.process(inbox.resolve(pathEvent.context()));
        }
        if (!key.reset()) {
            log.warn("Inbox watch key is no longer valid.");
        }
    }
}
