package com.example.invoiceagent.config;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }

    @Bean
    RestClientCustomizer invoiceAgentRestClientCustomizer() {
        return builder -> builder.defaultHeader("User-Agent", "invoice-agent/0.0.1");
    }
}
