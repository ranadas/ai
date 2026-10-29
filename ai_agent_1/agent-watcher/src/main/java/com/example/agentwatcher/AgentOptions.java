package com.example.agentwatcher;

import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

record AgentOptions(
        Path inbox,
        Path output,
        Path rejected,
        String model,
        boolean skipExisting,
        String apiKey
) {
    static AgentOptions from(String[] args, Environment environment, Map<String, String> dotenv) {
        Map<String, String> cli = parseArgs(args);

        Path inbox = Path.of(cli.getOrDefault("inbox", "incoming"));
        Path output = Path.of(cli.getOrDefault("output", "extracted"));
        Path rejected = Path.of(cli.getOrDefault("rejected", "rejected"));
        String model = firstNonBlank(
                cli.get("model"),
                environment.getProperty("OPENAI_MODEL"),
                environment.getProperty("openai.model"),
                dotenv.get("OPENAI_MODEL"),
                "gpt-4o"
        );
        String apiKey = firstNonBlank(
                cli.get("openai-api-key"),
                environment.getProperty("OPENAI_API_KEY"),
                environment.getProperty("openai.api-key"),
                dotenv.get("OPENAI_API_KEY"),
                ""
        );
        boolean skipExisting = cli.containsKey("skip-existing")
                || Boolean.parseBoolean(firstNonBlank(environment.getProperty("agent.skip-existing"), "false"));

        return new AgentOptions(inbox, output, rejected, model, skipExisting, apiKey);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> parsed = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String option = arg.substring(2);
            int equals = option.indexOf('=');
            if (equals >= 0) {
                parsed.put(option.substring(0, equals), option.substring(equals + 1));
                continue;
            }
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                parsed.put(option, args[++i]);
            } else {
                parsed.put(option, "true");
            }
        }
        return parsed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
