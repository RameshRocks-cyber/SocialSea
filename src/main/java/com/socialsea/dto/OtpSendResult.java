package com.socialsea.dto;

public class OtpSendResult {
    private final String otp;
    private final boolean deliveryFailed;
    private final String failureReason;

    public OtpSendResult(String otp, boolean deliveryFailed, String failureReason) {
        this.otp = otp;
        this.deliveryFailed = deliveryFailed;
        this.failureReason = failureReason;
    }

    public String getOtp() {
        return otp;
    }

    public boolean isDeliveryFailed() {
        return deliveryFailed;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
