package com.securefilesharing.dto;

public class OtpRequestResponse {
    private String message;
    private Long otpRequestId;

    public OtpRequestResponse() {
    }

    public OtpRequestResponse(String message, Long otpRequestId) {
        this.message = message;
        this.otpRequestId = otpRequestId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getOtpRequestId() {
        return otpRequestId;
    }

    public void setOtpRequestId(Long otpRequestId) {
        this.otpRequestId = otpRequestId;
    }
}
