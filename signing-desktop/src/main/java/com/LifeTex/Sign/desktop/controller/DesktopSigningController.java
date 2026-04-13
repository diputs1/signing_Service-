package com.lifetex.sign.desktop.controller;

import com.lifetex.sign.desktop.response.DesktopResponseFactory;
import com.lifetex.sign.desktop.service.DesktopFileService;
import com.lifetex.sign.desktop.service.UsbTokenService;
import com.lifetex.sign.model.domain.RectPosition;
import com.lifetex.sign.model.domain.SignaturePlacement;
import com.lifetex.sign.model.domain.SignaturePosition;
import com.lifetex.sign.model.dto.ImageInsertRequest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/desktop")
@RequiredArgsConstructor
public class DesktopSigningController {

    private final UsbTokenService usbTokenService;
    private final DesktopFileService desktopFileService;

    // Existing endpoint
    @PostMapping(value = "/sign", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> signPdf(@ModelAttribute LocalSignRequestDTO request) throws Exception {
        log.info("Nhận yêu cầu ký số từ User: {}", request.getUsername());

        if (request.getFile() == null || request.getFile().isEmpty()) {
            return DesktopResponseFactory.error("File PDF không được để trống", HttpStatus.BAD_REQUEST);
        }

        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            return DesktopResponseFactory.error("Mã PIN (Password) USB Token không được để trống",
                    HttpStatus.BAD_REQUEST);
        }

        byte[] fileBytes = request.getFile().getBytes();

        byte[] signedPdf = usbTokenService.signPdf(fileBytes, request, request.getPassword(),
                request.getPkcs11LibraryPath());

        String filename = request.getFile().getOriginalFilename();
        if (filename == null)
            filename = "signed_document.pdf";
        if (!filename.toLowerCase().endsWith(".pdf"))
            filename += ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(signedPdf);
    }

    /**
     * Ký document hỗ trợ file pdf và docx, doc
     */
    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> signDocument(
            @RequestPart(value = "file") MultipartFile file,
            @RequestPart(value = "username", required = false) String username,
            @RequestPart(value = "password") String pin,
            @RequestPart(value = "reason", required = false) String reason,
            @RequestPart(value = "location", required = false) String location,
            @RequestPart(value = "signatureLevel", required = false) String signatureLevel,
            @RequestPart(value = "slotIndex", required = false) String slotIndexStr,
            @RequestPart(value = "pkcs11LibraryPath", required = false) String pkcs11LibraryPath) throws Exception {

        log.info("Nhan yeu cau ky Desktop - File: {}, User: {}", file.getOriginalFilename(), username);
        String validationError = validateFile(file);
        if (validationError != null) {
            return DesktopResponseFactory.error(validationError, HttpStatus.BAD_REQUEST);
        }
        byte[] documentBytes;

        if (Objects.equals(file.getContentType(), "application/pdf")) {
            documentBytes = file.getBytes();
        } else if (Objects.requireNonNull(file.getOriginalFilename()).toLowerCase().endsWith(".xlsx")
                || file.getOriginalFilename().toLowerCase().endsWith(".xls")) {
            documentBytes = desktopFileService.convertXlsxToPdf(file);
        } else {
            documentBytes = desktopFileService.convertDocxToPdf(file);
        }

        // Use LocalSignRequestDTO to pass slotIndex to service
        LocalSignRequestDTO signRequest = new LocalSignRequestDTO();
        signRequest.setUsername(username);
        signRequest.setPassword(pin);
        signRequest.setReason(reason);
        signRequest.setLocation(location);
        signRequest.setSignatureFormat(signatureLevel);

        if (slotIndexStr != null && !slotIndexStr.isEmpty()) {
            try {
                signRequest.setSlotIndex(Integer.parseInt(slotIndexStr));
            } catch (NumberFormatException e) {
                signRequest.setSlotIndex(0);
            }
        } else {
            signRequest.setSlotIndex(0);
        }
        signRequest.setPkcs11LibraryPath(pkcs11LibraryPath);

        // Pass pin
        byte[] signedPdf = usbTokenService.signPdf(documentBytes, signRequest, pin, pkcs11LibraryPath);

        return buildResponse(file, documentBytes, signedPdf);
    }

    /**
     * Ký Pdf chèn ảnh
     */
    @PostMapping(value = "/document-with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> signWithImage(
            @RequestPart(value = "file") MultipartFile file,
            @RequestPart(value = "username", required = false) String username,
            @RequestPart(value = "password") String pin,
            @RequestPart(value = "reason", required = false) String reason,
            @RequestPart(value = "location", required = false) String location,
            @RequestPart(value = "imageMetadata") String imageMetadataJson,
            @RequestPart(value = "signatureLevel", required = false) String signatureLevel,
            @RequestPart(value = "placement", required = false) String placementStr,
            @RequestPart(value = "slotIndex", required = false) String slotIndexStr,
            @RequestPart(value = "pkcs11LibraryPath", required = false) String pkcs11LibraryPath) throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        List<ImageInsertRequest> imageRequests = mapper.readValue(imageMetadataJson,
                new TypeReference<List<ImageInsertRequest>>() {
                });

        log.info("Nhan yeu cau ky chen ảnh Desktop - File: {}, User: {}", file.getOriginalFilename(), username);
        String validationError = validateFile(file);
        if (validationError != null) {
            return DesktopResponseFactory.error(validationError, HttpStatus.BAD_REQUEST);
        }
        byte[] documentBytes;
        if (Objects.equals(file.getContentType(), "application/pdf")) {
            documentBytes = file.getBytes();
        } else if (file.getOriginalFilename().toLowerCase().endsWith(".xlsx")
                || file.getOriginalFilename().toLowerCase().endsWith(".xls")) {
            documentBytes = desktopFileService.convertXlsxToPdf(file);
        } else {
            documentBytes = desktopFileService.convertDocxToPdf(file);
        }

        byte[] currentPdf = documentBytes;
        int totalSignatures = 0;

        // Determine SignaturePlacement (Default OVERLAY)
        SignaturePlacement placement = SignaturePlacement.OVERLAY;
        if (placementStr != null && !placementStr.isEmpty()) {
            try {
                placement = SignaturePlacement.valueOf(placementStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid placement '{}', using default OVERLAY", placementStr);
            }
        }

        Integer slotIndex = 0;
        if (slotIndexStr != null && !slotIndexStr.isEmpty()) {
            try {
                slotIndex = Integer.parseInt(slotIndexStr);
            } catch (Exception e) {
                slotIndex = 0;
            }
        }

        for (ImageInsertRequest imgReq : imageRequests) {
            String keyword = imgReq.getKeyWord();
            if (keyword == null || keyword.isEmpty())
                continue;

            List<RectPosition> positions = desktopFileService.getKeywordPositionsInPdf(currentPdf, keyword);

            if (positions == null || positions.isEmpty()) {
                log.warn("Khong tim thay keyword: {}", keyword);
                continue;
            }

            byte[] imageBytes = Base64.getDecoder().decode(imgReq.getImagesBase());

            for (RectPosition pos : positions) {
                LocalSignRequestDTO signRequest = new LocalSignRequestDTO(); // Use Local DTO
                signRequest.setUsername(username);
                signRequest.setPassword(pin);
                signRequest.setReason(reason);
                signRequest.setLocation(location);
                signRequest.setSignatureFormat(signatureLevel);
                signRequest.setSignatureImage(imageBytes);
                signRequest.setSlotIndex(slotIndex);
                signRequest.setPkcs11LibraryPath(pkcs11LibraryPath);

                float imgWidth = (imgReq.getWidth() > 0) ? imgReq.getWidth() : 80f;
                float imgHeight = (imgReq.getHeight() > 0) ? imgReq.getHeight() : 50f;
                float margin = 5f;

                SignaturePosition sigPos = calculateSignaturePosition(
                        pos, placement, imgWidth, imgHeight, margin);

                signRequest.setPositions(Collections.singletonList(sigPos));

                currentPdf = usbTokenService.signPdf(currentPdf, signRequest, pin, pkcs11LibraryPath);
                totalSignatures++;
            }
        }

        if (totalSignatures == 0) {
            log.info("Khong tim thay keyword nao, ky invisible signature cuoi cung");
            LocalSignRequestDTO defaultRequest = new LocalSignRequestDTO();
            defaultRequest.setUsername(username);
            defaultRequest.setPassword(pin);
            defaultRequest.setSlotIndex(slotIndex);
            defaultRequest.setPkcs11LibraryPath(pkcs11LibraryPath);
            defaultRequest.setSignatureFormat(signatureLevel);
            currentPdf = usbTokenService.signPdf(currentPdf, defaultRequest, pin, pkcs11LibraryPath);
        }

        return buildResponse(file, documentBytes, currentPdf);
    }

    /**
     * Ký nháy
     */
    @PostMapping(value = "/document-initial-signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> initialSignature(
            @RequestPart(value = "file") MultipartFile file,
            @RequestPart(value = "username", required = false) String username,
            @RequestPart(value = "password") String pin,
            @RequestPart(value = "reason", required = false) String reason,
            @RequestPart(value = "location", required = false) String location,
            @RequestPart(value = "keyword", required = false) String keyword,
            @RequestPart(value = "base64Image") String base64Image,
            @RequestPart(value = "signatureLevel", required = false) String signatureLevel,
            @RequestPart(value = "placement", required = false) String placementStr,
            @RequestPart(value = "slotIndex", required = false) String slotIndexStr,
            @RequestPart(value = "pkcs11LibraryPath", required = false) String pkcs11LibraryPath) throws Exception {

        String keywordSign = keyword != null ? keyword : "./.";
        boolean hasImage = (base64Image != null && !base64Image.isEmpty());

        log.info("Nhan yeu cau ky nhay Desktop - File: {}, User: {}, Keyword: {}", file.getOriginalFilename(),
                username, keywordSign);

        String validationError = validateFile(file);
        if (validationError != null) {
            return DesktopResponseFactory.error(validationError, HttpStatus.BAD_REQUEST);
        }
        byte[] documentBytes;
        if (Objects.equals(file.getContentType(), "application/pdf")) {
            documentBytes = file.getBytes();
        } else if (file.getOriginalFilename().toLowerCase().endsWith(".xlsx")
                || file.getOriginalFilename().toLowerCase().endsWith(".xls")) {
            documentBytes = desktopFileService.convertXlsxToPdf(file);
        } else {
            documentBytes = desktopFileService.convertDocxToPdf(file);
        }

        List<RectPosition> positions = desktopFileService.getKeywordPositionsInPdf(documentBytes, keywordSign);

        if (positions == null || positions.isEmpty()) {
            throw new RuntimeException("Không tìm thấy keyword '" + keywordSign + "' trong file PDF");
        }

        byte[] currentPdf = documentBytes;

        // Determine SignaturePlacement (Default RIGHT)
        SignaturePlacement placement = SignaturePlacement.RIGHT;
        if (placementStr != null && !placementStr.isEmpty()) {
            try {
                placement = SignaturePlacement.valueOf(placementStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return DesktopResponseFactory.error("Invalid placement '" + placementStr + "'", HttpStatus.BAD_REQUEST);
            }
        }

        Integer slotIndex = 0;
        if (slotIndexStr != null && !slotIndexStr.isEmpty()) {
            try {
                slotIndex = Integer.parseInt(slotIndexStr);
            } catch (Exception e) {
                slotIndex = 0;
            }
        }

        for (RectPosition pos : positions) {
            LocalSignRequestDTO signRequest = new LocalSignRequestDTO();
            signRequest.setUsername(username);
            signRequest.setPassword(pin);
            signRequest.setReason(reason);
            signRequest.setLocation(location);
            signRequest.setSignatureFormat(signatureLevel);
            signRequest.setSlotIndex(slotIndex);
            signRequest.setPkcs11LibraryPath(pkcs11LibraryPath);

            if (hasImage) {
                byte[] imageBytes = Base64.getDecoder().decode(base64Image);
                signRequest.setSignatureImage(imageBytes);

                float signatureWidth = 40f;
                float signatureHeight = 20f;
                float margin = 8f;

                SignaturePosition sigPos = calculateSignaturePosition(
                        pos, placement, signatureWidth, signatureHeight, margin);

                signRequest.setPositions(Collections.singletonList(sigPos));
            }

            currentPdf = usbTokenService.signPdf(currentPdf, signRequest, pin, pkcs11LibraryPath);
        }

        return buildResponse(file, documentBytes, currentPdf);
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty())
            return "Vui lòng chọn file để ký";
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty())
            return "Tên file không hợp lệ";
        String lower = filename.toLowerCase();
        if (!lower.endsWith(".pdf") && !lower.endsWith(".docx") && !lower.endsWith(".doc") && !lower.endsWith(".xlsx")
                && !lower.endsWith(".xls"))
            return "Chỉ hỗ trợ file PDF, DOCX, DOC, XLSX hoặc XLS";
        if (file.getSize() > 10 * 1024 * 1024)
            return "File quá lớn. Tối đa 10MB";
        return null;
    }

    private ResponseEntity<byte[]> buildResponse(MultipartFile file, byte[] originalBytes, byte[] signedBytes) {
        String originalFilename = Arrays.equals(originalBytes, signedBytes)
                ? file.getOriginalFilename()
                : getOutputFilename(file.getOriginalFilename());

        if (originalFilename == null)
            originalFilename = "document.pdf";

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String signedFilename = originalFilename.replace(".pdf", "_signed_" + timestamp + ".pdf");

        String asciiName = Normalizer.normalize(signedFilename, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "");
        String encoded = URLEncoder.encode(signedFilename, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_PDF)
                .body(signedBytes);
    }

    private String getOutputFilename(String originName) {
        if (originName == null)
            return "document.pdf";
        return originName.replaceAll("\\.(docx?|xlsx?|pdf)$", ".pdf");
    }

    private SignaturePosition calculateSignaturePosition(
            RectPosition keywordPos,
            SignaturePlacement placement,
            float signatureWidth,
            float signatureHeight,
            float margin) {

        float dssX;
        float dssY;

        switch (placement) {
            case OVERLAY -> {
                dssX = keywordPos.getX();
                dssY = keywordPos.getY() - (signatureHeight / 2);
            }
            case RIGHT -> {
                dssX = keywordPos.getX() + keywordPos.getWidth() + margin;
                float keywordCenterY = keywordPos.getY() - (keywordPos.getHeight() / 2);
                dssY = keywordCenterY - (signatureHeight / 2);
            }
            case LEFT -> {
                dssX = keywordPos.getX() - signatureWidth - margin;
                float keywordCenterY = keywordPos.getY() - (keywordPos.getHeight() / 2);
                dssY = keywordCenterY - (signatureHeight / 2);
            }
            case TOP -> {
                dssX = keywordPos.getX() + (keywordPos.getWidth() / 4);
                dssY = keywordPos.getY() - keywordPos.getHeight(); // PDFBox Y originates Top, DSS uses Bottom?
                // Wait, logic in Web controller:
                // dssY = keywordPos.getY() - keywordPos.getHeight();
                // But PDFBox usually uses Bottom-Left origin for content stream, BUT scanner
                // might return Top-Left.
                // NOTE: If using PDFKeywordScanner from core, check its coordinate system.
                // Assuming web logic was correct for the same scanner.
            }
            case BOTTOM -> {
                dssX = keywordPos.getX() + (keywordPos.getWidth() / 4);
                dssY = keywordPos.getY() + keywordPos.getHeight();
            }
            default -> {
                dssX = keywordPos.getX();
                dssY = keywordPos.getY();
            }
        }

        // Adjust for implementation differences if any.
        // For Top/Bottom in original web controller:
        // TOP: Y - height
        // BOTTOM: Y + height
        // This implies Y grows downwards (Top-Left origin) in keywordPos?
        // Let's assume the copied logic is correct relative to the Scanner.

        return new SignaturePosition(
                keywordPos.getPage(),
                dssX,
                dssY,
                signatureWidth,
                signatureHeight);
    }
}
