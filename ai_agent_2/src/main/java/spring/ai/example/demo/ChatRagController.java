package spring.ai.example.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Example 5 - Retrieval Augmented Generation (RAG) using the QuestionAnswerAdvisor,
 * which retrieves relevant documents from the vector store and injects them as context.
 * Try: GET /ai/rag?message=What%20is%20wine?
 */
@RestController
class ChatRagController {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public ChatRagController(VectorStore vectorStore,
                             ChatClient.Builder chatClientBuilder) {
        ChatMemory chatMemory = MessageWindowChatMemory
                .builder().maxMessages(5)
                .build();
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .build())
                .build();
    }

    @GetMapping("/ai/rag")
    String generation(@RequestParam(value = "message",
                              defaultValue = "What is wine?")
                      String userInput,
                      @RequestParam(value = "conversationId",
                              defaultValue = "001")
                      String conversationId) {
        return this.chatClient.prompt("Please use advisors for " +
                        "answering wine related queries.")
                .user(userInput)
                .advisors(QuestionAnswerAdvisor
                        .builder(vectorStore)
                        .build())
                .advisors(a ->
                        a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
