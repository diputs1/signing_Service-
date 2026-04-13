package com.lifetex.sign.model.dto;

import lombok.Data;

@Data
public class SignPDFJsonRequestDTO {
    private String username;
    private String password;
    private String documentBase64;
    private String signatureLevel = "B";
    private String reason;
    private String location;
    private String contactInfo;
}
