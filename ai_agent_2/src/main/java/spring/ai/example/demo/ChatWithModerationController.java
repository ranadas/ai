package spring.ai.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.moderation.ModerationOptions;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.OpenAiModerationOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Example 10 - Content moderation. Both the user input and the model output are screened
 * with OpenAI's moderation model; flagged content yields HTTP 403.
 * Try: GET /ai/moderation?message=What%20is%20wine?
 */
@RestController
class ChatWithModerationController {

    private static final Logger logger = LoggerFactory.getLogger(ChatWithModerationController.class);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final OpenAiModerationModel openAiModerationModel;
    private final ModerationOptions moderationOptions;

    public ChatWithModerationController(VectorStore vectorStore,
                                        ChatClient.Builder chatClientBuilder,
                                        OpenAiModerationModel openAiModerationModel) {
        ChatMemory chatMemory = MessageWindowChatMemory
                .builder().maxMessages(5)
                .build();
        this.vectorStore = vectorStore;
        this.openAiModerationModel = openAiModerationModel;
        this.moderationOptions = OpenAiModerationOptions.builder()
                .model("omni-moderation-latest")
                .build();
        this.chatClient = chatClientBuilder.defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .build())
                .build();
    }

    private boolean classifyModeration(String corpus, boolean userPrompt) {
        try {
            var moderationPrompt = new ModerationPrompt(corpus, moderationOptions);
            var moderationResponse = this.openAiModerationModel.call(moderationPrompt);
            var moderation = moderationResponse.getResult().getOutput();
            logger.info("moderation({}) flagged={} raw={}",
                    userPrompt ? "user" : "assistant",
                    moderation.getResults()
                            .stream()
                            .anyMatch(ModerationResult::isFlagged),
                    moderation.getResults()
            );
            return moderation.getResults()
                    .stream()
                    .anyMatch(ModerationResult::isFlagged);
        } catch (Exception e) {
            logger.warn("moderation check failed - treating as safe=false", e);
            return true;
        }
    }

    @GetMapping("/ai/moderation")
    ResponseEntity<String> generation(@RequestParam(value = "message",
                                              defaultValue = "What is wine?")
                                      String userInput,
                                      @RequestParam(value = "conversationId",
                                              defaultValue = "001")
                                      String conversationId) {
        if (classifyModeration(userInput, true)) {
            return ResponseEntity.status(403)
                    .body("Content violates safety policy");
        }

        var response = this.chatClient.prompt("Please use advisors for " +
                        "answering wine related queries.")
                .user(userInput)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .build())
                .advisors(a -> a.param(
                        ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        if (classifyModeration(response, false)) {
            return ResponseEntity.status(403)
                    .body("Content violates safety policy");
        }
        return ResponseEntity.ok(response);
    }
}
