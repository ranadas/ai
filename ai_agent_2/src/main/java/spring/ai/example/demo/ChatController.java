package spring.ai.example.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Example 3 - Tool calling: the LLM decides when to query the vector store via {@link WineTool}.
 * Example 4 - Structured output: same flow but the response is mapped onto {@link WineDetails}.
 * Try: GET /ai/wine_explore?message=What%20wine%20do%20you%20suggest%20me?
 *      GET /ai/wine_explore/structured?message=Suggest%20two%20wines
 */
@RestController
class ChatController {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public ChatController(VectorStore vectorStore,
                          ChatClient.Builder chatClientBuilder) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(5)
                .build();
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.defaultAdvisors(
                MessageChatMemoryAdvisor
                        .builder(chatMemory)
                        .build()
        ).build();
    }

    @GetMapping("/ai/wine_explore")
    String generation(@RequestParam(value = "message",
                              defaultValue = "What wine do you suggest me?")
                      String userInput,
                      @RequestParam(value = "conversationId",
                              defaultValue = "001")
                      String conversationId) {
        return this.chatClient.prompt()
                .user(userInput)
                .advisors(a ->
                        a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(new WineTool(this.vectorStore))
                .call()
                .content();
    }

    @GetMapping("/ai/wine_explore/structured")
    WineDetails structuredGeneration(@RequestParam(value = "message",
                                             defaultValue = "Suggest two wines")
                                     String userInput,
                                     @RequestParam(value = "conversationId",
                                             defaultValue = "001")
                                     String conversationId) {
        return this.chatClient.prompt()
                .user(userInput)
                .advisors(a ->
                        a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(new WineTool(this.vectorStore))
                .call()
                .entity(WineDetails.class);
    }
}

/**
 * Example 4 - structured output target.
 */
record WineDetails(List<Map<String, String>> wines) {
}

/**
 * Example 3 - a tool the LLM can invoke to look up wines in the vector store.
 */
class WineTool {

    private final VectorStore vectorStore;

    WineTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(name = "WineQuery",
            description = "Get the wine related details. Takes query string as input.")
    public List<Document> wineQuery(
            @ToolParam(description = "wine related query string")
            String query) {
        return this.vectorStore
                .similaritySearch(
                        SearchRequest.builder()
                                .query(query)
                                .topK(3)
                                .build()
                );
    }
}
