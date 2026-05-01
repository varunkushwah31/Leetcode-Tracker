package com.tracker.leetcode.tracker.Controller;

import com.tracker.leetcode.tracker.DTO.ClassroomAnalyticsDTO;
import com.tracker.leetcode.tracker.DTO.ClassroomDashboardDTO;
import com.tracker.leetcode.tracker.DTO.SubmissionUrlRequest;
import com.tracker.leetcode.tracker.Models.Assignment;
import com.tracker.leetcode.tracker.Models.Classroom;
import com.tracker.leetcode.tracker.Models.Student;
import com.tracker.leetcode.tracker.Repository.ClassroomRepository;
import com.tracker.leetcode.tracker.Repository.StudentRepository;
import com.tracker.leetcode.tracker.Service.ClassroomService;
import com.tracker.leetcode.tracker.Service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

@Slf4j
@RestController
@RequestMapping("/api/classrooms")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"}, allowCredentials = "true")
public class ClassroomController {

    private final ClassroomService classroomService;
    private final ClassroomRepository classroomRepository;
    private final StudentRepository studentRepository;
    private final EmailService emailService;

    // 1. Create a Classroom
    // URL Example: POST /api/classrooms?mentorId=65f1a2b...&className=Data%20Structures
    @PostMapping
    public ResponseEntity<Classroom> createClassroom(
            @RequestParam String mentorId,
            @RequestParam String className) {
        return ResponseEntity.ok(classroomService.createClassroom(mentorId, className));
    }

    @PostMapping("/{classroomId}/students") // Removed the variable from path
    public ResponseEntity<Classroom> addStudentToClassroom(
            @PathVariable String classroomId,
            @RequestParam String leetcodeUsername) { // Changed to @RequestParam
        return ResponseEntity.ok(classroomService.addStudentToClassroom(classroomId, leetcodeUsername));
    }

    // 3. Get the Classroom Dashboard (With Sorting)
    // URL Examples:
    // GET /api/classrooms/{id}/dashboard
    // GET /api/classrooms/{id}/dashboard?sortBy=consistency
    // GET /api/classrooms/{id}/dashboard?sortBy=rating
    // GET /api/classrooms/{id}/dashboard?sortBy=solved
    @GetMapping("/{classroomId}/dashboard")
    public ResponseEntity<ClassroomDashboardDTO> getClassroomDashboard(
            @PathVariable String classroomId,
            @RequestParam(required = false, defaultValue = "name") String sortBy) {

        return ResponseEntity.ok(classroomService.getClassroomDashboard(classroomId, sortBy));
    }

    // 4. Assign a Question to the Classroom
    // URL: POST /api/classrooms/{id}/assignments
    /* Body Example:
       {
           "titleSlug": "two-sum",
           "startTimestamp": 1711000000,
           "endTimestamp": 1711604800
       }
    */
    @PostMapping("/{classroomId}/assignments")
    public ResponseEntity<Classroom> assignQuestion(
            @PathVariable String classroomId,
            @RequestBody Assignment assignment) {
        return ResponseEntity.ok(classroomService.assignQuestionToClassroom(classroomId, assignment));
    }

    // 5. Manually Validate Assignment URL
    // URL: POST /api/classrooms/{classroomId}/students/{username}/assignments/{assignmentId}/validate
    /* Body: { "url": "https://leetcode.com/problems/two-sum/submissions/123456789/" } */
    @PostMapping("/{classroomId}/students/{username}/assignments/{assignmentId}/validate")
    public ResponseEntity<Student> validateSubmission(
            @PathVariable String classroomId,
            @PathVariable String username,
            @PathVariable String assignmentId,
            @RequestBody SubmissionUrlRequest request) {

        return ResponseEntity.ok(classroomService.validateManualSubmission(classroomId, username, assignmentId, request.getUrl()));
    }

    // Autowire EmailService and StudentRepository at the top of your controller
    @PostMapping("/{classroomId}/students/{studentId}/nudge")
    public ResponseEntity<?> nudgeStudent(
            @PathVariable String classroomId,
            @PathVariable String studentId,
            @RequestParam String assignmentName) {

        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new RuntimeException("Classroom not found"));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        if (student.getEmail() != null) {
            emailService.sendNudgeEmail(student.getEmail(), student.getName(), assignmentName, classroom.getClassName());
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().body("Student does not have a valid email address.");
    }

    // 1. Endpoint for Bulk Import (Receives a file)
    @PostMapping("/{classroomId}/students/bulk")
    public ResponseEntity<List<String>> bulkAddStudents(
            @PathVariable String classroomId,
            @RequestParam("file") MultipartFile file) {

        List<String> failedAdds = classroomService.bulkAddStudents(classroomId, file);
        return ResponseEntity.ok(failedAdds);
    }

    // 2. Endpoint for CSV Export (Returns a downloadable file)
    @GetMapping(value = "/{classroomId}/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportClassroom(@PathVariable String classroomId) {
        String csvData = classroomService.generateClassroomCsv(classroomId);
        byte[] output = csvData.getBytes();

        HttpHeaders headers = new HttpHeaders();
        // This header tells the browser to download it as a file rather than displaying it as text
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"leaderboard_" + classroomId + ".csv\"");

        return new ResponseEntity<>(output, headers, HttpStatus.OK);
    }


    @GetMapping("/{classroomId}/analytics")
    public ResponseEntity<ClassroomAnalyticsDTO> getClassroomAnalytics(@PathVariable String classroomId) {
        return ResponseEntity.ok(classroomService.getClassroomAnalytics(classroomId));
    }

    // Add this method inside your ClassroomController class
    @DeleteMapping("/{classroomId}")
    public ResponseEntity<String> deleteClassroom(
            @PathVariable String classroomId,
            @RequestParam String mentorId) {

        classroomService.deleteClassroom(classroomId, mentorId);
        return ResponseEntity.ok("Classroom deleted successfully.");
    }

    @DeleteMapping("/{classroomId}/assignments/{assignmentId}")
    public ResponseEntity<Map<String, String>> deleteAssignment(
            @PathVariable String classroomId,
            @PathVariable String assignmentId,
            @RequestParam String mentorId) {

        classroomService.deleteAssignment(classroomId, assignmentId, mentorId);

        return ResponseEntity.ok(Map.of("message", "Assignment deleted successfully."));
    }
}