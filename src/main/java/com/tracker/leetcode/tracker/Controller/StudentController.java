package com.tracker.leetcode.tracker.Controller;

import com.tracker.leetcode.tracker.DTO.*;
import com.tracker.leetcode.tracker.Exception.DuplicateStudentException;
import com.tracker.leetcode.tracker.Mapper.StudentMapper;
import com.tracker.leetcode.tracker.Models.Classroom;
import com.tracker.leetcode.tracker.Models.Student;
import com.tracker.leetcode.tracker.Repository.ClassroomRepository;
import com.tracker.leetcode.tracker.Repository.StudentRepository;
import com.tracker.leetcode.tracker.Service.ClassroomService;
import com.tracker.leetcode.tracker.Service.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"}, allowCredentials = "true")
public class StudentController {

    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final StudentMapper mapper;
    private final ClassroomService classroomService;
    private final ClassroomRepository classroomRepository;

    // 1. Add a New Student -> Returns Summary
    @PostMapping
    public ResponseEntity<StudentSummaryDTO> addStudent(@RequestBody Student student) {
        log.info("Attempting to add new student with username: {}", student.getLeetcodeUsername());
        Optional<Student> existingStudent = studentRepository.findByLeetcodeUsername(student.getLeetcodeUsername());

        if (existingStudent.isPresent()) {
            throw new DuplicateStudentException("A student with the LeetCode username '" + student.getLeetcodeUsername() + "' already exists.");
        }

        Student savedStudent = studentRepository.save(student);
        return ResponseEntity.ok(mapper.toSummaryDTO(savedStudent));
    }

    // 2. Fetch Heatmap Data -> Returns Progress DTO
    @PostMapping("/{username}/fetch")
    public ResponseEntity<StudentProgressDTO> fetchProgress(@PathVariable String username) {
        Student student = studentService.fetchAndUpdateStudentProgress(username);
        return ResponseEntity.ok(mapper.toProgressDTO(student));
    }

    // 3. Fetch Problem Stats -> Returns Stats DTO
    @PostMapping("/{username}/stats/fetch")
    public ResponseEntity<StudentStatsDTO> fetchProblemStats(@PathVariable String username) {
        Student student = studentService.fetchAndUpdateProblemStats(username);
        return ResponseEntity.ok(mapper.toStatsDTO(student));
    }

    // 4. Fetch Recent Submissions -> Returns Recent DTO
    @PostMapping("/{username}/recent/fetch")
    public ResponseEntity<StudentRecentDTO> fetchRecentSubmissions(@PathVariable String username) {
        Student student = studentService.fetchAndUpdateRecentSubmissions(username);
        return ResponseEntity.ok(mapper.toRecentDTO(student));
    }

    // 5. Fetch Extended Profile -> Returns Extended DTO
    @PostMapping("/{username}/extended/fetch")
    public ResponseEntity<StudentExtendedDTO> fetchExtendedProfile(@PathVariable String username) {
        Student student = studentService.fetchAndUpdateExtendedProfile(username);
        return ResponseEntity.ok(mapper.toExtendedDTO(student));
    }

    // 6. Sync ALL Data at once -> Returns Extended DTO
    @PostMapping("/{username}/sync")
    public ResponseEntity<StudentExtendedDTO> syncAllData(@PathVariable String username) {
        // Fetch fresh data from LeetCode
        Student student = studentService.syncAllProfileData(username);

        // Build the DTO
        StudentExtendedDTO dto = mapper.toExtendedDTO(student);

        // <-- FIXED: Re-attach the classrooms before sending to React! -->
        List<Classroom> myClassrooms = classroomRepository.findByStudentIdsContaining(student.getId());
        dto.setClassrooms(myClassrooms);
        dto.setManuallyCompletedAssignments(student.getManuallyCompletedAssignments());

        return ResponseEntity.ok(dto);
    }

    // 7. Get All Students -> Returns a clean List of Summary DTOs
    @GetMapping
    public ResponseEntity<List<StudentSummaryDTO>> getAllStudents() {
        List<StudentSummaryDTO> summaries = studentRepository.findAll()
                .stream()
                .map(mapper::toSummaryDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/me/dashboard")
    public ResponseEntity<StudentExtendedDTO> getMyDashboard(@AuthenticationPrincipal Student currentStudent) {
        log.info("Student {} is viewing their dashboard", currentStudent.getEmail());

        Student freshStudentData = studentRepository.findById(currentStudent.getId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // 1. Build the base DTO
        StudentExtendedDTO dto = mapper.toExtendedDTO(freshStudentData);

        // 2. Fetch all classrooms this student is enrolled in
        List<Classroom> myClassrooms = classroomRepository.findByStudentIdsContaining(freshStudentData.getId());

        // 3. Attach the new data!
        dto.setClassrooms(myClassrooms);
        dto.setManuallyCompletedAssignments(freshStudentData.getManuallyCompletedAssignments());

        return ResponseEntity.ok(dto);
    }

    // URL: POST /api/students/me/classrooms/{classroomId}/assignments/{assignmentId}/validate
    @PostMapping("/me/classrooms/{classroomId}/assignments/{assignmentId}/validate")
    public ResponseEntity<StudentSummaryDTO> validateMySubmission(
            @AuthenticationPrincipal Student currentStudent,
            @PathVariable String classroomId,
            @PathVariable String assignmentId,
            @RequestBody SubmissionUrlRequest request) {

        // 1. Process the validation
        Student updatedStudent = classroomService.validateSubmissionAsStudent(
                currentStudent,
                classroomId,
                assignmentId,
                request.getUrl()
        );

        // 2. Fetch the classroom to get the assignment list for the Mapper
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new RuntimeException("Classroom not found"));

        // 3. Return the updated summary so the React frontend can instantly turn the "Pending" tag to "Completed"
        return ResponseEntity.ok(mapper.toSummaryDTO(updatedStudent, classroom.getAssignments()));
    }
}