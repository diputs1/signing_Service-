package com.lifetex.sign.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignResponse {
    private Boolean success;
    private String message;
    private String workflowId;
    private String signedDocumentUrl;
}
