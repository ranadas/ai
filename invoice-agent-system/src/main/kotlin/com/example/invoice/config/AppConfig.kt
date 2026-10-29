package com.example.invoice.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.http.HttpClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(InvoiceProperties::class)
class AppConfig {
    @Bean
    fun httpClient(): HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    @Bean
    fun jsonMapper(objectMapper: ObjectMapper): ObjectMapper = objectMapper.findAndRegisterModules()
}
