package com.example.agentwatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

@SpringBootApplication
public class AgentWatcherApplication implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AgentWatcherApplication.class);

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public AgentWatcherApplication(Environment environment, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    public static void main(String[] args) {
        SpringApplication.run(AgentWatcherApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Map<String, String> dotenv = DotenvLoader.load(Path.of(".env"));
        AgentOptions options = AgentOptions.from(args, environment, dotenv);

        if (options.apiKey().isBlank() || options.apiKey().contains("REPLACE_ME")) {
            throw new MissingApiKeyException("OPENAI_API_KEY is not set. Put it in .env or export it in the environment.");
        }

        Files.createDirectories(options.inbox());
        Files.createDirectories(options.output());

        Ledger ledger = new Ledger(options.output().resolve(ExtractionFields.LEDGER_NAME), objectMapper);
        OpenAiVisionClient openAiClient = new OpenAiVisionClient(
                options.apiKey(),
                options.model(),
                restClientBuilder,
                objectMapper
        );
        IdDocumentAgent agent = new IdDocumentAgent(
                options.inbox(),
                options.output(),
                options.rejected(),
                openAiClient,
                ledger
        );

        if (options.skipExisting()) {
            agent.seedExisting();
        } else {
            agent.scanInbox();
        }

        log.info("Watching {} -> {} (model: {}). Ctrl-C to stop.", options.inbox(), options.output(), options.model());
        new InboxWatcher(
                options.inbox(),
                agent,
                Duration.ofSeconds(ExtractionFields.RESCAN_INTERVAL_SECONDS)
        ).watch();
    }
}
