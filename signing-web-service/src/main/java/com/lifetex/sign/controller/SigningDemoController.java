package com.lifetex.sign.controller;

import com.lifetex.sign.exception.SigningException;
import com.lifetex.sign.infrastructure.http.GetFileServiceClient;
import com.lifetex.sign.model.CertificateData;
import com.lifetex.sign.model.dto.*;
import com.lifetex.sign.response.*;
import com.lifetex.sign.service.EJBCAService;
import com.lifetex.sign.service.DSSSigningService;
import com.lifetex.sign.service.SignLocalService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/sign/demo")
public class SigningDemoController {
    @Autowired
    private final DSSSigningService signingService;

    @Autowired
    private final SignLocalService signLocalService;

    @Autowired
    private final EJBCAService ejbcaService;

    private final GetFileServiceClient getFileService;

    public SigningDemoController(DSSSigningService signingService, SignLocalService signLocalService,
            EJBCAService ejbcaService, GetFileServiceClient getFileService) {
        this.signingService = signingService;
        this.signLocalService = signLocalService;
        this.ejbcaService = ejbcaService;
        this.getFileService = getFileService;
    }

    @GetMapping("/threads")
    public String checkThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().contains("worker"))
                .map(Thread::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Ký document hỗ trợ file pdf và docx, doc
     * POST /api/sign
     */
    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> signDocument(
            @Valid @ModelAttribute WebSignRequestDTO request, BindingResult bindingResult) throws Exception {
        try {

            MultipartFile uploadedFile = request.getFile();
            log.info("Nhan yeu cau ky - File: {}, User: {}",
                    uploadedFile.getOriginalFilename(), request.getUsername());
            String validationError = validateFile(uploadedFile);
            if (validationError != null) {
                return ResponseFactory.error(validationError, HttpStatus.BAD_REQUEST, 400);
            }
            byte[] documentBytes;

            if (!Objects.equals(uploadedFile.getContentType(), "application/pdf")) {
                // Convert DOCX/DOC sang PDF
                documentBytes = signLocalService.convertDocxToPdf(uploadedFile);
            } else {
                documentBytes = uploadedFile.getBytes();
            }

            boolean isUserExists = ejbcaService.endEntityExists(request.getUsername());

            if (!isUserExists) {
                boolean result = signingService.revokeCache(request.getUsername());
                if (result) {
                    log.info("Đã thu hồi .p12 (chung chi) cache cho người dùng không tồn tại: {}",
                            request.getUsername());
                }
                throw new SigningException("Người dùng không tồn tại trong hệ thống EJBCA");
            }

            // Sign the document
            byte[] signedPdf = signingService.signPDF(documentBytes, request);

            // Generate filename
            String originalFilename = Arrays.equals(documentBytes, uploadedFile.getBytes())
                    ? uploadedFile.getOriginalFilename()
                    : getOutputFilename(uploadedFile);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            assert originalFilename != null;
            String signedFilename = originalFilename.replace(".pdf", "_signed_" + timestamp + ".pdf");
            String asciiName = Normalizer
                    .normalize(signedFilename, Normalizer.Form.NFD)
                    .replaceAll("[^\\p{ASCII}]", "");

            String encoded = URLEncoder.encode(signedFilename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            // Return signed PDF
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.add(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded);
            headers.setContentLength(signedPdf.length);

            log.info("Ky thanh cong - File: {}, User: {}", signedFilename, request.getUsername());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(signedPdf);

        } catch (Exception e) {
            log.error("Signing failed", e);
            throw e;
        }
    }

    /**
     * Ký Pdf chèn ảnh
     */

    @PostMapping(value = "/document-with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> sign(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "username", required = false) String username,
            @RequestPart(value = "password", required = false) String password,
            @RequestPart(value = "reason", required = false) String reason,
            @RequestPart(value = "location", required = false) String location,
            @RequestPart(value = "imageMetadata", required = false) String imageMetadataJson,
            @RequestPart(value = "signatureLevel", required = false) String signatureLevel) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<ImageInsertRequest> imageMetadata = mapper.readValue(imageMetadataJson,
                new TypeReference<List<ImageInsertRequest>>() {
                });
        try {

            log.info("Nhan yeu cau ky chen anh - File: {}, User: {}",
                    file.getOriginalFilename(), username);
            String validationError = validateFile(file);
            if (validationError != null) {
                return ResponseFactory.error(validationError, HttpStatus.BAD_REQUEST, 400);
            }
            byte[] documentBytes;

            if (!Objects.equals(file.getContentType(), "application/pdf")) {
                documentBytes = signLocalService.convertDocxToPdf(file);
            } else {
                documentBytes = file.getBytes();
            }
            // log.info("Nhan file pdf ky chen anh va chung chi: {}, User: {}",
            // documentBytes, username);

            byte[] pdfWithImages = signLocalService.insertImagesIntoPdf(
                    documentBytes,
                    imageMetadata);

            boolean isUserExists = ejbcaService.endEntityExists(username);

            if (!isUserExists) {
                boolean result = signingService.revokeCache(username);
                if (result) {
                    log.info("Đã thu hồi .p12 cache cho người dùng không tồn tại: {}", username);
                }
                throw new SigningException("Người dùng không tồn tại trong hệ thống EJBCA");
            }

            // byte[] pdfWithImages = signLocalService.insertImagesIntoPdf(
            // file.getBytes(),
            // imageMetadata
            // );

            // Sign the document
            SignRequestDTO signRequest = new SignRequestDTO();
            signRequest.setUsername(username);
            signRequest.setPassword(password);
            signRequest.setReason(reason);
            signRequest.setLocation(location);
            signRequest.setSignatureFormat(signatureLevel);

            byte[] signedPdf = signingService.signPDF(pdfWithImages, signRequest);

            // Generate filename
            String originalFilename = Arrays.equals(documentBytes, file.getBytes()) ? file.getOriginalFilename()
                    : getOutputFilename(file);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            assert originalFilename != null;
            String signedFilename = originalFilename.replace(".pdf", "_signed_" + timestamp + ".pdf");
            String asciiName = Normalizer
                    .normalize(signedFilename, Normalizer.Form.NFD)
                    .replaceAll("[^\\p{ASCII}]", "");

            String encoded = URLEncoder.encode(signedFilename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            // Return signed PDF
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.add(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded);
            headers.setContentLength(signedPdf.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(signedPdf);

        } catch (Exception e) {
            log.error("Signing failed", e);
            throw e;
        }
    }

    /**
     * Ký nháy
     */

    @PostMapping(value = "/document-initial-signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> InitialSignature(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "username", required = false) String username,
            @RequestPart(value = "password", required = false) String password,
            @RequestPart(value = "reason", required = false) String reason,
            @RequestPart(value = "location", required = false) String location,
            @RequestPart(value = "keyword", required = false) String keyword,
            @RequestPart(value = "base64Image", required = false) String base64Image,
            @RequestPart(value = "signatureLevel", required = false) String signatureLevel) throws Exception {

        try {

            String keywordSign = keyword != null ? keyword : "./.";
            log.info("Nhan yeu cau ky nhay - File: {}, User: {}",
                    file.getOriginalFilename(), username);
            String validationError = validateFile(file);
            if (validationError != null) {
                return ResponseFactory.error(validationError, HttpStatus.BAD_REQUEST, 400);
            }
            byte[] documentBytes;

            if (!Objects.equals(file.getContentType(), "application/pdf")) {
                documentBytes = signLocalService.convertDocxToPdf(file);
            } else {
                documentBytes = file.getBytes();
            }

            ImageInsertRequest imageRequests = new ImageInsertRequest();
            imageRequests.setWidth(50);
            imageRequests.setHeight(25);
            imageRequests.setKeyWord(keywordSign);
            imageRequests.setImagesBase(base64Image);

            byte[] pdfWithImages = signLocalService.insertImagesToInitialSignature(
                    documentBytes,
                    imageRequests);

            boolean isUserExists = ejbcaService.endEntityExists(username);

            if (!isUserExists) {
                boolean result = signingService.revokeCache(username);
                if (result) {
                    log.info("Đã thu hồi .p12 cache cho người dùng không tồn tại(ký nháy): {}", username);
                }
                throw new SigningException("Người dùng không tồn tại trong hệ thống EJBCA");
            }

            // Sign the document
            SignRequestDTO signRequest = new SignRequestDTO();
            signRequest.setUsername(username);
            signRequest.setPassword(password);
            signRequest.setReason(reason);
            signRequest.setLocation(location);
            signRequest.setSignatureFormat(signatureLevel);

            byte[] signedPdf = signingService.signPDF(pdfWithImages, signRequest);

            // Generate filename
            String originalFilename = Arrays.equals(documentBytes, file.getBytes()) ? file.getOriginalFilename()
                    : getOutputFilename(file);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            assert originalFilename != null;
            String signedFilename = originalFilename.replace(".pdf", "_signed_" + timestamp + ".pdf");
            String asciiName = Normalizer
                    .normalize(signedFilename, Normalizer.Form.NFD)
                    .replaceAll("[^\\p{ASCII}]", "");

            String encoded = URLEncoder.encode(signedFilename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            // Return signed PDF
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.add(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded);
            headers.setContentLength(signedPdf.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(signedPdf);

        } catch (Exception e) {
            log.error("Ký nhay that bai", e);
            throw e;
        }
    }

    /**
     * Ký số server tải file ký
     */

    @PostMapping(value = "/document-file-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> documentFileUrl(@RequestBody JsonNode body) throws Exception {

        String fileUrl = body.get("fileUrl").asText();
        String username = body.get("username").asText();
        String password = body.get("password").asText();
        String reason = body.get("reason").asText();
        String location = body.get("location").asText();
        String signatureLevel = body.get("signatureLevel").asText();

        try {
            if (fileUrl == null || fileUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("fileUrl is required");
            }
            ResponseEntity<byte[]> response = getFileService.downloadFile(fileUrl);

            byte[] fileBytes = response.getBody();

            if (fileBytes == null) {
                return ResponseFactory.error("Không thấy file từ fileUrl để ký", HttpStatus.BAD_REQUEST, 400);
            }
            String contentType = Objects.requireNonNull(response.getHeaders().getContentType()).toString();
            String originalFilenameApi = extractFilename(response.getHeaders());

            log.info("Ký file với file: {}, contentType: {}", originalFilenameApi, contentType);

            if (!"application/pdf".equals(contentType)) {
                fileBytes = signLocalService.convertDocxToPdf(fileBytes);
            }

            boolean isUserExists = ejbcaService.endEntityExists(username);

            if (!isUserExists) {
                boolean result = signingService.revokeCache(username);
                if (result) {
                    log.info("Đã thu hồi .p12 (chung chi) cache cho người dùng không tồn tại: {}", username);
                }
                throw new SigningException("Người dùng không tồn tại trong hệ thống EJBCA");
            }

            SignRequestDTO signRequest = new SignRequestDTO();
            signRequest.setUsername(username);
            signRequest.setPassword(password);
            signRequest.setReason(reason);
            signRequest.setLocation(location);
            signRequest.setSignatureFormat(signatureLevel);

            // Sign the document
            byte[] signedPdf = signingService.signPDF(fileBytes, signRequest);

            boolean isConverted = !Objects.equals(contentType, "application/pdf");

            String outputFilename = isConverted
                    ? getOutputFilename(originalFilenameApi)
                    : originalFilenameApi;

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            assert outputFilename != null;
            String signedFilename = outputFilename.replace(".pdf", "_signed_" + timestamp + ".pdf");
            String asciiName = Normalizer
                    .normalize(signedFilename, Normalizer.Form.NFD)
                    .replaceAll("[^\\p{ASCII}]", "");

            String encoded = URLEncoder.encode(signedFilename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            // Return signed PDF
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.add(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded);
            headers.setContentLength(signedPdf.length);

            log.info("Ky thanh cong - File: {}, User: {}", signedFilename, username);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(signedPdf);

        } catch (Exception e) {
            log.error("Ký số hỗ trợ url lỗi: ", e);
            throw e;
        }
    }

    /**
     * lấy cert để hash file
     */
    @GetMapping("/cert-chain/{username}")
    public ResponseEntity<CertificateData> getCertChain(
            @PathVariable String username,
            @RequestHeader("X-PASSWORD") String password) throws Exception {
        CertificateData chain = signingService.getCertChain(username, password);
        if (chain == null) {
            throw new SigningException("Không tìm thấy certificate chain");
        }
        return ResponseEntity.ok(chain);
    }

    /**
     * Health check
     * GET /api/sign/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        boolean ejbcaHealthy = ejbcaService.isHealthy();

        Map<String, Object> body = new HashMap<>();
        body.put("ejbcaConnected", ejbcaHealthy);
        body.put("service", "Lifetex Signing Service");
        body.put("version", "1.0.0");
        body.put("timestamp", LocalDateTime.now().toString());

        // Nếu EJBCA lỗi -> trả về 503
        if (!ejbcaHealthy) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }

        // Nếu OK -> 200
        return ResponseEntity.ok(body);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "Vui lòng chọn file để ký";
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            return "Tên file không hợp lệ";
        }

        String lowerFilename = filename.toLowerCase();
        if (!lowerFilename.endsWith(".pdf") &&
                !lowerFilename.endsWith(".docx") &&
                !lowerFilename.endsWith(".doc")) {
            return "Chỉ hỗ trợ file PDF, DOCX hoặc DOC";
        }

        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            return "File quá lớn. Kích thước tối đa: 10MB";
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            return "Không xác định được loại file";
        }

        List<String> allowedTypes = Arrays.asList(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword");

        if (!allowedTypes.contains(contentType)) {
            return "Loại file không được hỗ trợ: " + contentType;
        }

        return null;
    }

    private String getOutputFilename(MultipartFile file) {
        String original = file.getOriginalFilename();
        assert original != null;
        return "signed_" + original.replaceAll("\\.(docx?|pdf)$", ".pdf");
    }

    private String getOutputFilename(String originName) {
        return "signed_" + originName.replaceAll("\\.(docx?|pdf)$", ".pdf");
    }

    private String extractFilename(HttpHeaders headers) {
        ContentDisposition disposition = headers.getContentDisposition();
        if (disposition.getFilename() != null) {
            return disposition.getFilename();
        }
        return "unknown-file";
    }

}
