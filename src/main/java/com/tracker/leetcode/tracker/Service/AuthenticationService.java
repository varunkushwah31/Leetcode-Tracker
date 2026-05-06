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
    private final EmailService emailService;

    // --- HELPER: Generate 6-digit OTP ---
    private String generateOtp() {
        return String.format("%06d", new java.util.Random().nextInt(999999));
    }

    // 1. REGISTRATION LOGIC

    public String register(RegisterRequest request){
        log.info("Registering new Mentor with email: {}", request.email());

        var existingMentor = mentorRepository.findByEmail(request.email());
        if (existingMentor.isPresent()) {
            if (existingMentor.get().isEmailVerified()) {
                throw new DuplicateMentorException("Email already in use.");
            } else {
                log.info("Deleting abandoned, unverified Mentor account for: {}", request.email());
                mentorRepository.delete(existingMentor.get());
            }
        }

        Mentor mentor = new Mentor();
        mentor.setName(request.name());
        mentor.setEmail(request.email());
        mentor.setPassword(passwordEncoder.encode(request.password()));
        mentor.setRole(Role.MENTOR);
        mentor.setProvider(AuthProvider.LOCAL);
        mentor.setEnabled(true);

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

        var existingStudent = studentRepository.findByEmail(request.email());
        if (existingStudent.isPresent()) {
            if (existingStudent.get().isEmailVerified()) {
                throw new DuplicateMentorException("Student email already in use.");
            } else {
                log.info("Deleting abandoned, unverified Student account for: {}", request.email());
                studentRepository.delete(existingStudent.get());
            }
        }

        Student student = new Student();
        student.setName(request.name());
        student.setEmail(request.email());
        student.setPassword(passwordEncoder.encode(request.password()));
        student.setLeetcodeUsername(request.leetcodeUsername());
        student.setRole(Role.STUDENT);
        student.setAuthProvider(AuthProvider.LOCAL);
        student.setEnabled(true);

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
        var studentOpt = studentRepository.findByEmail(request.getEmail());
        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            validateOtp(student.getOtpExpiryTime(), student.getOtp(), request.getOtp());

            student.setEmailVerified(true);
            student.setOtp(null);
            student.setOtpExpiryTime(0);
            studentRepository.save(student);

            return buildAuthResponse(student.getId(), student.getName(), student.getRole(), student);
        }

        var mentorOpt = mentorRepository.findByEmail(request.getEmail());
        if (mentorOpt.isPresent()) {
            Mentor mentor = mentorOpt.get();
            validateOtp(mentor.getOtpExpiryTime(), mentor.getOtp(), request.getOtp());

            mentor.setEmailVerified(true);
            mentor.setOtp(null);
            mentor.setOtpExpiryTime(0);
            mentorRepository.save(mentor);

            return buildAuthResponse(mentor.getId(), mentor.getName(), mentor.getRole(), mentor);
        }

        throw new UserAuthenticationException("User not found");
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
            if (!studentOpt.get().isEmailVerified()) throw new UserAuthenticationException("Please verify your email before logging in.");
            return buildAuthResponse(studentOpt.get().getId(), studentOpt.get().getName(), studentOpt.get().getRole(), studentOpt.get());
        }

        var mentorOpt = mentorRepository.findByEmail(request.email());
        if (mentorOpt.isPresent()) {
            if (!mentorOpt.get().isEmailVerified()) throw new UserAuthenticationException("Please verify your email before logging in.");
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

    // --------------------------------------------------------
    // 4. SHARED DRY HELPER METHODS (Fixes the duplication warnings)
    // --------------------------------------------------------

    private void validateOtp(long expiryTime, String storedOtp, String requestOtp) {
        if (expiryTime < System.currentTimeMillis()) {
            throw new UserAuthenticationException("OTP has expired");
        }
        if (storedOtp == null || !storedOtp.equals(requestOtp)) {
            throw new UserAuthenticationException("Invalid OTP");
        }
    }

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