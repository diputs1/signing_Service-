package com.lifetex.sign.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class SignRequest {
    private String certIdentifier;
    private String profile;
    private String signatureLevel;

    public SignRequest() {
        this.profile = "default";
        this.signatureLevel = "PAdES-BASELINE-B";
    }

}
