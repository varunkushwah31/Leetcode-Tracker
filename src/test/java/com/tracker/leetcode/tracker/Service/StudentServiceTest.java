package com.tracker.leetcode.tracker.Service;

import com.tracker.leetcode.tracker.Models.Student;
import com.tracker.leetcode.tracker.Repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private LeetCodeApiClient leetCodeApiClient;

    @InjectMocks
    private StudentService studentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void fetchAndUpdateStudentProgress_ShouldUpdateStudent() {
        String username = "testuser";
        Student student = new Student();
        student.setLeetcodeUsername(username);

        when(studentRepository.findByLeetcodeUsername(username)).thenReturn(Optional.of(student));
        when(leetCodeApiClient.fetchCalendarData(username)).thenReturn("some_calendar_data");
        when(studentRepository.save(any(Student.class))).thenReturn(student);

        Student result = studentService.fetchAndUpdateStudentProgress(username);

        assertNotNull(result);
        assertEquals(username, result.getLeetcodeUsername());
        verify(studentRepository).save(student);
    }

    @Test
    void fetchAndUpdateProblemStats_ShouldUpdateStats() {
        String username = "testuser";
        Student student = new Student();
        student.setLeetcodeUsername(username);

        when(studentRepository.findByLeetcodeUsername(username)).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenReturn(student);

        Student result = studentService.fetchAndUpdateProblemStats(username);

        assertNotNull(result);
        verify(leetCodeApiClient).fetchProblemStats(username);
        verify(studentRepository).save(student);
    }
}
