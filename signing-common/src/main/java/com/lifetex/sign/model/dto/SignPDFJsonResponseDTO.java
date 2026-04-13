package com.lifetex.sign.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SignPDFJsonResponseDTO {
    private String signedDocumentBase64;
    private String message;
    private LocalDateTime signingTime;
}
