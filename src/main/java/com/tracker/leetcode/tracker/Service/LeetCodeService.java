package com.tracker.leetcode.tracker.Service;

import com.tracker.leetcode.tracker.Exception.StudentNotFoundException;
import com.tracker.leetcode.tracker.Models.Student;
import com.tracker.leetcode.tracker.Repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeetCodeService {

    private final StudentRepository studentRepository;
    private final LeetCodeApiClient leetCodeApiClient;

    /**
     * Core helper: Fetches ALL data from the optimized API client and saves to DB.
     * Keeps this deprecated service compatible with our optimized API client.
     */
    private Student fetchAndSaveFullProfile(String username) {
        Student dbStudent = studentRepository.findByLeetcodeUsername(username)
                .orElseThrow(() -> new StudentNotFoundException("Student '" + username + "' not found in database."));

        // Fetch everything in 1 call using the new master method
        Student freshData = leetCodeApiClient.fetchCompleteProfile(username);

        // Map fresh data to our existing database entity
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

        return studentRepository.save(dbStudent);
    }

    /**
     * Deprecated: This service is maintained for backward compatibility only.
     * Please use StudentService instead for all new code.
     * This method fetches and updates student progress (calendar heatmap).
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public Student fetchAndUpdateStudentProgress(String leetcodeUsername) {
        log.info("DEPRECATED: fetchAndUpdateStudentProgress called. Please use StudentService instead.");
        return fetchAndSaveFullProfile(leetcodeUsername);
    }

    /**
     * Deprecated: This service is maintained for backward compatibility only.
     * Please use StudentService instead for all new code.
     * This method fetches and updates problem statistics.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public Student fetchAndUpdateProblemStats(String leetcodeUsername) {
        log.info("DEPRECATED: fetchAndUpdateProblemStats called. Please use StudentService instead.");
        return fetchAndSaveFullProfile(leetcodeUsername);
    }
}