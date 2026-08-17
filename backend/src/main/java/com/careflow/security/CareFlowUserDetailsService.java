package com.careflow.security;

import com.careflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CareFlowUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(email)
                .map(CareFlowUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for the supplied credentials."));
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long userId) {
        return userRepository.findById(userId)
                .map(CareFlowUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for the supplied credentials."));
    }
}
