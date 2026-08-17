package com.careflow.system;

import com.careflow.config.CareFlowProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Tag(name = "System", description = "Build and deployment metadata.")
public class SystemController {

    private final CareFlowProperties properties;

    @GetMapping("/version")
    @SecurityRequirements
    @Operation(summary = "Get the running build's version metadata",
            description = """
                    Values are injected from the environment at deploy time, so this endpoint
                    proves which build is actually serving traffic.
                    """)
    public VersionResponse version() {
        return new VersionResponse(
                "CareFlow",
                properties.getVersion(),
                properties.getEnvironment(),
                properties.getCommit(),
                properties.getDeployedAt());
    }

    public record VersionResponse(
            String application,
            String version,
            String environment,
            String commit,
            String deployedAt) {
    }
}
