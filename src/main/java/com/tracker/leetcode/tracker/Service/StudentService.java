package com.tracker.leetcode.tracker.Service;

import com.tracker.leetcode.tracker.Exception.StudentNotFoundException;
import com.tracker.leetcode.tracker.Models.Student;
import com.tracker.leetcode.tracker.Repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final LeetCodeApiClient leetCodeApiClient;

    // Helper method to keep code DRY
    private Student getStudentOrThrow(String username) {
        return studentRepository.findByLeetcodeUsername(username)
                .orElseThrow(() -> new StudentNotFoundException("Student '" + username + "' not found in database. Please add them first!"));
    }

    /**
     * Fetches and updates student progress (calendar heatmap)
     * Results are cached for 30 minutes
     */
    @Cacheable(value = "student-progress", key = "#username")
    public Student fetchAndUpdateStudentProgress(String username) {
        log.info("Updating calendar heatmap for user: {}", username);
        Student student = getStudentOrThrow(username);
        student.setProgressHistory(leetCodeApiClient.fetchCalendarData(username));
        return studentRepository.save(student);
    }

    /**
     * Fetches and updates problem statistics
     * Results are cached for 30 minutes
     */
    @Cacheable(value = "student-stats", key = "#username")
    public Student fetchAndUpdateProblemStats(String username) {
        log.info("Updating problem stats for user: {}", username);
        Student student = getStudentOrThrow(username);
        student.setProblemStats(leetCodeApiClient.fetchProblemStats(username));
        return studentRepository.save(student);
    }

    /**
     * Fetches and updates recent submissions
     * Results are cached for 30 minutes
     */
    @Cacheable(value = "student-recent", key = "#username")
    public Student fetchAndUpdateRecentSubmissions(String username) {
        log.info("Updating recent submissions for user: {}", username);
        Student student = getStudentOrThrow(username);
        student.setRecentSubmissions(leetCodeApiClient.fetchRecentSubmissions(username, 5));
        return studentRepository.save(student);
    }

    /**
     * Fetches and updates extended profile (socials, contests, badges)
     * Results are cached for 1 hour
     */
    @Cacheable(value = "student-profile", key = "#username")
    public Student fetchAndUpdateExtendedProfile(String username) {
        log.info("Updating extended profile (Socials, Contests, Badges) for user: {}", username);
        Student student = getStudentOrThrow(username);

        Student extendedData = leetCodeApiClient.fetchExtendedProfileDetails(username);

        student.setAbout(extendedData.getAbout());
        student.setRank(extendedData.getRank());
        student.setCurrentContestRating(extendedData.getCurrentContestRating());
        student.setSocialMedia(extendedData.getSocialMedia());
        student.setBadges(extendedData.getBadges());
        student.setContestHistory(extendedData.getContestHistory());
        student.setAvatarUrl(extendedData.getAvatarUrl());
        student.setSkills(leetCodeApiClient.fetchSkillStats(username));

        return studentRepository.save(student);
    }

    /**
     * Syncs all profile data and clears related caches to ensure fresh data
     */
    @Caching(evict = {
            // 1. Clear this specific student's personal data
            @CacheEvict(value = {"student-progress", "student-stats", "student-recent", "student-profile"}, key = "#username"),
            // 2. Blast away all classroom caches so they rebuild with this new fresh data!
            @CacheEvict(value = {"classroom-dashboard", "classroom-analytics"}, allEntries = true)
    })
    public Student syncAllProfileData(String username) {
        log.info("Performing FULL profile sync for user: {}", username);
        Student student = getStudentOrThrow(username);

        student.setProgressHistory(leetCodeApiClient.fetchCalendarData(username));
        student.setProblemStats(leetCodeApiClient.fetchProblemStats(username));
        student.setRecentSubmissions(leetCodeApiClient.fetchRecentSubmissions(username, 20));
        student.setSkills(leetCodeApiClient.fetchSkillStats(username));

        Student extendedData = leetCodeApiClient.fetchExtendedProfileDetails(username);
        student.setAbout(extendedData.getAbout());
        student.setRank(extendedData.getRank());
        student.setCurrentContestRating(extendedData.getCurrentContestRating());
        student.setSocialMedia(extendedData.getSocialMedia());
        student.setBadges(extendedData.getBadges());
        student.setContestHistory(extendedData.getContestHistory());
        student.setAvatarUrl(extendedData.getAvatarUrl());

        return studentRepository.save(student);
    }

    /**
     * Async profile synchronization with cache invalidation
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> syncProfileAsync(String username) {
        try {
            syncAllProfileData(username);
        } catch (Exception e) {
            log.error("Async sync failed for {}", username);
        }
        return CompletableFuture.completedFuture(null);
    }
}