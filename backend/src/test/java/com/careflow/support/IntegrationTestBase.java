package com.careflow.support;

import com.careflow.auth.dto.AuthResponse;
import com.careflow.auth.dto.LoginRequest;
import com.careflow.common.enums.UserRole;
import com.careflow.user.User;
import com.careflow.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Boots the full application context and exposes helpers for authenticating as
 * each role, so integration tests exercise the real security filter chain
 * rather than bypassing it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "IntegrationTest1";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected User admin;
    protected User careManager;

    @BeforeEach
    void seedAccounts() {
        admin = findOrCreate("admin@test.careflow", "Test Admin", UserRole.ADMIN);
        careManager = findOrCreate("manager@test.careflow", "Test Manager", UserRole.CARE_MANAGER);
    }

    protected User findOrCreate(String email, String fullName, UserRole role) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .passwordHash(passwordEncoder.encode(PASSWORD))
                        .fullName(fullName)
                        .role(role)
                        .enabled(true)
                        .build()));
    }

    protected String tokenFor(String email) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(response, AuthResponse.class).accessToken();
    }

    protected String adminToken() throws Exception {
        return tokenFor(admin.getEmail());
    }

    protected String careManagerToken() throws Exception {
        return tokenFor(careManager.getEmail());
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
