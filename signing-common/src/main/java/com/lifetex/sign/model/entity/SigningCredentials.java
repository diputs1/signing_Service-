package com.lifetex.sign.model.entity;

import eu.europa.esig.dss.model.x509.CertificateToken;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
public class SigningCredentials {
    private CertificateToken certificateToken;
    private PrivateKey privateKey;
    private Date expiryDate;
    private List<CertificateToken> certificateChain;
}
