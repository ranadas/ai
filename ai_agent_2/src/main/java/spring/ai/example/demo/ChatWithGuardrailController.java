package spring.ai.example.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Example 6 - Guardrails: SafeGuardAdvisor blocks prompts containing sensitive words
 * before they reach the model.
 * Try: GET /ai/guardrail?message=What%20is%20wine?   (blocked, "wine" is sensitive here)
 */
@RestController
class ChatWithGuardrailController {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public ChatWithGuardrailController(VectorStore vectorStore,
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

    @GetMapping("/ai/guardrail")
    String generation(@RequestParam(value = "message",
                              defaultValue = "What is wine?")
                      String userInput,
                      @RequestParam(value = "conversationId",
                              defaultValue = "001")
                      String conversationId) {
        Advisor safeguardAdvisor = SafeGuardAdvisor.builder()
                .sensitiveWords(List.of(
                        "wine"
                ))
                .build();

        return this.chatClient.prompt("Please use advisors for " +
                        "answering wine related queries.")
                .user(userInput)
                .advisors(safeguardAdvisor)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .build())
                .advisors(a ->
                        a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
