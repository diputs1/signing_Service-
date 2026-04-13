package com.lifetex.sign.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SignRemoteResponseDTO {
    private String signatureBase64;

}
