package com.lifetex.sign.service;

import com.lifetex.sign.model.CertificateData;
import com.lifetex.sign.model.entity.SigningCredentials;
import eu.europa.esig.dss.model.x509.CertificateToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KeystoreService {

    /**
     * tải keystore PKCS12 từ base64
     */
    public SigningCredentials loadFromPKCS12(byte[] p12Bytes, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new ByteArrayInputStream(p12Bytes), password.toCharArray());

        String alias = keyStore.aliases().nextElement();

        X509Certificate signingCert = (X509Certificate) keyStore.getCertificate(alias);
        Certificate[] chainArray = keyStore.getCertificateChain(alias);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());

        // Chuyển sang DSS types
        CertificateToken signingCertToken = new CertificateToken(signingCert);

        List<CertificateToken> chainTokens = Arrays.stream(chainArray)
                .map(c -> new CertificateToken((X509Certificate) c))
                .collect(Collectors.toList());

        Date expiryDate = signingCert.getNotAfter();

        return new SigningCredentials(signingCertToken, privateKey, expiryDate, chainTokens);
    }

    public CertificateData extractCertificateData(SigningCredentials credentials) {
        // Lấy signing certificate (user cert)
        CertificateToken signingCertToken = credentials.getCertificateToken();

        // Encode signing cert riêng
        String certificateBase64 = Base64.getEncoder()
                .encodeToString(signingCertToken.getEncoded());

        // Encode full chain
        List<String> chainBase64 = credentials.getCertificateChain().stream()
                .map(token -> Base64.getEncoder().encodeToString(token.getEncoded()))
                .collect(Collectors.toList());

        X509Certificate signingCert = signingCertToken.getCertificate();

        return new CertificateData(
                certificateBase64,
                chainBase64,
                signingCert.getSubjectX500Principal().getName(),
                signingCert.getIssuerX500Principal().getName(),
                signingCert.getNotBefore(),
                signingCert.getNotAfter(),
                signingCert.getSerialNumber().toString());
    }

    /**
     * Validate signing credentials
     */
    public boolean validateCredentials(SigningCredentials credentials) {

        if (credentials == null)
            return false;
        if (credentials.getCertificateToken() == null)
            return false;
        if (credentials.getPrivateKey() == null)
            return false;

        try {

            Date now = new Date();
            Date notBefore = credentials.getCertificateToken().getNotBefore();
            Date notAfter = credentials.getCertificateToken().getNotAfter();

            if (now.before(notBefore)) {
                log.warn("Certificate không hợp lệ!");
                return false;
            }

            if (now.after(notAfter)) {
                log.warn("Certificate hết hạn");
                return false;
            }

            // Check private key exists
            if (credentials.getPrivateKey() == null) {
                return false;
            }

            log.info("Signing credentials hợp lệ");
            return true;

        } catch (Exception e) {
            log.error("Credential validation failed", e);
            return false;
        }
    }
}
