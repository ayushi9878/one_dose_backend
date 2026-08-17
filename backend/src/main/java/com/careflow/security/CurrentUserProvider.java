package com.careflow.security;

import com.careflow.common.enums.UserRole;
import com.careflow.exception.BusinessRuleException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Single access point for the authenticated principal so services never reach
 * into the security context directly.
 */
@Component
public class CurrentUserProvider {

    public Optional<CareFlowUserDetails> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof CareFlowUserDetails details) {
            return Optional.of(details);
        }
        return Optional.empty();
    }

    public CareFlowUserDetails require() {
        return find().orElseThrow(
                () -> new BusinessRuleException("No authenticated user is bound to this request."));
    }

    public Long requireId() {
        return require().getId();
    }

    public boolean hasRole(UserRole role) {
        return find().map(details -> details.getRole() == role).orElse(false);
    }

    public boolean isAdmin() {
        return hasRole(UserRole.ADMIN);
    }
}
