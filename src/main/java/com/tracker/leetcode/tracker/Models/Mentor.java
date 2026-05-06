package com.tracker.leetcode.tracker.Models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jspecify.annotations.NonNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mentor implements UserDetails {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String email;

    private String password;

    private Role role;

    private boolean isEmailVerified = false;
    private String otp;
    private long otpExpiryTime; // Store as epoch timestamp

    @Builder.Default
    private List<String> classroomIds = new ArrayList<>();

    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    @Builder.Default
    private boolean enabled = true;

    @Override
    @JsonIgnore
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        // This grants the user their role (e.g., "ROLE_MENTOR") so Spring can authorize routes
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    @JsonIgnore
    public @NonNull String getUsername() {
        // We use Email as the username for logging in
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() { return true; }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() { return true; }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return enabled ;}
}
