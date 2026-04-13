package com.tracker.leetcode.tracker.Service;

import com.tracker.leetcode.tracker.Models.LearningPath;
import com.tracker.leetcode.tracker.Repository.LearningPathRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPathService {

    private final LearningPathRepository pathRepository;
    private final ClassroomService classroomService;

    // 1. Save a new template to the database
    @CacheEvict(value = "learning-paths-by-mentor", allEntries = true)
    public LearningPath createPath(LearningPath path) {
        log.info("Creating new learning path: {}", path.getTitle());
        return pathRepository.save(path);
    }

    // 2. Fetch all templates created by a specific mentor
    @Cacheable(value = "learning-paths-by-mentor", key = "#mentorId")
    public List<LearningPath> getPathsByMentor(String mentorId) {
        return pathRepository.findByMentorId(mentorId);
    }

    // 3. The Core Automation: Assigning the template to a class
    @CacheEvict(value = "classroom-dashboard", allEntries = true)
    public void assignPathToClassroom(String pathId, String classroomId) {
        LearningPath path = pathRepository.findById(pathId)
                .orElseThrow(() -> new RuntimeException("Learning Path not found"));

        log.info("Assigning path '{}' to classroom '{}'", path.getTitle(), classroomId);

        // Get current time in Unix Seconds (LeetCode standard)
        long currentTimestamp = System.currentTimeMillis() / 1000;

        // Loop through the template and generate real assignments!
        for (LearningPath.PathQuestion question : path.getQuestions()) {

            // Calculate the exact deadline (86400 seconds = 1 day)
            long endTimestamp = currentTimestamp + (question.getDaysToComplete() * 86400L);

            // Reusing your existing logic to add the question to the classroom
            // Note: Make sure the method name matches what you have in ClassroomService!
            classroomService.assignQuestion(
                    classroomId,
                    question.getTitleSlug(),
                    currentTimestamp,
                    endTimestamp
            );
        }
    }
}