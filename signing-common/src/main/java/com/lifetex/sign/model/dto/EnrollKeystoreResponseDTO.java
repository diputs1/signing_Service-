package com.lifetex.sign.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class EnrollKeystoreResponseDTO {
    private String certificate;

    @JsonProperty("serial_number")
    private String serialNumber;

    @JsonProperty("response_format")
    private String responseFormat;

    @JsonProperty("certificate_chain")
    private List<String> certificateChain;
}
