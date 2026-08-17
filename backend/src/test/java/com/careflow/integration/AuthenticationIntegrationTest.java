package com.careflow.integration;

import com.careflow.auth.dto.LoginRequest;
import com.careflow.auth.dto.RegisterRequest;
import com.careflow.common.enums.UserRole;
import com.careflow.support.IntegrationTestBase;
import com.careflow.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Authentication")
class AuthenticationIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("valid credentials return a JWT and the user profile")
    void loginReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(careManager.getEmail(), PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(careManager.getEmail()))
                .andExpect(jsonPath("$.user.role").value("CARE_MANAGER"));
    }

    @Test
    @DisplayName("the login response never exposes a password hash")
    void loginDoesNotLeakCredentials() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(careManager.getEmail(), PASSWORD))))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("passwordHash").doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("a wrong password is rejected with 401")
    void wrongPasswordIsUnauthorised() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(careManager.getEmail(), "WrongPassword1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an unknown account is rejected with 401, not 404")
    void unknownAccountIsUnauthorised() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest("nobody@test.careflow", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("protected endpoints reject requests without a token")
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a malformed token is rejected")
    void malformedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/patients").header(AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/auth/me returns the authenticated identity")
    void meReturnsCurrentUser() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(careManagerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(careManager.getEmail()))
                .andExpect(jsonPath("$.role").value("CARE_MANAGER"));
    }

    @Test
    @DisplayName("registration stores a BCrypt hash, never the plaintext password")
    void registrationHashesPassword() throws Exception {
        String email = "new.manager@test.careflow";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(json(new RegisterRequest(
                                email, "BrandNewPass1", "New Manager", UserRole.CARE_MANAGER))))
                .andExpect(status().isCreated());

        User created = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(created.getPasswordHash())
                .isNotEqualTo("BrandNewPass1")
                .startsWith("$2");
        assertThat(passwordEncoder.matches("BrandNewPass1", created.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("registering a duplicate email returns 409")
    void duplicateRegistrationConflicts() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(json(new RegisterRequest(
                                careManager.getEmail(), "AnotherPass1", "Copy", UserRole.CARE_MANAGER))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a weak password is rejected with field-level validation errors")
    void weakPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(json(new RegisterRequest(
                                "weak@test.careflow", "short", "Weak", UserRole.CARE_MANAGER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
    }

    @Test
    @DisplayName("anonymous self-registration cannot create an ADMIN account")
    void anonymousCannotSelfRegisterAsAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(json(new RegisterRequest(
                                "escalate@test.careflow", "TryingHard1", "Escalate", UserRole.ADMIN))))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findByEmailIgnoreCase("escalate@test.careflow")).isEmpty();
    }
}
