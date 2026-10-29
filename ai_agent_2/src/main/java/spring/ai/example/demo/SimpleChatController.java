package spring.ai.example.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Example 1 - Basic LLM integration with a sliding conversation memory window.
 * Try: GET /ai/chat?message=Hi&conversationId=001
 */
@RestController
class SimpleChatController {

    private final ChatClient chatClient;

    public SimpleChatController(ChatClient.Builder chatClientBuilder) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(5)
                .build();

        this.chatClient = chatClientBuilder.defaultAdvisors(
                MessageChatMemoryAdvisor
                        .builder(chatMemory)
                        .build()
        ).build();
    }

    @GetMapping("/ai/chat")
    String generation(@RequestParam(value = "message",
                              defaultValue = "Hello LLM") String userInput,
                      @RequestParam(value = "conversationId",
                              defaultValue = "001") String conversationId) {
        return this.chatClient.prompt("provide succinct answers.")
                .user(userInput)
                .advisors(a ->
                        a.param(
                                ChatMemory.CONVERSATION_ID
                                , conversationId))
                .call()
                .content();
    }
}
