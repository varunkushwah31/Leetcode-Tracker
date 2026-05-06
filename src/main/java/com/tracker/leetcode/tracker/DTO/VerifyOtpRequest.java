package com.tracker.leetcode.tracker.DTO;

import lombok.Data;

@Data
public class VerifyOtpRequest {
    private String email;
    private String otp;
}