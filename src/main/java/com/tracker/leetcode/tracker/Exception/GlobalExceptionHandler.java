package com.tracker.leetcode.tracker.Exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Updated helper method utilizing the Builder pattern and the request path
    private ResponseEntity<ApiErrorResponse> buildErrorResponse(HttpStatus status, String error, String message, String path) {
        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .path(path)
                .build();
        // Timestamp is handled automatically by your @Builder.Default annotation!

        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Failed login attempt: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password.", request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", "You do not have permission to access this resource.", request.getRequestURI());
    }

    @ExceptionHandler({
            StudentNotFoundException.class,
            MentorNotFoundException.class,
            ClassroomNotFoundException.class,
            AssignmentNotFoundException.class
    })
    public ResponseEntity<ApiErrorResponse> handleNotFoundExceptions(RuntimeException ex, HttpServletRequest request) {
        log.warn("Resource Not Found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({
            DuplicateMentorException.class,
            DuplicateStudentException.class,
            StudentAlreadyEnrolledException.class
    })
    public ResponseEntity<ApiErrorResponse> handleConflicts(RuntimeException ex, HttpServletRequest request) {
        log.warn("Conflict Error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationFailed(ValidationFailedException ex, HttpServletRequest request) {
        log.warn("Validation Failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(LeetCodeApiException.class)
    public ResponseEntity<ApiErrorResponse> handleLeetCodeApiError(LeetCodeApiException ex, HttpServletRequest request) {
        log.warn("External API Error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_GATEWAY, "Bad Gateway", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<ApiErrorResponse> handleTokenRefreshException(TokenRefreshException ex, HttpServletRequest request){
        log.warn("Refresh Token Error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal Argument provided: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        StringBuilder errorMessage = new StringBuilder("Invalid input data: ");
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errorMessage.append(error.getField()).append(" ").append(error.getDefaultMessage()).append("; ")
        );

        log.warn("DTO Validation Failed: {}", errorMessage);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", errorMessage.toString(), request.getRequestURI());
    }

    // Catch-All must be at the bottom
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("An Unexpected error occurred: ", ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An Unexpected Error occurred. Please Contact Support.",
                request.getRequestURI()
        );
    }
}