package com.securefilesharing.dto;

public class SigninOtpVerifyRequest {
    private Long otpRequestId;
    private String otp;

    public Long getOtpRequestId() {
        return otpRequestId;
    }

    public void setOtpRequestId(Long otpRequestId) {
        this.otpRequestId = otpRequestId;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}
