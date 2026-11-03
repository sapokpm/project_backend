package com.example.shop.security.service;

import com.example.shop.model.entity.User;
import com.example.shop.model.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** The logged-in caller. Carries the id so services can check ownership without another query. */
public class UserPrincipal implements UserDetails {
    private final Long id;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final boolean enabled;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPassword();
        this.role = user.getRole();
        this.enabled = Boolean.TRUE.equals(user.getEnabled());
    }

    public Long getId() { return id; }

    public UserRole getRole() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }

    @Override public String getUsername() { return email; }

    @Override public boolean isEnabled() { return enabled; }
}
