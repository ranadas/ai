# Spring AI Demo — Agentic AI through Spring AI (Maven)

A Maven port of the examples from
[*Agentic AI through Spring AI — basic examples*](https://medium.com/@amrit08ju/agentic-ai-through-spring-ai-basic-examples-c2b70bd89347).
The article uses Gradle; this project provides the same code as a Maven build.

- **Spring Boot** 3.4.6, **Java** 21
- **Spring AI** (OpenAI model, vector-store advisors, Chroma vector store, MCP client)

## Prerequisites

- JDK 21
- Maven 3.9+
- An OpenAI API key
- (For the vector-store / RAG / tool / moderation examples) a running **ChromaDB** on `localhost:8000`.
  The `WineReviews` collection is created on startup and can be populated with the bundled loader
  (see [Load sample wine data](#load-sample-wine-data-into-chroma)).

  ```bash
  docker run -p 8000:8000 chromadb/chroma
  ```
- (For the MCP example) an MCP server on `localhost:8001`.

## Configure

```bash
export OPENAI_API_KEY=sk-...
```

Connection details live in `src/main/resources/application.properties`.

## Run

```bash
mvn spring-boot:run
```

Swagger UI: http://localhost:8080/swagger-ui.html
Metrics:    http://localhost:8080/actuator/metrics

## Load sample wine data into Chroma

The article never shows how `WineReviews` gets populated, so this project ships a loader and a
sample dataset (`src/main/resources/data/wine-reviews.json`, 14 wines incl. **Grizzly Peak** so the
metadata-filter example works).

With Chroma running and the app started, load on demand:

```bash
curl -X POST http://localhost:8080/ai/chroma/load
# {"status":"ok","loaded":14}
```

…or load automatically on startup by setting `app.wine.load-on-startup=true` in
`application.properties`. `spring.ai.vectorstore.chroma.initialize-schema=true` (already set) makes
Spring AI create the collection on boot. Embeddings are produced by the configured OpenAI embedding
model, so a valid `OPENAI_API_KEY` is required to load. To add your own wines, edit the JSON file.

## Endpoints (one per example in the article)

| # | Example | Endpoint |
|---|---------|----------|
| — | Load sample wine data into Chroma     | `POST /ai/chroma/load` |
| 1 | Basic chat + conversation memory      | `GET /ai/chat?message=Hi` |
| 2 | Vector similarity search (Chroma)     | `GET /ai/chroma?message=tasty%20wine` |
| 2 | Vector search with metadata filter    | `GET /ai/chroma/meta?search_query=tasty%20wine&meta_query=Grizzly%20Peak` |
| 3 | Tool calling                          | `GET /ai/wine_explore?message=Suggest%20a%20wine` |
| 4 | Structured output                     | `GET /ai/wine_explore/structured?message=Suggest%20two%20wines` |
| 5 | RAG (QuestionAnswerAdvisor)           | `GET /ai/rag?message=What%20is%20wine?` |
| 6 | Guardrail (SafeGuardAdvisor)          | `GET /ai/guardrail?message=What%20is%20wine?` |
| 7+8 | Prompt-chaining workflow            | `GET /ai/chain?message=What%20is%20wine?` |
| 9 | MCP client (reactive)                 | `GET /ai/mcp/wine_explore?message=Suggest%20a%20wine` |
| 10 | Input/output moderation              | `GET /ai/moderation?message=What%20is%20wine?` |

## Notes

- The **MCP** controller (`ChatMcpController`) is only loaded when
  `spring.ai.mcp.client.enabled=true`. It is disabled by default so the app boots without an
  MCP server. Enable it in `application.properties` and start your MCP server on `:8001`.
- Examples 2–6, 9 and 10 need a populated Chroma vector store — run `POST /ai/chroma/load`
  (or set `app.wine.load-on-startup=true`) once Chroma is up. Without data they return empty results.
- `spring-ai.version` is set to `1.1.0` in `pom.xml`. If you need the snapshot the article used,
  change it to `1.1.0-SNAPSHOT` — the Spring snapshot repository is already declared.
- Alternative providers (Ollama Cloud, Sarvam) are shown commented-out in `application.properties`.
