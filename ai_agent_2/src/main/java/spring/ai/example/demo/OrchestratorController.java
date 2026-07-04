package spring.ai.example.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Example 8 - Orchestration: combines conversation memory and a guardrail on a ChatClient
 * that drives the multi-step {@link RecurrenceWorkflow}.
 * Try: GET /ai/chain?message=What%20is%20wine?
 */
@RestController
public class OrchestratorController {

    private final RecurrenceWorkflow workflow;

    public OrchestratorController(VectorStore vectorStore,
                                  ChatClient.Builder chatClientBuilder) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(5)
                .build();

        Advisor safeguardAdvisor = SafeGuardAdvisor.builder()
                .sensitiveWords(List.of(
                        "alcohol"
                ))
                .build();

        ChatClient chatClient = chatClientBuilder.defaultAdvisors(
                MessageChatMemoryAdvisor
                        .builder(chatMemory)
                        .build(),
                safeguardAdvisor
        ).build();

        workflow = new RecurrenceWorkflow(chatClient);
    }

    @GetMapping("/ai/chain")
    String generation(@RequestParam(value = "message",
                              defaultValue = "What is wine?")
                      String userInput,
                      @RequestParam(value = "conversationId",
                              defaultValue = "001")
                      String conversationId) {
        return this.workflow.chain(userInput);
    }
}
