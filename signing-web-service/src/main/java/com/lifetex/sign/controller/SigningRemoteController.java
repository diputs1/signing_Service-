package com.lifetex.sign.controller;

import com.lifetex.sign.exception.SigningException;
import com.lifetex.sign.infrastructure.http.GetFileServiceClient;
import com.lifetex.sign.model.dto.*;
import com.lifetex.sign.service.EJBCAService;
import com.lifetex.sign.service.DSSSigningService;
import com.lifetex.sign.service.SignLocalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/sign-remote/demo")
public class SigningRemoteController {
    @Autowired
    private final DSSSigningService signingService;

    @Autowired
    private final EJBCAService ejbcaService;

    public SigningRemoteController(DSSSigningService signingService, SignLocalService signLocalService,
            EJBCAService ejbcaService, GetFileServiceClient getFileService) {
        this.signingService = signingService;
        this.ejbcaService = ejbcaService;
    }

    @PostMapping(value = "/document", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignRemoteResponseDTO> signDocument(@RequestBody SignRemoteRequestDTO request)
            throws Exception {
        try {
            String username = request.getUsername();

            log.info("Nhan yeu cau ky - User: {}", username);

            boolean isUserExists = ejbcaService.endEntityExists(username);

            if (!isUserExists) {
                boolean result = signingService.revokeCache(username);
                if (result) {
                    log.info("Đã thu hồi .p12 (chung chi) cache cho người dùng không tồn tại: {}", username);
                }
                throw new SigningException("Người dùng không tồn tại trong hệ thống EJBCA");
            }

            byte[] signatureBytes = signingService.signDataToSign(
                    request.getDataToSign(),
                    request.getUsername(),
                    request.getPassword());
            String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);

            log.info("Remote signing success - User: {}", request.getUsername());

            SignRemoteResponseDTO response = new SignRemoteResponseDTO(signatureBase64);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Signing failed", e);
            throw e;
        }
    }
}
