package com.careflow.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "careflow")
public class CareFlowProperties {

    private String version = "1.0.0";
    private String environment = "local";
    private String commit = "unknown";
    private String deployedAt = "";

    private Security security = new Security();
    private Adherence adherence = new Adherence();
    private Risk risk = new Risk();
    private Ai ai = new Ai();
    private Seed seed = new Seed();

    @Getter
    @Setter
    public static class Security {
        private Jwt jwt = new Jwt();
        private Cors cors = new Cors();
    }

    @Getter
    @Setter
    public static class Jwt {
        /** Base64 or raw secret; must be at least 32 bytes for HS256. */
        @NotBlank
        private String secret;

        @Min(5)
        private long expirationMinutes = 480;

        private String issuer = "careflow";
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:3000");
    }

    @Getter
    @Setter
    public static class Adherence {
        @Min(1)
        private int lowThresholdPercentage = 80;
    }

    @Getter
    @Setter
    public static class Risk {
        @Min(1)
        private int missedDoseThreshold = 3;
    }

    @Getter
    @Setter
    public static class Ai {
        private boolean enabled = false;
        private String apiKey = "";
        private String baseUrl = "https://api.groq.com/openai/v1";
        private String model = "llama-3.3-70b-versatile";
        private int timeoutSeconds = 20;

        public boolean isUsable() {
            return enabled && apiKey != null && !apiKey.isBlank();
        }
    }

    @Getter
    @Setter
    public static class Seed {
        private boolean enabled = false;
    }
}
