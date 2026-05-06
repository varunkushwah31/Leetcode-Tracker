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
    private final EmailService emailService;

    // --- HELPER: Generate 6-digit OTP ---
    private String generateOtp() {
        return String.format("%06d", new java.util.Random().nextInt(999999));
    }

    // 1. REGISTRATION LOGIC (Now returns a String message instead of tokens)

    public String register(RegisterRequest request){
        log.info("Registering new Mentor with email: {}", request.email());
        if (mentorRepository.findByEmail(request.email()).isPresent()){
            throw new DuplicateMentorException("Email already in use.");
        }
        Mentor mentor = new Mentor();
        mentor.setName(request.name());
        mentor.setEmail(request.email());
        mentor.setPassword(passwordEncoder.encode(request.password()));
        mentor.setRole(Role.MENTOR);
        mentor.setProvider(AuthProvider.LOCAL);
        mentor.setEnabled(true);

        // NEW: OTP Logic
        String otp = generateOtp();
        mentor.setOtp(otp);
        mentor.setOtpExpiryTime(System.currentTimeMillis() + (10 * 60 * 1000)); // 10 mins
        mentor.setEmailVerified(false);

        mentorRepository.save(mentor);
        emailService.sendVerificationOtp(mentor.getEmail(), otp, mentor.getName());

        return "Registration successful. Please check your email for the OTP.";
    }

    public String registerStudent(StudentRegisterRequest request){
        log.info("Registering new student: {}", request.email());
        if (studentRepository.findByEmail(request.email()).isPresent()){
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

        // NEW: OTP Logic
        String otp = generateOtp();
        student.setOtp(otp);
        student.setOtpExpiryTime(System.currentTimeMillis() + (10 * 60 * 1000)); // 10 mins
        student.setEmailVerified(false);

        Student savedStudent = studentRepository.save(student);

        try {
            log.info("Auto-syncing LeetCode data for new student: {}", savedStudent.getLeetcodeUsername());
            studentService.syncAllProfileData(savedStudent.getLeetcodeUsername());
        } catch (Exception e) {
            log.warn("Failed to auto-sync LeetCode data for {}. Error: {}", savedStudent.getLeetcodeUsername(), e.getMessage());
        }

        emailService.sendVerificationOtp(student.getEmail(), otp, student.getName());

        return "Registration successful. Please check your email for the OTP.";
    }

    // NEW: VERIFY OTP LOGIC
    public AuthenticationResponse verifyEmail(VerifyOtpRequest request) {
        // Check Students first
        var studentOpt = studentRepository.findByEmail(request.getEmail());
        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            if (student.getOtpExpiryTime() < System.currentTimeMillis()) throw new UserAuthenticationException("OTP has expired");
            if (!student.getOtp().equals(request.getOtp())) throw new UserAuthenticationException("Invalid OTP");

            student.setEmailVerified(true);
            student.setOtp(null);
            student.setOtpExpiryTime(0);
            studentRepository.save(student);
            return generateAuthResponseForStudent(student);
        }

        // Check Mentors
        var mentorOpt = mentorRepository.findByEmail(request.getEmail());
        if (mentorOpt.isPresent()) {
            Mentor mentor = mentorOpt.get();
            if (mentor.getOtpExpiryTime() < System.currentTimeMillis()) throw new UserAuthenticationException("OTP has expired");
            if (!mentor.getOtp().equals(request.getOtp())) throw new UserAuthenticationException("Invalid OTP");

            mentor.setEmailVerified(true);
            mentor.setOtp(null);
            mentor.setOtpExpiryTime(0);
            mentorRepository.save(mentor);
            return generateAuthResponseForMentor(mentor);
        }

        throw new UserAuthenticationException("User not found");
    }

    // 2. LOGIN LOGIC (Now blocks unverified users)

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (Exception e) {
            log.error("Authentication failed for user: {}", request.email());
            throw new UserAuthenticationException("Invalid email or password");
        }

        var studentOpt = studentRepository.findByEmail(request.email());
        if (studentOpt.isPresent()) {
            if (!studentOpt.get().isEmailVerified()) throw new UserAuthenticationException("Please verify your email before logging in.");
            return generateAuthResponseForStudent(studentOpt.get());
        }

        var mentorOpt = mentorRepository.findByEmail(request.email());
        if (mentorOpt.isPresent()) {
            if (!mentorOpt.get().isEmailVerified()) throw new UserAuthenticationException("Please verify your email before logging in.");
            return generateAuthResponseForMentor(mentorOpt.get());
        }

        throw new UserAuthenticationException("User not found after successful authentication");
    }

    // 3. REFRESH TOKEN LOGIC

    public AuthenticationResponse refreshToken(String requestRefreshToken){
        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getMentorId) // Fetches the generic User ID attached to the token
                .map(userId -> {

                    // Is this ID a Student?
                    var studentOpt = studentRepository.findById(userId);
                    if (studentOpt.isPresent()) {
                        Student student = studentOpt.get();
                        String jwtToken = jwtService.generateToken(student);
                        return AuthenticationResponse.builder()
                                .accessToken(jwtToken)
                                .refreshToken(requestRefreshToken)
                                .mentorId(student.getId())
                                .name(student.getName())
                                .role(student.getRole()) // <-- ADDED ROLE HERE
                                .build();
                    }

                    // Is this ID a Mentor?
                    var mentorOpt = mentorRepository.findById(userId);
                    if (mentorOpt.isPresent()) {
                        Mentor mentor = mentorOpt.get();
                        String jwtToken = jwtService.generateToken(mentor);
                        return AuthenticationResponse.builder()
                                .accessToken(jwtToken)
                                .refreshToken(requestRefreshToken)
                                .mentorId(mentor.getId())
                                .name(mentor.getName())
                                .role(mentor.getRole()) // <-- ADDED ROLE HERE
                                .build();
                    }

                    throw new UserAuthenticationException("User not found during refresh");
                })
                .orElseThrow(() -> new UserAuthenticationException("Refresh token is not in database!"));
    }

    // 4. DRY HELPER METHODS

    private AuthenticationResponse generateAuthResponseForStudent(Student student) {
        String jwtToken = jwtService.generateToken(student);

        refreshTokenService.deleteByMentorId(student.getId());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(student.getId());

        return AuthenticationResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken.getToken())
                .mentorId(student.getId())
                .name(student.getName())
                .role(student.getRole()) // <-- ADDED ROLE HERE
                .build();
    }

    private AuthenticationResponse generateAuthResponseForMentor(Mentor mentor) {
        String jwtToken = jwtService.generateToken(mentor);

        refreshTokenService.deleteByMentorId(mentor.getId());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(mentor.getId());

        return AuthenticationResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken.getToken())
                .mentorId(mentor.getId())
                .name(mentor.getName())
                .role(mentor.getRole()) // <-- ADDED ROLE HERE
                .build();
    }
}