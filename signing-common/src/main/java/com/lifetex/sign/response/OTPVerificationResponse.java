package com.lifetex.sign.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OTPVerificationResponse extends HttpResponse<OTPVerificationResponse> {
    private String signingToken;
    private Integer tokenExpiryMinutes;
    private Integer remainingAttempts;
    private int remainingLockoutMinutes;
}