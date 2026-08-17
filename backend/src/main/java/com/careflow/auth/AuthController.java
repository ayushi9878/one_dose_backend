package com.careflow.auth;

import com.careflow.auth.dto.AuthResponse;
import com.careflow.auth.dto.LoginRequest;
import com.careflow.auth.dto.RegisterRequest;
import com.careflow.auth.dto.UserProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login and identity.")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Register an account",
            description = "Creates a CARE_MANAGER by default. Creating an ADMIN requires an authenticated admin.")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Exchange credentials for a JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's profile")
    public UserProfile me() {
        return authService.currentUser();
    }
}
