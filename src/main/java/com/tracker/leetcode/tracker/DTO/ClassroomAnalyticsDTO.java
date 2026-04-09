package com.tracker.leetcode.tracker.DTO;

import com.tracker.leetcode.tracker.Models.SkillStat;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ClassroomAnalyticsDTO {
    private String classroomId;
    private String className;
    private int totalStudents;

    // Averages
    private int averageTotalSolved;
    private int averageEasy;
    private int averageMedium;
    private int averageHard;

    // Engagement
    private int activeStudentsThisWeek; // Students with a recent submission
    private double classEngagementScore; // Percentage 0-100

    // Topic Analysis
    private List<SkillStat> topStrengths;
    private List<SkillStat> criticalWeaknesses;
}