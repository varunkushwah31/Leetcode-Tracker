package com.tracker.leetcode.tracker.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * Sends a nudge email to a student about a pending assignment.
     * This method runs asynchronously to avoid blocking the main thread.
     *
     * @param toEmail the email address to send to
     * @param studentName the name of the student
     * @param assignmentName the name of the assignment
     * @param className the name of the classroom
     */
    @Async // Send emails in the background so the mentor's dashboard doesn't freeze!
    public void sendNudgeEmail(String toEmail, String studentName, String assignmentName, String className) {
        try {
            // Validate input parameters
            if (toEmail == null || toEmail.trim().isEmpty()) {
                log.warn("Cannot send email: recipient email is null or empty");
                return;
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Reminder: Pending LeetCode Assignment for " + className);
            message.setText("Hi " + studentName + ",\n\n" +
                    "This is a gentle reminder from your mentor that you have a pending assignment: '" + assignmentName + "'.\n\n" +
                    "Please complete it on LeetCode and validate it on your MentorSync dashboard as soon as possible.\n\n" +
                    "Happy Coding!");

            mailSender.send(message);
            log.info("Nudge email successfully sent to {}", toEmail);
        } catch (MailException e) {
            log.error("Failed to send email to {}: Mail exception - {}", toEmail, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while sending email to {}: {}", toEmail, e.getMessage());
        }
    }
}

