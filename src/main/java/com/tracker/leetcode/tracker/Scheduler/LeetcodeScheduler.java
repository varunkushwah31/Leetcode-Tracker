package com.tracker.leetcode.tracker.Scheduler;

import com.tracker.leetcode.tracker.Models.Student;
import com.tracker.leetcode.tracker.Repository.StudentRepository;
import com.tracker.leetcode.tracker.Service.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
public class LeetcodeScheduler {

    private final StudentService studentService;
    private final StudentRepository studentRepository;

    @Scheduled(cron = "0 55 23 * * ?")
    // <-- ADD THE LOCK HERE -->
    // name: Must be unique for this specific task
    // lockAtLeastFor: Prevents clock de-sync issues between servers (locks it for at least 5 mins)
    // lockAtMostFor: Max time the lock is held (10 mins)
    @SchedulerLock(name = "updateAllStudentsDaily", lockAtLeastFor = "5m", lockAtMostFor = "10m")
    public void updateAllStudentsDaily() {
        log.info("Executing ShedLock protected Daily Sync...");
        List<Student> students = studentRepository.findAll();
        for (Student student : students) {
            studentService.syncProfileAsync(student.getLeetcodeUsername());
        }
    }
}