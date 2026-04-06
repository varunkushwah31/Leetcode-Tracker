package com.tracker.leetcode.tracker.Mapper;

import com.tracker.leetcode.tracker.DTO.*;
import com.tracker.leetcode.tracker.Models.Assignment;
import com.tracker.leetcode.tracker.Models.DailyProgress;
import com.tracker.leetcode.tracker.Models.ProblemStats;
import com.tracker.leetcode.tracker.Models.Student;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
public class StudentMapper {

    // Use this when fetching a student globally (no classroom context)
    public StudentSummaryDTO toSummaryDTO(Student student) {
        return toSummaryDTO(student, null);
    }

    // Updated Method: Now accepts the list of assignments!
    public StudentSummaryDTO toSummaryDTO(Student student, List<Assignment> classroomAssignments) {
        int completed = 0;
        int pending = 0;

        if (classroomAssignments != null && !classroomAssignments.isEmpty()) {
            for (Assignment assignment : classroomAssignments) {
                // 1. Check if they manually validated it first (O(1) lookup if it's a HashSet, very fast)
                boolean isManuallyValidated = student.getManuallyCompletedAssignments() != null &&
                        student.getManuallyCompletedAssignments().contains(assignment.getId());

                // 2. Fallback: Check if the server caught it in their recent activity
                boolean isCaughtByServer = false;
                if (!isManuallyValidated && student.getRecentSubmissions() != null) {
                    isCaughtByServer = student.getRecentSubmissions().stream()
                            .anyMatch(sub -> sub.getTitleSlug().equals(assignment.getTitleSlug())
                                    && sub.getTimestamp() >= assignment.getStartTimestamp()
                                    && sub.getTimestamp() <= assignment.getEndTimestamp());
                }

                // If either is true, they get credit!
                if (isManuallyValidated || isCaughtByServer) {
                    completed++;
                } else {
                    pending++;
                }
            }
        }

        return StudentSummaryDTO.builder()
                .id(student.getId())
                .name(student.getName())
                .email(student.getEmail())
                .leetcodeUsername(student.getLeetcodeUsername())
                .rank(student.getRank() != null ? student.getRank() : "Unranked")
                .currentContestRating(student.getCurrentContestRating())
                .totalSolved(calculateTotalSolved(student.getProblemStats()))
                .consistencyStreak(calculateStreak(student.getProgressHistory()))
                .completedAssignments(completed)
                .pendingAssignments(pending)
                .avatarUrl(student.getAvatarUrl())
                .build();
    }

    public StudentProgressDTO toProgressDTO(Student student) {
        return StudentProgressDTO.builder()
                .leetcodeUsername(student.getLeetcodeUsername())
                .progressHistory(student.getProgressHistory())
                .build();
    }

    public StudentStatsDTO toStatsDTO(Student student) {
        return StudentStatsDTO.builder()
                .leetcodeUsername(student.getLeetcodeUsername())
                .problemStats(student.getProblemStats())
                .build();
    }

    public StudentRecentDTO toRecentDTO(Student student) {
        return StudentRecentDTO.builder()
                .leetcodeUsername(student.getLeetcodeUsername())
                .recentSubmissions(student.getRecentSubmissions())
                .build();
    }

    // THE FIX IS HERE: We now pass ALL the heavy data to the Extended DTO!
    public StudentExtendedDTO toExtendedDTO(Student student) {
        return StudentExtendedDTO.builder()
                .id(student.getId()) // Good practice to include ID
                .name(student.getName())
                .email(student.getEmail()) // Added Email
                .leetcodeUsername(student.getLeetcodeUsername())
                .about(student.getAbout())
                .rank(student.getRank())
                .currentContestRating(student.getCurrentContestRating())
                .socialMedia(student.getSocialMedia())
                .badges(student.getBadges())
                .contestHistory(student.getContestHistory())

                // --- THE MISSING ARRAYS ---
                .problemStats(student.getProblemStats())
                .recentSubmissions(student.getRecentSubmissions())
                .progressHistory(student.getProgressHistory())

                // Fallbacks just in case the arrays are empty
                .avatarUrl(student.getAvatarUrl())
                .build();
    }

    // --- Helper Methods ---

    private int calculateTotalSolved(List<ProblemStats> stats){
        if (stats == null || stats.isEmpty()) return 0;
        // Find the stat where difficulty is "All"
        return stats.stream()
                .filter(stat -> "All".equalsIgnoreCase(stat.getDifficulty()))
                .mapToInt(ProblemStats::getCount)
                .findFirst()
                .orElse(0);
    }

    private int calculateStreak(List<DailyProgress> history) {
        if (history == null || history.isEmpty()) return 0;

        // Sort history by date descending (newest first)
        List<DailyProgress> sortedHistory = history.stream()
                .sorted(Comparator.comparing(DailyProgress::getDate).reversed())
                .toList();

        int streak = 0;
        LocalDate expectedDate = sortedHistory.getFirst().getDate(); // Start with their most recent submission

        // If their most recent submission is more than 2 days ago, their current streak is dead (0)
        if (expectedDate.isBefore(LocalDate.now().minusDays(2))) {
            return 0;
        }

        for (DailyProgress progress : sortedHistory) {
            if (progress.getDate().equals(expectedDate)) {
                streak++;
                expectedDate = expectedDate.minusDays(1); // Expect the next submission to be the day before
            } else {
                break; // The streak is broken
            }
        }
        return streak;
    }
}