package com.tracker.leetcode.tracker.Service;

import com.tracker.leetcode.tracker.Exception.StudentNotFoundException;
import com.tracker.leetcode.tracker.Models.Student;
import com.tracker.leetcode.tracker.Repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private LeetCodeApiClient leetCodeApiClient;

    @InjectMocks
    private StudentService studentService;

    private Student mockStudent;

    @BeforeEach
    void setUp() {
        mockStudent = new Student();
        mockStudent.setId("123");
        mockStudent.setName("Test Student");
        mockStudent.setLeetcodeUsername("test_user");
    }

    @Test
    void syncAllProfileData_WhenStudentExists_ShouldReturnUpdatedStudent() {
        // Arrange: Tell the mocks what to do when called
        when(studentRepository.findByLeetcodeUsername("test_user")).thenReturn(Optional.of(mockStudent));

        // We mock the API call to return a blank student just to satisfy the method signature
        when(leetCodeApiClient.fetchExtendedProfileDetails(anyString())).thenReturn(new Student());
        when(studentRepository.save(any(Student.class))).thenReturn(mockStudent);

        // Act
        Student result = studentService.syncAllProfileData("test_user");

        // Assert
        assertNotNull(result);
        assertEquals("test_user", result.getLeetcodeUsername());

        // Verify that our API client was actually called during the sync
        verify(leetCodeApiClient, times(1)).fetchCalendarData("test_user");
        verify(leetCodeApiClient, times(1)).fetchProblemStats("test_user");
        verify(studentRepository, times(1)).save(mockStudent);
    }

    @Test
    void syncAllProfileData_WhenStudentDoesNotExist_ShouldThrowException() {
        // Arrange
        when(studentRepository.findByLeetcodeUsername("unknown_user")).thenReturn(Optional.empty());

        // Act & Assert
        Exception exception = assertThrows(StudentNotFoundException.class, () -> studentService.syncAllProfileData("unknown_user"));

        assertTrue(exception.getMessage().contains("not found in database"));

        // Verify the API client was NEVER called because it failed early
        verify(leetCodeApiClient, never()).fetchCalendarData(anyString());
    }
}