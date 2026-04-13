package com.lifetex.sign.response;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OTPResponse extends HttpResponse<OTPResponse> {
    private Integer remainingRequests;
    private Integer expiryMinutes;
    private int remainingLockoutMinutes;
}
