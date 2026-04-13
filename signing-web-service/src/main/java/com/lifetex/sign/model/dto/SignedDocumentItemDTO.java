package com.lifetex.sign.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignedDocumentItemDTO {
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private int index;
    private String filename;
    private String status;
    /** Base64 PDF when status = SUCCESS */
    private String signedBase64;
    /** Error message when status = FAILED */
    private String error;
}
