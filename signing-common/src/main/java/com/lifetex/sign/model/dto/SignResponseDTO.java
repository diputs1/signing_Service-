package com.lifetex.sign.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SignResponseDTO {
    private String message;

    private String signatureFormat;

    private String signatureLevel;

    private String certificateSerial;

    private String issuer;

    private LocalDateTime signingTime;

    private Long fileSizeBytes;
}
