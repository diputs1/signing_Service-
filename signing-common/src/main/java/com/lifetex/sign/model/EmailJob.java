package com.lifetex.sign.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EmailJob {
    private String email;
    private String otp;
    private int retry;
    private long timestamp;
}
