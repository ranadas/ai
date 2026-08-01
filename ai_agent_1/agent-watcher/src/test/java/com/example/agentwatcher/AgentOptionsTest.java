package com.example.agentwatcher;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOptionsTest {
    @Test
    void parsesPythonStyleArguments() {
        MockEnvironment environment = new MockEnvironment();
        AgentOptions options = AgentOptions.from(
                new String[]{
                        "--inbox", "input",
                        "--output", "out",
                        "--rejected=bad",
                        "--model", "gpt-4o",
                        "--skip-existing"
                },
                environment,
                Map.of("OPENAI_API_KEY", "key-from-env-file")
        );

        assertThat(options.inbox()).hasToString("input");
        assertThat(options.output()).hasToString("out");
        assertThat(options.rejected()).hasToString("bad");
        assertThat(options.model()).isEqualTo("gpt-4o");
        assertThat(options.skipExisting()).isTrue();
        assertThat(options.apiKey()).isEqualTo("key-from-env-file");
    }

    @Test
    void exportedEnvironmentWinsOverDotenvForModelAndApiKey() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("OPENAI_MODEL", "env-model")
                .withProperty("OPENAI_API_KEY", "env-key");

        AgentOptions options = AgentOptions.from(
                new String[]{},
                environment,
                Map.of("OPENAI_MODEL", "dotenv-model", "OPENAI_API_KEY", "dotenv-key")
        );

        assertThat(options.model()).isEqualTo("env-model");
        assertThat(options.apiKey()).isEqualTo("env-key");
    }
}
