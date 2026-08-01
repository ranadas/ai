package spring.ai.example.demo;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Example 7 - Prompt-chaining workflow. The output of each step becomes the input
 * of the next, building up an answer across three sequential LLM calls.
 */
class RecurrenceWorkflow {

    public static final String GREEN = "\u001B[32m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";

    private static final String[] prompts = {
            "Extract wine details from advisors. Format each piece of data.",
            "from the wine details find out details of its origin location. " +
                    "Add those location details to the wine details.",
            "Summarize all details together. Answer should be elaborated and " +
                    "to the point. it should be within 250 words"
    };

    private final ChatClient chatClient;
    private final String[] systemPrompts;

    public RecurrenceWorkflow(ChatClient chatClient) {
        this(chatClient, prompts);
    }

    public RecurrenceWorkflow(ChatClient chatClient, String[] systemPrompts) {
        this.chatClient = chatClient;
        this.systemPrompts = systemPrompts;
    }

    public String chain(String userInput) {
        int step = 0;
        String response = userInput;
        System.out.printf(GREEN + "User asked:\n%s%n", response);

        for (String prompt : systemPrompts) {
            String input = String.format("{%s}\n {%s}", prompt, response);
            System.out.println(PURPLE + input);
            response = this.chatClient.prompt(input).call().content();
            System.out.println(BLUE + String.format("\nSTEP %s:\n %s",
                    step++, response));
        }
        return response;
    }
}
