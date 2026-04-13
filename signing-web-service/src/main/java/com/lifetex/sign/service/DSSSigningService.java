package com.lifetex.sign.service;

import com.lifetex.sign.exception.SigningException;
import com.lifetex.sign.model.CertificateData;
import com.lifetex.sign.model.SignRequest;
import com.lifetex.sign.model.dto.EnrollKeystoreResponseDTO;
import com.lifetex.sign.model.dto.SignRequestDTO;
import com.lifetex.sign.model.entity.SigningCredentials;
import com.lifetex.sign.service.core.PdfSigner;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import eu.europa.esig.dss.validation.CertificateVerifier;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DSSSigningService {
    @Value("${dss.signature-level:PAdES_BASELINE_B}")
    private String defaultSignatureLevel;

    @Value("${dss.digest-algorithm:SHA256}")
    private String defaultDigestAlgorithm;

    private final EJBCAService ejbcaService;
    private final KeystoreService keystoreService;
    private final CertificateVerifier certificateVerifier;
    private final PdfSigner pdfSigner;

    private final StringRedisTemplate redis;
    private static final String CACHE_PREFIX = "signing:p12:";
    private static final String CACHE_CERT_PREFIX = "signing:cert:";

    public DSSSigningService(EJBCAService ejbcaService, KeystoreService keystoreService,
            CertificateVerifier certificateVerifier, StringRedisTemplate redis, PdfSigner pdfSigner) {
        this.ejbcaService = ejbcaService;
        this.keystoreService = keystoreService;
        this.certificateVerifier = certificateVerifier;
        this.redis = redis;
        this.pdfSigner = pdfSigner;
    }

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Ký PDF bằng DSS với keystore PKCS12 từ base64
     */
    public byte[] signPDF(byte[] pdfBytes, SignRequestDTO signRequest) throws Exception {
        try {
            String userName = signRequest.getUsername();
            String password = signRequest.getPassword();
            String cacheKey = CACHE_PREFIX + userName;
            String certCacheKey = CACHE_CERT_PREFIX + userName;

            log.info("=== Starting PDF signing ===");
            byte[] p12Bytes;

            // 1. Lấy từ cache nếu có
            String cachedBase64 = redis.opsForValue().get(cacheKey);

            if (cachedBase64 != null && !cachedBase64.isEmpty()) {
                log.info("Keystore tìm thấy trong cache với user: {}", userName);
                try {
                    p12Bytes = Base64.getDecoder().decode(cachedBase64);
                } catch (Exception e) {
                    log.warn("Cached keystore corrupted → ignoring cache");
                    cachedBase64 = null;
                    p12Bytes = null;
                }
            } else {
                p12Bytes = null;
            }
            // 2. Nếu cache không có → enroll từ EJBCA
            if (p12Bytes == null) {
                log.info("No cache, enrolling new keystore from EJBCA...");

                EnrollKeystoreResponseDTO response = ejbcaService.enrollKeystore(userName, password);
                String cleanBase64 = response.getCertificate().replaceAll("\\s+", "");
                p12Bytes = Base64.getDecoder().decode(cleanBase64);
            }

            // 3.Load certificate & private key
            SigningCredentials credentials = keystoreService.loadFromPKCS12(p12Bytes, password);

            Date expiryDate = credentials.getExpiryDate();
            Date now = new Date();
            // 3. Nếu credential còn hạn & chưa cache thì mới cache
            if (expiryDate.after(now) && cachedBase64 == null) {

                long ttlSeconds = (expiryDate.getTime() - now.getTime()) / 1000;
                String encoded = Base64.getEncoder().encodeToString(p12Bytes);

                redis.opsForValue().set(cacheKey, encoded, ttlSeconds, TimeUnit.SECONDS);

                log.info("Cache keystore cho nguoi dung: {} | TTL: {} seconds", userName, ttlSeconds);

                // Cache RIÊNG certificate data (không chứa private key)
                CertificateData certData = keystoreService.extractCertificateData(credentials);
                String certDataJson = new ObjectMapper().writeValueAsString(certData);
                redis.opsForValue().set(certCacheKey, certDataJson, ttlSeconds, TimeUnit.SECONDS);
                log.info("Certificate data cached for user: {}", userName);
            }

            // 4. Tạo Pkcs12SignatureToken
            byte[] keystoreBytes = Base64.getDecoder().decode(Base64.getEncoder().encodeToString(p12Bytes));
            Pkcs12SignatureToken signingToken = new Pkcs12SignatureToken(new ByteArrayInputStream(keystoreBytes),
                    new KeyStore.PasswordProtection(signRequest.getPassword().toCharArray()));

            try {
                // 5. Lấy private key entry
                DSSPrivateKeyEntry privateKeyEntry = signingToken.getKeys().get(0);

                // 6. Tạo DSSDocument từ PDF bytes
                DSSDocument toSignDocument = new InMemoryDocument(pdfBytes, "document.pdf");

                // 7. DELEGATE TO CORE SIGNER
                return pdfSigner.signPdf(
                        toSignDocument,
                        signingToken, // Pass generic token
                        privateKeyEntry,
                        signRequest,
                        defaultSignatureLevel,
                        defaultDigestAlgorithm);
            } finally {
                // 12. Đóng signing token
                signingToken.close();
            }

        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("password") || msg.contains("mac") || msg.contains("incorrect")) {
                log.warn("Sai mat khau chung thu so cho nguoi dung: {}", signRequest.getUsername());
                throw new SigningException(
                        "Mật khẩu chứng thư số không đúng cho người dùng: " + signRequest.getUsername());
            }
            log.error("Loi IO khi ky PDF voi nguoi dung: {}", signRequest.getUsername(), e);
            throw new SigningException("Lỗi đọc chứng thư số: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Loi khi ky PDF voi nguoi dung: {}", signRequest.getUsername(), e);
            throw new SigningException("Loi khi ky PDF voi nguoi dung: " + signRequest.getUsername(), e);
        }
    }

    public byte[] signDataToSign(
            String dataToSignBase64,
            String userName,
            String password) throws Exception {

        log.info("=== Remote signing: sign ToBeSigned only ===");

        byte[] dataToSignBytes = Base64.getDecoder().decode(dataToSignBase64);

        // Load PKCS12 / HSM
        byte[] p12Bytes;
        String cachedBase64 = redis.opsForValue().get(CACHE_PREFIX + userName);

        if (cachedBase64 != null && !cachedBase64.isEmpty()) {
            p12Bytes = Base64.getDecoder().decode(cachedBase64);
        } else {
            EnrollKeystoreResponseDTO response = ejbcaService.enrollKeystore(userName, password);
            p12Bytes = Base64.getDecoder().decode(response.getCertificate().replaceAll("\\s+", ""));
            cacheKeystoreAndCertData(p12Bytes, userName, password);
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try {
            keyStore.load(new ByteArrayInputStream(p12Bytes), password.toCharArray());
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("password") || msg.contains("mac") || msg.contains("incorrect")) {
                log.warn("Sai mat khau chung thu so cho nguoi dung (remote sign): {}", userName);
                throw new SigningException("Mật khẩu chứng thư số không đúng cho người dùng: " + userName);
            }
            throw new SigningException("Lỗi đọc chứng thư số: " + e.getMessage(), e);
        }
        String alias = keyStore.aliases().nextElement();
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());

        // Ký ĐÚNG chuẩn DSS
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(dataToSignBytes);

        byte[] signatureBytes = signature.sign();

        log.info("Signature length: {}", signatureBytes.length);

        return signatureBytes;
    }

    /**
     * Thu hồi cache .p12 cho user cụ thể
     */
    public boolean revokeCache(String username) {
        String cacheKey = CACHE_PREFIX + username;
        Boolean existed = redis.hasKey(cacheKey);
        if (existed) {
            redis.delete(cacheKey);
            log.info("Đã xóa cache .p12 cho user: {}", username);
            return true;
        } else {
            log.warn("Không tìm thấy cache .p12 để xóa cho user: {}", username);
            return false;
        }
    }

    /**
     * Thu hồi toàn bộ cache .p12
     */
    public void revokeAllCache() {
        Set<String> keys = redis.keys(CACHE_PREFIX + "*");
        redis.delete(keys);
        log.warn("Đã xóa toàn bộ cache .p12");
    }

    /**
     * Ký XML bằng DSS với XAdES (Chưa triển khai)
     */
    public byte[] signXML(byte[] xmlBytes, SignRequest signRequest) {
        // Similar implementation with XAdESService
        throw new UnsupportedOperationException("XML signing not implemented yet");
    }

    public CertificateData getCertChain(String userName, String password) throws Exception {
        String certCacheKey = CACHE_CERT_PREFIX + userName;

        String cachedCertJson = redis.opsForValue().get(certCacheKey);

        if (cachedCertJson == null || cachedCertJson.isEmpty()) {
            boolean isUserExists = ejbcaService.endEntityExists(userName);
            if (!isUserExists) {
                return null;
            }
            byte[] p12Bytes;
            EnrollKeystoreResponseDTO response = ejbcaService.enrollKeystore(userName, password);
            String cleanBase64 = response.getCertificate().replaceAll("\\s+", "");
            p12Bytes = Base64.getDecoder().decode(cleanBase64);

            cacheKeystoreAndCertData(p12Bytes, userName, password);
            return keystoreService.extractCertificateData(keystoreService.loadFromPKCS12(p12Bytes, password));
        }

        try {
            return new ObjectMapper().readValue(cachedCertJson, CertificateData.class);

        } catch (Exception e) {
            throw new SigningException("Lỗi khi lấy certificate chain" + e.getMessage());
        }

    }

    /**
     * cache keystore
     */
    private void cacheKeystoreAndCertData(byte[] p12Bytes, String userName, String password) throws Exception {
        SigningCredentials credentials = keystoreService.loadFromPKCS12(p12Bytes, password);
        Date expiryDate = credentials.getExpiryDate();
        long ttlSeconds = (expiryDate.getTime() - System.currentTimeMillis()) / 1000;

        // Cache full PKCS12
        String encodedP12 = Base64.getEncoder().encodeToString(p12Bytes);
        redis.opsForValue().set(CACHE_PREFIX + userName, encodedP12, ttlSeconds, TimeUnit.SECONDS);

        // Cache riêng cert data nếu cần (cho client lấy public chain)
        CertificateData certData = keystoreService.extractCertificateData(credentials);
        String certDataJson = new ObjectMapper().writeValueAsString(certData);
        redis.opsForValue().set(CACHE_CERT_PREFIX + userName, certDataJson, ttlSeconds, TimeUnit.SECONDS);

        log.info("Đã cache keystore và cert data cho user: {} | TTL: {}s", userName, ttlSeconds);
    }
}
