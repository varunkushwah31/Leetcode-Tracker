package com.tracker.leetcode.tracker.Service;

import com.tracker.leetcode.tracker.DTO.MentorDTO;
import com.tracker.leetcode.tracker.Exception.DuplicateMentorException;
import com.tracker.leetcode.tracker.Exception.MentorNotFoundException;
import com.tracker.leetcode.tracker.Models.Mentor;
import com.tracker.leetcode.tracker.Repository.MentorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MentorService {

    private final MentorRepository mentorRepository;

    // Helper method to map Model to DTO
    private MentorDTO mapToDTO(Mentor mentor) {
        return MentorDTO.builder()
                .id(mentor.getId())
                .name(mentor.getName())
                .email(mentor.getEmail())
                .classroomIds(mentor.getClassroomIds())
                .build();
    }

    // 1. Create a new Mentor
    @CacheEvict(value = "mentors-all", allEntries = true)
    public MentorDTO createMentor(Mentor mentor) {
        log.info("Attempting to create mentor with email: {}", mentor.getEmail());

        Optional<Mentor> existingMentor = mentorRepository.findByEmail(mentor.getEmail());
        if (existingMentor.isPresent()) {
            throw new DuplicateMentorException("A mentor with the email '" + mentor.getEmail() + "' already exists.");
        }

        Mentor savedMentor = mentorRepository.save(mentor);
        log.info("Successfully created mentor: {}", savedMentor.getName());
        return mapToDTO(savedMentor);
    }

    // 2. Get a specific Mentor by ID
    @Cacheable(value = "mentor", key = "#mentorId")
    public MentorDTO getMentorById(String mentorId) {
        log.info("Fetching mentor with ID: {}", mentorId);
        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new MentorNotFoundException("Mentor not found with ID: " + mentorId));
        return mapToDTO(mentor);
    }

    // 3. Get all Mentors (Useful for an Admin panel)
    @Cacheable(value = "mentors-all")
    public List<MentorDTO> getAllMentors() {
        log.info("Fetching all mentors");
        return mentorRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
}