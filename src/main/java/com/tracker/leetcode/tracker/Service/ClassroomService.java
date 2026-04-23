package com.tracker.leetcode.tracker.Service;

import com.tracker.leetcode.tracker.DTO.ClassroomAnalyticsDTO;
import com.tracker.leetcode.tracker.DTO.ClassroomDashboardDTO;
import com.tracker.leetcode.tracker.DTO.StudentSummaryDTO;
import com.tracker.leetcode.tracker.Exception.*;
import com.tracker.leetcode.tracker.Mapper.StudentMapper;
import com.tracker.leetcode.tracker.Models.*;
import com.tracker.leetcode.tracker.Repository.ClassroomRepository;
import com.tracker.leetcode.tracker.Repository.MentorRepository;
import com.tracker.leetcode.tracker.Repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final MentorRepository mentorRepository;
    private final StudentRepository studentRepository;
    private final StudentMapper studentMapper;
    private final LeetCodeApiClient leetCodeApiClient;
    private final SimpMessagingTemplate messagingTemplate;

    @Lazy
    @Autowired
    private ClassroomService self;

    // 1. Create Classroom
    @CacheEvict(value = {"classroom-dashboard", "classroom-analytics", "mentors-all", "mentor"}, allEntries = true)
    public Classroom createClassroom(String mentorId, String className) {
        log.info("Creating classroom {} for mentor ID: {}", className, mentorId);
        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new MentorNotFoundException("Mentor not found with ID: " + mentorId));

        Classroom classroom = new Classroom();
        classroom.setClassName(className);
        classroom.setMentorId(mentorId);
        Classroom savedClassroom = classroomRepository.save(classroom);

        mentor.getClassroomIds().add(savedClassroom.getId());
        mentorRepository.save(mentor);

        return savedClassroom;
    }

    // 2. Add Student
    @CacheEvict(value = {"classroom-dashboard", "classroom-analytics"}, allEntries = true)
    public Classroom addStudentToClassroom(String classroomId, String leetcodeUsername) {
        log.info("Adding student {} to classroom ID: {}", leetcodeUsername, classroomId);
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ClassroomNotFoundException("Classroom not found with ID: " + classroomId));

        Student student = studentRepository.findByLeetcodeUsername(leetcodeUsername)
                .orElseThrow(() -> new StudentNotFoundException("Student not found in database. Please add them first."));

        // Prevent duplicate enrollments in the same class
        if (classroom.getStudentIds().contains(student.getId())) {
            throw new StudentAlreadyEnrolledException("Student is already enrolled in this classroom.");
        }

        classroom.getStudentIds().add(student.getId());
        return classroomRepository.save(classroom);
    }

    // 3. Get Dashboard (with Sorting & Fetching restored!)
    @Cacheable(value = "classroom-dashboard", key = "#classroomId + ':' + #sortBy")
    public ClassroomDashboardDTO getClassroomDashboard(String classroomId, String sortBy) {
        log.info("Fetching dashboard data for classroom ID: {} sorted by: {}", classroomId, sortBy);

        // 1. Fetch the data we need from MongoDB
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ClassroomNotFoundException("Classroom not found with ID: " + classroomId));

        Mentor mentor = mentorRepository.findById(classroom.getMentorId())
                .orElseThrow(() -> new MentorNotFoundException("Mentor not found for this classroom."));

        List<Student> enrolledStudents = studentRepository.findAllById(classroom.getStudentIds());

        // 2. Map to DTOs and pass the classroom assignments for evaluation
        List<StudentSummaryDTO> studentSummaries = enrolledStudents.stream()
                .map(student -> studentMapper.toSummaryDTO(student, classroom.getAssignments()))
                .collect(Collectors.toList());

        // 3. Apply Dynamic Sorting
        if (sortBy != null && !sortBy.isBlank()) {
            switch (sortBy.toLowerCase()) {
                case "consistency":
                    studentSummaries.sort((s1, s2) -> Integer.compare(s2.getConsistencyStreak(), s1.getConsistencyStreak()));
                    break;
                case "rating":
                    studentSummaries.sort((s1, s2) -> Double.compare(s2.getCurrentContestRating(), s1.getCurrentContestRating()));
                    break;
                case "solved":
                    studentSummaries.sort((s1, s2) -> Integer.compare(s2.getTotalSolved(), s1.getTotalSolved()));
                    break;
                case "pending": // Sort by students who are falling behind!
                    studentSummaries.sort((s1, s2) -> Integer.compare(s2.getPendingAssignments(), s1.getPendingAssignments()));
                    break;
                case "completed": // Sort by students who finished the most assignments!
                    studentSummaries.sort((s1, s2) -> Integer.compare(s2.getCompletedAssignments(), s1.getCompletedAssignments()));
                    break;
                case "name":
                    studentSummaries.sort((s1, s2) -> s1.getName().compareToIgnoreCase(s2.getName()));
                    break;
                default:
                    log.warn("Unknown sort parameter: {}. Defaulting to unsorted.", sortBy);
            }
        }

        // 4. Return the fully built Dashboard
        return ClassroomDashboardDTO.builder()
                .classroomId(classroom.getId())
                .className(classroom.getClassName())
                .mentorName(mentor.getName())
                .enrolledStudents(studentSummaries)
                .build();
    }

    // 4. Assign a LeetCode problem to the classroom
    @CacheEvict(value = {"classroom-dashboard", "classroom-analytics"}, allEntries = true)
    public Classroom assignQuestionToClassroom(String classroomId, Assignment assignment) {
        log.info("Assigning question '{}' to classroom ID: {}", assignment.getTitleSlug(), classroomId);

        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ClassroomNotFoundException("Classroom not found with ID: " + classroomId));

        // --- Ensure ID and questionLink are explicitly set before saving ---
        if (assignment.getId() == null) {
            assignment.setId(UUID.randomUUID().toString());
        }

        if (assignment.getQuestionLink() == null && assignment.getTitleSlug() != null) {
            assignment.setQuestionLink("https://leetcode.com/problems/" + assignment.getTitleSlug() + "/");
        }

        // Defensive programming: initialize assignments array if null
        initializeAssignmentsIfNull(classroom);

        classroom.getAssignments().add(assignment);
        return classroomRepository.save(classroom);
    }

    // Helper method to extract submission ID from URL
    private String extractSubmissionIdFromUrl(String submissionUrl) {
        Pattern pattern = Pattern.compile("submissions/(?:detail/)?(\\d+)");
        Matcher matcher = pattern.matcher(submissionUrl);
        if (!matcher.find()) {
            throw new ValidationFailedException("Invalid LeetCode URL. It must contain the submission ID.");
        }
        return matcher.group(1);
    }

    // Helper method to initialize assignments list if null
    private void initializeAssignmentsIfNull(Classroom classroom) {
        if (classroom.getAssignments() == null) {
            classroom.setAssignments(new ArrayList<>());
        }
    }

    public Student validateManualSubmission(String classroomId, String leetcodeUsername, String assignmentId, String submissionUrl) {
        log.info("Validating manual submission for {} on assignment {}", leetcodeUsername, assignmentId);

        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ClassroomNotFoundException("Classroom not found"));

        Assignment assignment = classroom.getAssignments().stream()
                .filter(a -> a.getId().equals(assignmentId))
                .findFirst()
                .orElseThrow(() -> new AssignmentNotFoundException("Assignment not found in this classroom."));

        Student student = studentRepository.findByLeetcodeUsername(leetcodeUsername)
                .orElseThrow(() -> new StudentNotFoundException("Student not found."));

        // If they already validated it, skip the network call and return
        if (student.getManuallyCompletedAssignments().contains(assignmentId)) {
            log.info("Assignment {} already validated for {}", assignmentId, leetcodeUsername);
            return student;
        }

        String submissionId = extractSubmissionIdFromUrl(submissionUrl);

        boolean isValid = leetCodeApiClient.verifySubmission(submissionId, leetcodeUsername, assignment.getTitleSlug());

        if (!isValid) {
            throw new ValidationFailedException("Validation Failed! Ensure the submission is 'Accepted', belongs to you, and is the correct problem.");
        }

        // Success! Permanently save it to the student's profile
        student.getManuallyCompletedAssignments().add(assignmentId);
        return studentRepository.save(student);
    }

    public Student validateSubmissionAsStudent(Student student, String classroomId, String assignmentId, String submissionUrl) {
        log.info("Student {} is self-validating assignment {}", student.getLeetcodeUsername(), assignmentId);

        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ClassroomNotFoundException("Classroom not Found."));

        if (!classroom.getStudentIds().contains(student.getId())) {
            throw new RuntimeException("Security Exception: You are not enrolled in this classroom.");
        }

        Assignment assignment = classroom.getAssignments().stream()
                .filter(a -> a.getId().equals(assignmentId))
                .findFirst()
                .orElseThrow(() -> new AssignmentNotFoundException("Assignment not found in this classroom."));

        // If they already validated it, skip the network call
        if (student.getManuallyCompletedAssignments().contains(assignmentId)) {
            log.info("Assignment {} already validated for {}", assignmentId, student.getLeetcodeUsername());
            return student;
        }

        String submissionId = extractSubmissionIdFromUrl(submissionUrl);

        // Verify with LeetCode API (using the student's exact username)
        boolean isValid = leetCodeApiClient.verifySubmission(submissionId, student.getLeetcodeUsername(), assignment.getTitleSlug());

        if (!isValid) {
            throw new ValidationFailedException("Validation Failed! Ensure the submission is 'Accepted', belongs to you, and is the correct problem.");
        }



        // Success! Save it to the student's profile
        student.getManuallyCompletedAssignments().add(assignmentId);
        Student savedStudent = studentRepository.save(student);

        // Broadcast the "Ping" to anyone listening to this classroom
        log.info("Broadcasting leaderboard update for classroom: {}", classroomId);
        messagingTemplate.convertAndSend(
                "/topic/classrooms/" + classroomId,
                (Object) Map.of("action", "UPDATE", "message", "Leaderboard changed!") // <-- Added (Object) cast
        );


        return savedStudent;

    }


    // 1. BULK IMPORT: Read CSV and add students
    public List<String> bulkAddStudents(String classroomId, MultipartFile file) {
        List<String> failedUsernames = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String leetcodeUsername;
            // Read the CSV line by line
            while ((leetcodeUsername = reader.readLine()) != null) {
                leetcodeUsername = leetcodeUsername.trim();
                // Skip empty lines or CSV header if it exists
                if (leetcodeUsername.isEmpty() || leetcodeUsername.equalsIgnoreCase("username") || leetcodeUsername.equalsIgnoreCase("leetcode_username")) {
                    continue;
                }

                try {
                    // FIXED: Changed to the correct method name!
                    addStudentToClassroom(classroomId, leetcodeUsername);
                } catch (Exception e) {
                    log.warn("Failed to bulk add student: {}", leetcodeUsername);
                    failedUsernames.add(leetcodeUsername); // Track failures
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV file.");
        }

        return failedUsernames;
    }

    // 2. EXPORT: Generate CSV string of the leaderboard
    //
    public String generateClassroomCsv(String classroomId) {
        // Reuse your existing dashboard logic to get sorted, fully-calculated stats!
        ClassroomDashboardDTO dashboard = self.getClassroomDashboard(classroomId, "solved");

        StringBuilder csv = new StringBuilder();
        // Add the standard CSV Header row
        csv.append("Rank,Name,LeetCode Username,Daily Streak,Total Solved,Contest Rating,Done Assignments,Pending Assignments\n");

        int rank = 1;
        for (StudentSummaryDTO s : dashboard.getEnrolledStudents()) {
            // FIXED: Removed the != null checks since int and double can never be null in Java
            csv.append(rank++).append(",")
                    .append("\"").append(s.getName()).append("\",")
                    .append(s.getLeetcodeUsername()).append(",")
                    .append(s.getConsistencyStreak()).append(",")
                    .append(s.getTotalSolved()).append(",")
                    .append(Math.round(s.getCurrentContestRating())).append(",")
                    .append(s.getCompletedAssignments()).append(",")
                    .append(s.getPendingAssignments()).append("\n");
        }
        return csv.toString();
    }


    public void assignQuestion(String classroomId, String titleSlug, long startTimestamp, long endTimestamp) {
        log.info("Assigning question {} to classroom ID: {} with deadline from {} to {}",
                titleSlug, classroomId, startTimestamp, endTimestamp);


        // 1. Find the classroom
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ClassroomNotFoundException("Classroom not found with ID: " + classroomId));

        // 2. Build the Assignment object
        Assignment assignment = new Assignment();
        assignment.setId(UUID.randomUUID().toString()); // Generate a unique ID for this specific homework
        assignment.setTitleSlug(titleSlug);
        assignment.setQuestionLink("https://leetcode.com/problems/" + titleSlug + "/");
        assignment.setStartTimestamp(startTimestamp);
        assignment.setEndTimestamp(endTimestamp);

        // 3. Add it to the classroom's assignment list (initialize if null)
        initializeAssignmentsIfNull(classroom);
        classroom.getAssignments().add(assignment);

        // 4. Save the updated classroom back to MongoDB
        classroomRepository.save(classroom);
        log.info("Successfully assigned {} to classroom {}", titleSlug, classroom.getClassName());
    }

    // NEW: Get Classroom Analytics
    @Cacheable(value = "classroom-analytics", key = "#classroomId")
    public ClassroomAnalyticsDTO getClassroomAnalytics(String classroomId) {
        log.info("Generating Analytics for Classroom ID: {}", classroomId);

        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ClassroomNotFoundException("Classroom not found"));

        List<Student> students = studentRepository.findAllById(classroom.getStudentIds());
        int totalStudents = students.size();

        if (totalStudents == 0) {
            return ClassroomAnalyticsDTO.builder()
                    .classroomId(classroomId).className(classroom.getClassName()).totalStudents(0)
                    .build();
        }

        int totalSolved = 0, totalEasy = 0, totalMed = 0, totalHard = 0, activeCount = 0;
        java.util.Map<String, Integer> aggregatedSkills = new java.util.HashMap<>();

        long oneWeekAgo = System.currentTimeMillis() / 1000 - (7 * 86400);

        for (Student s : students) {
            // 1. Calculate Difficulties
            if (s.getProblemStats() != null) {
                for (var stat : s.getProblemStats()) {
                    switch (stat.getDifficulty().toLowerCase()) {
                        case "all" -> totalSolved += stat.getCount();
                        case "easy" -> totalEasy += stat.getCount();
                        case "medium" -> totalMed += stat.getCount();
                        case "hard" -> totalHard += stat.getCount();
                    }
                }
            }

            // 2. Check Engagement (Active in last 7 days)
            boolean isActive = s.getRecentSubmissions() != null && s.getRecentSubmissions().stream()
                    .anyMatch(sub -> sub.getTimestamp() >= oneWeekAgo);
            if (isActive) activeCount++;

            // 3. Aggregate Skills
            if (s.getSkills() != null) {
                for (var skill : s.getSkills()) {
                    aggregatedSkills.merge(skill.getTagName(), skill.getProblemsSolved(), Integer::sum);
                }
            }
        }

        // Sort skills by total solved across the class
        List<SkillStat> sortedSkills = aggregatedSkills.entrySet().stream()
                .map(e -> new SkillStat(e.getKey(), e.getValue()))
                .sorted((a, b) -> Integer.compare(b.getProblemsSolved(), a.getProblemsSolved()))
                .toList();

        // Top 5 Strengths
        List<SkillStat> topStrengths = sortedSkills.stream().limit(5).toList();

        // Top 5 Weaknesses (Topics they have barely touched, but at least 1 person tried)
        List<SkillStat> criticalWeaknesses = sortedSkills.stream()
                .filter(s -> s.getProblemsSolved() > 0) // Ignore completely untouched
                .skip(Math.max(0, sortedSkills.size() - 5)) // Get the bottom 5
                .sorted(Comparator.comparingInt(SkillStat::getProblemsSolved)) // Sort ascending for weaknesses
                .toList();

        return ClassroomAnalyticsDTO.builder()
                .classroomId(classroomId)
                .className(classroom.getClassName())
                .totalStudents(totalStudents)
                .averageTotalSolved(totalSolved / totalStudents)
                .averageEasy(totalEasy / totalStudents)
                .averageMedium(totalMed / totalStudents)
                .averageHard(totalHard / totalStudents)
                .activeStudentsThisWeek(activeCount)
                .classEngagementScore((activeCount * 100.0) / totalStudents)
                .topStrengths(topStrengths)
                .criticalWeaknesses(criticalWeaknesses)
                .build();
    }

    @CacheEvict(value = {"classroom-dashboard", "classroom-analytics", "mentors-all", "mentor"}, allEntries = true)
    public void deleteClassroom(String classroomId, String mentorId) {
        log.info("Attempting to delete classroom ID: {} by mentor ID: {}", classroomId, mentorId);

        // 1. Find the classroom
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ClassroomNotFoundException("Classroom not found with ID: " + classroomId));

        // 2. Security Check: Ensure the mentor deleting it actually owns it
        if (!classroom.getMentorId().equals(mentorId)) {
            log.warn("Security Alert: Mentor {} attempted to delete classroom {} which they do not own.", mentorId, classroomId);
            throw new AccessDeniedException("You do not have permission to delete this classroom.");
        }

        // 3. Remove the classroom ID from the mentor's profile
        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new MentorNotFoundException("Mentor not found with ID: " + mentorId));

        mentor.getClassroomIds().remove(classroomId);
        mentorRepository.save(mentor);

        // 4. Delete the actual classroom document
        classroomRepository.delete(classroom);
        log.info("Successfully deleted classroom ID: {}", classroomId);
    }
}