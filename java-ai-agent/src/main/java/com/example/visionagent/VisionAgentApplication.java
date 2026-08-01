package com.example.visionagent;

import com.example.visionagent.document.DocumentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DocumentProperties.class)
public class VisionAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(VisionAgentApplication.class, args);
    }
}
