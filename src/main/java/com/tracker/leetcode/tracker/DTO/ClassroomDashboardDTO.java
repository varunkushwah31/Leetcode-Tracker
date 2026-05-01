package com.tracker.leetcode.tracker.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomDashboardDTO {
    private String classroomId;
    private String className;
    private String mentorName;

    // The list of students for the leaderboard
    private List<StudentSummaryDTO> enrolledStudents;

    // NEW: The list of active assignments for the "Manage Assignments" tab
    private List<AssignmentDTO> assignments;
}