package com.tracker.leetcode.tracker.Service;

import com.tracker.leetcode.tracker.Exception.StudentNotFoundException;
import com.tracker.leetcode.tracker.Models.Student;
import com.tracker.leetcode.tracker.Repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public Student fetchAndUpdateStudentProgress(String username) {
        log.info("Updating calendar heatmap for user: {}", username);
        Student student = getStudentOrThrow(username);
        student.setProgressHistory(leetCodeApiClient.fetchCalendarData(username));
        return studentRepository.save(student);
    }

    public Student fetchAndUpdateProblemStats(String username) {
        log.info("Updating problem stats for user: {}", username);
        Student student = getStudentOrThrow(username);
        student.setProblemStats(leetCodeApiClient.fetchProblemStats(username));
        return studentRepository.save(student);
    }

    public Student fetchAndUpdateRecentSubmissions(String username) {
        log.info("Updating recent submissions for user: {}", username);
        Student student = getStudentOrThrow(username);
        student.setRecentSubmissions(leetCodeApiClient.fetchRecentSubmissions(username, 5));
        return studentRepository.save(student);
    }

    // THIS IS THE METHOD FOR TEST 3
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

        return studentRepository.save(student);
    }

    // BONUS: The "Sync All" method for your automated Scheduler!
    public Student syncAllProfileData(String username) {
        log.info("Performing FULL profile sync for user: {}", username);
        Student student = getStudentOrThrow(username);

        student.setProgressHistory(leetCodeApiClient.fetchCalendarData(username));
        student.setProblemStats(leetCodeApiClient.fetchProblemStats(username));
        student.setRecentSubmissions(leetCodeApiClient.fetchRecentSubmissions(username, 20));

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

    // Inside StudentService.java
    //
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