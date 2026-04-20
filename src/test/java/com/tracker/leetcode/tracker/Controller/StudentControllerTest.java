package com.tracker.leetcode.tracker.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.leetcode.tracker.DTO.StudentSummaryDTO;
import com.tracker.leetcode.tracker.Mapper.StudentMapper;
import com.tracker.leetcode.tracker.Models.Student;
import com.tracker.leetcode.tracker.Repository.ClassroomRepository;
import com.tracker.leetcode.tracker.Repository.StudentRepository;
import com.tracker.leetcode.tracker.Security.JwtAuthenticationFilter;
import com.tracker.leetcode.tracker.Security.JwtService;
import com.tracker.leetcode.tracker.Service.ClassroomService;
import com.tracker.leetcode.tracker.Service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest(StudentController.class)
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters = false) // Bypasses JWT security for pure controller logic testing
class   StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;


    // ADD THIS INSTEAD
    private final ObjectMapper objectMapper = new ObjectMapper();

    // We must mock ALL dependencies injected into the Controller
    @MockitoBean
    private StudentRepository studentRepository;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private StudentMapper studentMapper;

    @MockitoBean
    private ClassroomService classroomService;

    @MockitoBean
    private ClassroomRepository classroomRepository;

    @MockitoBean
    private JwtService jwtService; // Needed for Spring Security context

    @MockitoBean
    JwtAuthenticationFilter jwtAuthenticationFilter; // Needed for Spring Security context

    @MockitoBean
    UserDetailsService userDetailsService; // Needed for Spring Security context

    @Test
    void getAllStudents_ShouldReturnListOfSummariesAndStatus200() throws Exception {
        // Arrange
        Student mockStudent = new Student();
        mockStudent.setName("Alice");

        StudentSummaryDTO mockSummary = new StudentSummaryDTO();
        mockSummary.setName("Alice");
        mockSummary.setLeetcodeUsername("alice_codes");

        when(studentRepository.findAll()).thenReturn(List.of(mockStudent));
        when(studentMapper.toSummaryDTO(any(Student.class))).thenReturn(mockSummary);

        // Act & Assert
        mockMvc.perform(get("/api/students")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[0].leetcodeUsername").value("alice_codes"));
    }
}