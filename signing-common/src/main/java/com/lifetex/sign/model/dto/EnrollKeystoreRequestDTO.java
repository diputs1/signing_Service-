package com.lifetex.sign.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollKeystoreRequestDTO {
    private String username;

    private String password;

    @JsonProperty("key_alg")
    private String keyAlg = "RSA";

    @JsonProperty("key_spec")
    private String keySpec = "2048";
}
