package com.lifetex.sign.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SigningSession {
    private String userName;
    private Instant issuedAt;
    private Instant expiresAt;

}
