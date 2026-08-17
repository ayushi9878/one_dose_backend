package com.careflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    private final CareFlowProperties properties;

    @Bean
    public OpenAPI careFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CareFlow API")
                        .version(properties.getVersion())
                        .description("""
                                Continuity care and medication management platform.

                                CareFlow tracks post-discharge follow-ups, medication adherence and
                                operational risk signals, routing cases that need attention to a human
                                care manager.

                                **Clinical safety note:** CareFlow does not diagnose conditions or
                                recommend treatment. The risk engine emits deterministic *operational*
                                signals only, and every high-risk case is routed to a qualified human
                                for review.

                                Authenticate via `POST /api/auth/login`, then send the returned token as
                                `Authorization: Bearer <token>`.
                                """)
                        .contact(new Contact().name("CareFlow Engineering"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url("/").description("Current environment")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT issued by /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
