package com.lifetex.sign.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CertificateData {
    private String certificateBase64; // Certificate chính
    private List<String> certificateChainBase64; // Certificate chain
    private String subject; // DN của subject
    private String issuer; // DN của issuer
    private Date notBefore; // Ngày bắt đầu hiệu lực
    private Date notAfter; // Ngày hết hạn
    private String serialNumber; // Serial number
}
