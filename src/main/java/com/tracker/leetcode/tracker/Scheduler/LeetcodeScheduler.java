package com.tracker.leetcode.tracker.Scheduler;

import com.tracker.leetcode.tracker.Models.Student;
import com.tracker.leetcode.tracker.Repository.StudentRepository;
import com.tracker.leetcode.tracker.Service.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class LeetcodeScheduler {

    private final StudentService studentService;
    private final StudentRepository studentRepository;

    @Scheduled(cron = "0 55 23 * * ?")
//    @Scheduled(fixedRate = 30000) // Runs every 30,000 milliseconds
    // Inside LeetcodeScheduler.java
    public void updateAllStudentsDaily() {
        List<Student> students = studentRepository.findAll();
        for (Student student : students) {
            // This fires off the task to a background thread and immediately moves to the next student
            studentService.syncProfileAsync(student.getLeetcodeUsername());
        }
    }

}
