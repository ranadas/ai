package spring.ai.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads sample wine reviews from {@code data/wine-reviews.json} into the Chroma
 * vector store (the {@code WineReviews} collection). Each review is stored as a
 * {@link Document} whose text is the tasting note (what gets embedded) and whose
 * metadata carries {@code title}, {@code variety}, {@code country}, etc. — the
 * {@code title} field is what {@code /ai/chroma/meta} filters on.
 *
 * <p>Trigger it either way:
 * <ul>
 *   <li>On startup — set {@code app.wine.load-on-startup=true}</li>
 *   <li>On demand — {@code POST /ai/chroma/load}</li>
 * </ul>
 *
 * <p>Requires {@code spring.ai.vectorstore.chroma.initialize-schema=true} so the
 * collection exists before documents are written.
 */
@Component
class WineDataLoader {

    private static final Logger logger = LoggerFactory.getLogger(WineDataLoader.class);
    private static final String DATA_FILE = "data/wine-reviews.json";

    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    WineDataLoader(VectorStore vectorStore, ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    /**
     * Reads the JSON dataset, builds one {@link Document} per wine and writes them
     * to the vector store (embeddings are computed by the configured embedding model).
     *
     * @return the number of documents loaded
     */
    int load() {
        List<WineReview> reviews = readReviews();
        List<Document> documents = new ArrayList<>(reviews.size());

        for (WineReview r : reviews) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("title", r.title());
            metadata.put("variety", r.variety());
            metadata.put("country", r.country());
            metadata.put("province", r.province());
            metadata.put("winery", r.winery());
            metadata.put("points", r.points());
            metadata.put("price", r.price());

            String text = "%s (%s, %s) — %s".formatted(
                    r.title(), r.variety(), r.country(), r.description());

            documents.add(Document.builder()
                    .text(text)
                    .metadata(metadata)
                    .build());
        }

        this.vectorStore.add(documents);
        logger.info("Loaded {} wine reviews into the vector store", documents.size());
        return documents.size();
    }

    private List<WineReview> readReviews() {
        try (InputStream is = new ClassPathResource(DATA_FILE).getInputStream()) {
            WineReview[] reviews = objectMapper.readValue(is, WineReview[].class);
            return List.of(reviews);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + DATA_FILE, e);
        }
    }

    /**
     * Optional startup loader. Enabled with {@code app.wine.load-on-startup=true}.
     */
    @Component
    @ConditionalOnProperty(name = "app.wine.load-on-startup", havingValue = "true")
    static class StartupLoader implements ApplicationRunner {

        private final WineDataLoader loader;

        StartupLoader(WineDataLoader loader) {
            this.loader = loader;
        }

        @Override
        public void run(ApplicationArguments args) {
            loader.load();
        }
    }
}

/**
 * Shape of one entry in {@code data/wine-reviews.json}.
 */
record WineReview(
        String title,
        String variety,
        String country,
        String province,
        String winery,
        int points,
        double price,
        String description) {
}
