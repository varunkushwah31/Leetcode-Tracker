package com.tracker.leetcode.tracker.Controller;

import com.tracker.leetcode.tracker.DTO.MentorDTO;
import com.tracker.leetcode.tracker.Models.Mentor;
import com.tracker.leetcode.tracker.Service.MentorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/mentors")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"}, allowCredentials = "true")
public class MentorController {

    private final MentorService mentorService;

    // 1. Register / Create a Mentor
    // URL: POST http://localhost:8080/api/mentors
    /* Body:
       {
           "name": "Professor Smith",
           "email": "smith@school.edu",
           "password": "securepassword123"
       }
    */
    @PostMapping
    public ResponseEntity<MentorDTO> createMentor(@RequestBody Mentor mentor) {
        return ResponseEntity.ok(mentorService.createMentor(mentor));
    }

    // 2. Get a Mentor Profile
    // URL: GET http://localhost:8080/api/mentors/{id}
    @GetMapping("/{id}")
    public ResponseEntity<MentorDTO> getMentor(@PathVariable String id) {
        return ResponseEntity.ok(mentorService.getMentorById(id));
    }

    // 3. Get All Mentors (Admin view)
    // URL: GET http://localhost:8080/api/mentors
    @GetMapping
    public ResponseEntity<List<MentorDTO>> getAllMentors() {
        return ResponseEntity.ok(mentorService.getAllMentors());
    }
}