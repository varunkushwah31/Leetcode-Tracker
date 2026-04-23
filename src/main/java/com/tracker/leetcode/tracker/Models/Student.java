package com.tracker.leetcode.tracker.Models;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "Students")
public class Student implements UserDetails {

    @Id
    private String id;
    private String name;
    @Indexed(unique = true)
    private String leetcodeUsername;
    private String about;
    private String rank;
    private double currentContestRating;
    private SocialMedia socialMedia;
    @Indexed(unique = true)
    private String email;
    private String password;
    private Role role = Role.STUDENT;
    private AuthProvider authProvider = AuthProvider.LOCAL;
    private boolean enabled = true;
    private String avatarUrl;


    private List<DailyProgress> progressHistory = new ArrayList<>();
    private List<Badge> badges = new ArrayList<>();
    private List<ContestHistory> contestHistory = new ArrayList<>();
    private List<ProblemStats> problemStats = new ArrayList<>();
    private List<RecentSubmission> recentSubmissions = new ArrayList<>();
    private List<String > manuallyCompletedAssignments = new ArrayList<>();
    private List<SkillStat> skills = new ArrayList<>();


    public Student(String leetcodeUsername, String name) {
        this.leetcodeUsername = leetcodeUsername;
        this.name = name;
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    @JsonIgnore
    public String getUsername() {
        return email; // We use email to log in
    }

    @Override
    public String getPassword() { return password; }

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
    public boolean isEnabled() { return enabled; }
}
