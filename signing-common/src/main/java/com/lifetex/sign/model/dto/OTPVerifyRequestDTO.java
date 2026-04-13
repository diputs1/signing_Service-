package com.lifetex.sign.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OTPVerifyRequestDTO {
    @NotBlank
    private String otp;
}
