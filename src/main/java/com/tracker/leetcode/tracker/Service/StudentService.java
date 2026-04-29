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
     * Core helper: Fetches ALL data from the optimized API client and saves to DB.
     * Because our API now fetches everything in 1 call, we just update the whole student
     * anytime any piece of data is requested!
     */
    private Student fetchAndSaveFullProfile(String username) {
        Student dbStudent = getStudentOrThrow(username);

        // 1. Fetch EVERYTHING from LeetCode in exactly ONE network request
        Student freshData = leetCodeApiClient.fetchCompleteProfile(username);

        // 2. Map the fresh data to our existing database entity
        dbStudent.setAbout(freshData.getAbout());
        dbStudent.setRank(freshData.getRank());
        dbStudent.setAvatarUrl(freshData.getAvatarUrl());
        dbStudent.setSocialMedia(freshData.getSocialMedia());
        dbStudent.setCurrentContestRating(freshData.getCurrentContestRating());
        dbStudent.setProgressHistory(freshData.getProgressHistory());
        dbStudent.setProblemStats(freshData.getProblemStats());
        dbStudent.setRecentSubmissions(freshData.getRecentSubmissions());
        dbStudent.setSkills(freshData.getSkills());
        dbStudent.setBadges(freshData.getBadges());
        dbStudent.setContestHistory(freshData.getContestHistory());

        // 3. Save to MongoDB
        return studentRepository.save(dbStudent);
    }

    /**
     * Fetches and updates student progress (calendar heatmap)
     * Results are cached for 30 minutes
     */
    @Cacheable(value = "student-progress", key = "#username")
    public Student fetchAndUpdateStudentProgress(String username) {
        log.info("Updating calendar heatmap for user: {}", username);
        return fetchAndSaveFullProfile(username);
    }

    /**
     * Fetches and updates problem statistics
     * Results are cached for 30 minutes
     */
    @Cacheable(value = "student-stats", key = "#username")
    public Student fetchAndUpdateProblemStats(String username) {
        log.info("Updating problem stats for user: {}", username);
        return fetchAndSaveFullProfile(username);
    }

    /**
     * Fetches and updates recent submissions
     * Results are cached for 30 minutes
     */
    @Cacheable(value = "student-recent", key = "#username")
    public Student fetchAndUpdateRecentSubmissions(String username) {
        log.info("Updating recent submissions for user: {}", username);
        return fetchAndSaveFullProfile(username);
    }

    /**
     * Fetches and updates extended profile (socials, contests, badges)
     * Results are cached for 1 hour
     */
    @Cacheable(value = "student-profile", key = "#username")
    public Student fetchAndUpdateExtendedProfile(String username) {
        log.info("Updating extended profile (Socials, Contests, Badges) for user: {}", username);
        return fetchAndSaveFullProfile(username);
    }

    /**
     * Syncs all profile data and clears related caches to ensure fresh data
     */
    @Caching(evict = {
            @CacheEvict(value = {"student-progress", "student-stats", "student-recent", "student-profile"}, key = "#username"),
            @CacheEvict(value = {"classroom-dashboard", "classroom-analytics"}, allEntries = true)
    })
    public Student syncAllProfileData(String username) {
        log.info("Performing FULL optimized profile sync for user: {}", username);
        return fetchAndSaveFullProfile(username);
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