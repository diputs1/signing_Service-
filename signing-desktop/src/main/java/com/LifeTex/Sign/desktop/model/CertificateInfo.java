package com.lifetex.sign.desktop.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CertificateInfo {
    private String subject;
    private String issuer;
    private Date notAfter;
    private String serialNumber;
}
