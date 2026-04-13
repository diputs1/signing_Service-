package com.lifetex.sign.desktop.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lifetex.sign.desktop.service.UsbTokenService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/sign/local")
@RequiredArgsConstructor
public class LocalSigningController {

    private final UsbTokenService usbTokenService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> signLocal(@ModelAttribute LocalSignRequestDTO request) {
        log.info("Received local signing request for file: {}", request.getFile().getOriginalFilename());

        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            return ResponseEntity.badRequest().body("pin is required");
        }

        try {
            byte[] fileBytes = request.getFile().getBytes();

            byte[] signedPdf = usbTokenService.signPdf(
                    fileBytes,
                    request,
                    request.getPassword(),
                    request.getPkcs11LibraryPath());

            // response
            String filename = request.getFile().getOriginalFilename();
            String signedFilename = "signed_" + (filename != null ? filename : "document.pdf");
            String encodedFilename = URLEncoder.encode(signedFilename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(signedPdf);

        } catch (Exception e) {
            log.error("Local signing failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Signing failed: " + e.getMessage());
        }
    }
}
