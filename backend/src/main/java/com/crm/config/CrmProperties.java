package com.crm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm")
public record CrmProperties(App app, Seed seed, Mail mail, Ai ai) {

    public record App(
        String name, String apiPrefix, String jwtSecret, int jwtAccessMinutes, int jwtRefreshDays,
        String corsOrigins, String encryptionKey, String storageDir, String appUrl
    ) {}

    public record Seed(boolean enabled, String adminEmail, String adminPassword, String orgName) {}

    public record Mail(String host, int port, String username, String password, String from) {}

    public record Ai(String apiKey, String baseUrl, String model) {}
}
