package com.example.visionagent.extraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Sends one or more images to a vision-capable LLM and maps the response into a
 * structured {@link ExtractionResult}.
 *
 * <p>This class depends only on Spring AI's provider-neutral {@link ChatClient}.
 * The concrete model (Ollama today, OpenAI in the future) is selected entirely
 * through dependencies and configuration, so this code never changes.
 */
@Service
public class VisionExtractionService {

    private static final Logger log = LoggerFactory.getLogger(VisionExtractionService.class);

    private static final String DEFAULT_PROMPT = """
            You are a meticulous data-extraction agent.
            Examine the attached image(s) and extract every data point you can identify.
            The images may be consecutive pages of a single document; treat them as one document.
            For each data point provide a clear label, its value as plain text, and a
            confidence score between 0.0 and 1.0.
            Also classify the document type and write a one-sentence summary.
            Only report what is actually visible in the image(s); never invent values.
            """;

    private final ChatClient chatClient;

    public VisionExtractionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Extract data points from one or more page images.
     *
     * @param images       the page image(s) to analyse (e.g. a single photo or the rendered pages of a PDF)
     * @param instructions optional extra, task-specific instructions (may be null/blank)
     */
    public ExtractionResult extract(List<Media> images, String instructions) {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("At least one image is required for extraction.");
        }

        String prompt = (instructions == null || instructions.isBlank())
                ? DEFAULT_PROMPT
                : DEFAULT_PROMPT + "\n\nAdditional instructions:\n" + instructions;

        log.debug("Extracting data points from {} image(s)", images.size());

        ExtractionResult result = chatClient.prompt()
                .user(u -> u.text(prompt).media(images.toArray(Media[]::new)))
                .call()
                .entity(ExtractionResult.class);

        return Objects.requireNonNullElseGet(result,
                () -> new ExtractionResult("No result produced by the model.", "unknown", List.of()));
    }
}
