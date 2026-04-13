package com.lifetex.sign.model.dto;

import com.lifetex.sign.model.domain.SignaturePosition;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignRequestDTO {

    // File removed to separate Web dependency. Use WebSignRequestDTO in Web layer.

    @NotBlank(message = "Username không được để trống")
    private String username;

    @NotBlank(message = "Password không được để trống")
    private String password;

    private String signatureFormat = "PAdES"; // PAdES, XAdES, CAdES

    private String signatureLevel = "B"; // B, T, LT, LTA

    private String reason;

    private String location;

    private String contactInfo;

    private String Page;

    private List<SignaturePosition> positions;

    private byte[] signatureImage;

}