package com.tracker.leetcode.tracker.DTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentSummaryDTO {
    private String id;
    private String name;
    private String leetcodeUsername;
    private String rank;
    private double currentContestRating;
    private int totalSolved;
    private int consistencyStreak;
    private int completedAssignments;
    private int pendingAssignments;
    private String email;
    private String avatarUrl;
}