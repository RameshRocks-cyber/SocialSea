package com.socialsea.security;

import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .or(() -> userRepository.findByNameIgnoreCase(email))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        if (user.isBanned()) {
            throw new UsernameNotFoundException("User banned: " + email);
        }

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        // Add Role
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        // Add Permissions
        user.getRole().getPermissions().forEach(permission -> 
            authorities.add(new SimpleGrantedAuthority(permission.name()))
        );

        // OTP-only accounts can exist without a password; provide a placeholder
        // so UserDetails construction does not fail during JWT auth.
        String password = (user.getPassword() == null || user.getPassword().isBlank())
                ? "{noop}otp-user"
                : user.getPassword();

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                password,
                authorities
        );
    }
}
