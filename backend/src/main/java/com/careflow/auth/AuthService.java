package com.careflow.auth;

import com.careflow.auth.dto.AuthResponse;
import com.careflow.auth.dto.LoginRequest;
import com.careflow.auth.dto.RegisterRequest;
import com.careflow.auth.dto.UserProfile;
import com.careflow.common.enums.UserRole;
import com.careflow.exception.DuplicateResourceException;
import com.careflow.patient.PatientRepository;
import com.careflow.security.CareFlowUserDetails;
import com.careflow.security.CurrentUserProvider;
import com.careflow.security.JwtTokenProvider;
import com.careflow.user.User;
import com.careflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Registers an account. Self-service registration is limited to care
     * managers and patients; creating an ADMIN requires an authenticated admin,
     * so the endpoint cannot be used to grant oneself elevated access.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        UserRole requestedRole = request.role() != null ? request.role() : UserRole.CARE_MANAGER;

        if (requestedRole == UserRole.ADMIN && !currentUserProvider.isAdmin()) {
            throw new AccessDeniedException("Only an administrator can create administrator accounts.");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("An account already exists for this email address.");
        }

        User user = userRepository.save(User.builder()
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(requestedRole)
                .enabled(true)
                .build());

        log.info("Registered user id={} role={}", user.getId(), user.getRole());
        return buildAuthResponse(user);
    }

    /**
     * Verifies credentials through the Spring Security authentication manager so
     * password comparison stays constant-time and the failure message never
     * reveals whether the account exists.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        CareFlowUserDetails principal = (CareFlowUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user is missing."));

        log.info("Login succeeded for user id={}", user.getId());
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserProfile currentUser() {
        CareFlowUserDetails principal = currentUserProvider.require();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user is missing."));
        return toProfile(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = tokenProvider.generateToken(
                user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
        return new AuthResponse(token, "Bearer", tokenProvider.getExpirationSeconds(), toProfile(user));
    }

    private UserProfile toProfile(User user) {
        Long patientId = patientRepository.findByUserId(user.getId())
                .map(patient -> patient.getId())
                .orElse(null);
        return new UserProfile(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), patientId);
    }
}
