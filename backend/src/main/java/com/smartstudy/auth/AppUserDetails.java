package com.smartstudy.auth;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import com.smartstudy.user.User;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The principal stored in the HTTP session after login. It deliberately keeps only the
 * immutable identity data (no JPA entity) so it can be serialized with the session. The
 * password hash is erased after authentication.
 */
public class AppUserDetails implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String fullName;
    private final boolean enabled;
    private final List<SimpleGrantedAuthority> authorities;
    private String passwordHash;

    public AppUserDetails(Long id, String email, String fullName, String passwordHash, boolean enabled,
                          String role) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public static AppUserDetails from(User user) {
        return new AppUserDetails(user.getId(), user.getEmail(), user.getFullName(), user.getPasswordHash(),
                user.isEnabled(), user.getRole().name());
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
