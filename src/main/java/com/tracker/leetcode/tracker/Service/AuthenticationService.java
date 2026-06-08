package com.tracker.leetcode.tracker.Service;

import com.tracker.leetcode.tracker.DTO.*;
import com.tracker.leetcode.tracker.Exception.DuplicateMentorException;
import com.tracker.leetcode.tracker.Exception.UserAuthenticationException;
import com.tracker.leetcode.tracker.Models.*;
import com.tracker.leetcode.tracker.Repository.MentorRepository;
import com.tracker.leetcode.tracker.Repository.StudentRepository;
import com.tracker.leetcode.tracker.Security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationService {

    private final StudentRepository studentRepository;
    private final MentorRepository mentorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final StudentService studentService;

    // 1. REGISTRATION LOGIC

    public AuthenticationResponse register(RegisterRequest request){
        log.info("Registering new Mentor with email: {}", request.email());

        if (mentorRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateMentorException("Email already in use.");
        }

        Mentor mentor = new Mentor();
        mentor.setName(request.name());
        mentor.setEmail(request.email());
        mentor.setPassword(passwordEncoder.encode(request.password()));
        mentor.setRole(Role.MENTOR);
        mentor.setProvider(AuthProvider.LOCAL);
        mentor.setEnabled(true);
        mentor.setEmailVerified(true); // Bypass Verification

        Mentor savedMentor = mentorRepository.save(mentor);
        return buildAuthResponse(savedMentor.getId(), savedMentor.getName(), savedMentor.getRole(), savedMentor);
    }

    public AuthenticationResponse registerStudent(StudentRegisterRequest request){
        log.info("Registering new student: {}", request.email());

        if (studentRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateMentorException("Student email already in use.");
        }

        Student student = new Student();
        student.setName(request.name());
        student.setEmail(request.email());
        student.setPassword(passwordEncoder.encode(request.password()));
        student.setLeetcodeUsername(request.leetcodeUsername());
        student.setRole(Role.STUDENT);
        student.setAuthProvider(AuthProvider.LOCAL);
        student.setEnabled(true);
        student.setEmailVerified(true); // Bypass Verification

        Student savedStudent = studentRepository.save(student);

        try {
            log.info("Auto-syncing LeetCode data for new student: {}", savedStudent.getLeetcodeUsername());
            studentService.syncAllProfileData(savedStudent.getLeetcodeUsername());
        } catch (Exception e) {
            log.warn("Failed to auto-sync LeetCode data for {}. Error: {}", savedStudent.getLeetcodeUsername(), e.getMessage());
        }

        return buildAuthResponse(savedStudent.getId(), savedStudent.getName(), savedStudent.getRole(), savedStudent);
    }

    // 2. LOGIN LOGIC

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (Exception e) {
            log.error("Authentication failed for user: {}", request.email());
            throw new UserAuthenticationException("Invalid email or password");
        }

        var studentOpt = studentRepository.findByEmail(request.email());
        if (studentOpt.isPresent()) {
            return buildAuthResponse(studentOpt.get().getId(), studentOpt.get().getName(), studentOpt.get().getRole(), studentOpt.get());
        }

        var mentorOpt = mentorRepository.findByEmail(request.email());
        if (mentorOpt.isPresent()) {
            return buildAuthResponse(mentorOpt.get().getId(), mentorOpt.get().getName(), mentorOpt.get().getRole(), mentorOpt.get());
        }

        throw new UserAuthenticationException("User not found after successful authentication");
    }

    // 3. REFRESH TOKEN LOGIC

    public AuthenticationResponse refreshToken(String requestRefreshToken){
        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getMentorId)
                .map(userId -> {
                    var studentOpt = studentRepository.findById(userId);
                    if (studentOpt.isPresent()) {
                        Student s = studentOpt.get();
                        return buildAuthResponse(s.getId(), s.getName(), s.getRole(), s);
                    }

                    var mentorOpt = mentorRepository.findById(userId);
                    if (mentorOpt.isPresent()) {
                        Mentor m = mentorOpt.get();
                        return buildAuthResponse(m.getId(), m.getName(), m.getRole(), m);
                    }

                    throw new UserAuthenticationException("User not found during refresh");
                })
                .orElseThrow(() -> new UserAuthenticationException("Refresh token is not in database!"));
    }

    // 4. SHARED HELPER METHOD

    private AuthenticationResponse buildAuthResponse(String id, String name, Role role, UserDetails userDetails) {
        String jwtToken = jwtService.generateToken(userDetails);

        refreshTokenService.deleteByMentorId(id);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(id);

        return AuthenticationResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken.getToken())
                .mentorId(id)
                .name(name)
                .role(role)
                .build();
    }
}