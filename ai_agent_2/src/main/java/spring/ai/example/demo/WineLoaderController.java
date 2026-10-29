package spring.ai.example.demo;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Triggers loading of the sample wine reviews into the Chroma vector store.
 * Try: POST /ai/chroma/load
 */
@RestController
class WineLoaderController {

    private final WineDataLoader loader;

    WineLoaderController(WineDataLoader loader) {
        this.loader = loader;
    }

    @PostMapping("/ai/chroma/load")
    Map<String, Object> load() {
        int count = loader.load();
        return Map.of("status", "ok", "loaded", count);
    }
}
