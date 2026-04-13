package com.lifetex.sign.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SignRemoteRequestDTO {
    private String username;
    private String password;
    private String dataToSign;
}
