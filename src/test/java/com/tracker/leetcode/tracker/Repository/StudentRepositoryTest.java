package com.tracker.leetcode.tracker.Repository;

import com.tracker.leetcode.tracker.Models.Student;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest
class StudentRepositoryTest {

    @Autowired
    private StudentRepository studentRepository;

    @AfterEach
    void tearDown() {
        studentRepository.deleteAll(); // Clean up after each test
    }

    @Test
    void findByLeetcodeUsername_WhenStudentExists_ShouldReturnStudent() {
        // Arrange
        Student student = new Student();
        student.setName("Bob");
        student.setLeetcodeUsername("bob_builder");
        student.setEmail("bob@test.com");
        studentRepository.save(student);

        // Act
        Optional<Student> result = studentRepository.findByLeetcodeUsername("bob_builder");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Bob", result.get().getName());
    }

    @Test
    void findByLeetcodeUsername_WhenStudentDoesNotExist_ShouldReturnEmpty() {
        // Act
        Optional<Student> result = studentRepository.findByLeetcodeUsername("ghost_user");

        // Assert
        assertTrue(result.isEmpty());
    }
}