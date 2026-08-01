plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.spring") version "2.3.10"
    id("org.springframework.boot") version "4.0.7"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.agent.liquidalts"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0"))

    // --- Spring ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // --- Koog agent runtime (stable 1.0 line) ---
    implementation("ai.koog:koog-agents:1.0.0")
    // Bridges Spring AI's ChatModel into a Koog PromptExecutor (auto-configured bean)
    implementation("ai.koog:koog-spring-ai-starter-model-chat:1.0.0")

    // --- Spring AI: OpenAI-compatible provider, base-url configurable ---
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // --- Coroutines (agent pipeline runs off the request thread) ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // --- PDF text extraction (deterministic step before the LLM sees anything) ---
    implementation("org.apache.pdfbox:pdfbox:3.0.5")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
