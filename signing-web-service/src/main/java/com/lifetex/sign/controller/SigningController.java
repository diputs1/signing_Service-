package com.lifetex.sign.controller;

import com.lifetex.sign.exception.SigningException;
import com.lifetex.sign.infrastructure.http.GetFileServiceClient;
import com.lifetex.sign.model.CertificateData;
import com.lifetex.sign.model.SigningSession;
import com.lifetex.sign.model.dto.*;
import com.lifetex.sign.response.*;
import com.lifetex.sign.response.HttpResponse;
import com.lifetex.sign.response.OTPResponse;
import com.lifetex.sign.response.OTPVerificationResponse;
import com.lifetex.sign.service.EJBCAService;
import com.lifetex.sign.service.EnhancedOTPService;
import com.lifetex.sign.service.DSSSigningService;
import com.lifetex.sign.service.SignLocalService;
import com.lifetex.sign.util.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/sign")
public class SigningController {

    @Autowired
    private final DSSSigningService signingService;

    @Autowired
    private final SignLocalService signLocalService;

    @Autowired
    private final EJBCAService ejbcaService;

    @Autowired
    private EnhancedOTPService otpService;

    private final GetFileServiceClient getFileService;

    private final ExecutorService signingExecutor;

    private static final int MAX_MULTI_SIGN_FILES = 20;

    public SigningController(DSSSigningService signingService, SignLocalService signLocalService,
            EJBCAService ejbcaService, GetFileServiceClient getFileService,
            @Qualifier("signingExecutor") ExecutorService signingExecutor) {
        this.signingService = signingService;
        this.signLocalService = signLocalService;
        this.ejbcaService = ejbcaService;
        this.getFileService = getFileService;
        this.signingExecutor = signingExecutor;
    }

    @GetMapping("/threads")
    public String checkThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().contains("worker"))
                .map(Thread::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Yêu cầu OTP
     * POST /api/sign/request-otp
     */
    @PostMapping("/request-otp")
    public ResponseEntity<HttpResponse<OTPResponse>> requestOTP(
            HttpServletRequest request) {

        String userName = SecurityUtils.getCurrentUsername();
        String email = SecurityUtils.getCurrentUserEmail();
        String clientId = getClientIpAddress(request);

        log.info("Yeu cau OTP cua nguoi dung: {} | IP: {}", userName, clientId);

        if (email == null || email.isEmpty()) {
            throw new SigningException("Người dùng không có email");
        } else if (userName == null || userName.isEmpty()) {
            throw new SigningException("Người dùng không có username");
        }

        // xóa otp cũ khi yêu cầu otp mới
        boolean isDelete = otpService.deleteOldOTP(userName);

        log.info("Xóa OTP cũ cho người dùng: {} | Kết quả: {}", userName, isDelete);

        HttpResponse<OTPResponse> response = otpService.generateOTP(userName, email);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Xác thực OTP
     * POST /api/sign/verify-otp
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<HttpResponse<OTPVerificationResponse>> verifyOTP(
            @Valid @RequestBody OTPVerifyRequestDTO requestBody,
            HttpServletRequest request) {

        String userName = SecurityUtils.getCurrentUsername();
        String email = SecurityUtils.getCurrentUserEmail();
        String clientId = getClientIpAddress(request);

        log.info("OTP verification from user: {} | IP: {}", userName, clientId);

        OTPVerificationResponse response = otpService.verifyOTP(userName, requestBody.getOtp());

        
        if (response.isSuccess()) {
            return ResponseFactory.success(response);
        } else {
            HttpStatus status = response.getRemainingLockoutMinutes() == 0
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.UNAUTHORIZED;
            return ResponseFactory.error(response.getMessage(), status, 401);
        }
    }

    /**
     * Ký document hỗ trợ file pdf và docx, doc
     * POST /api/sign
     */
    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> signDocument(
            @Valid @ModelAttribute WebSignRequestDTO request,
            BindingResult bindingResult,
            @RequestHeader("Token-Signing") String authorization) throws Exception {

        try {
            SigningSession session = otpService.verifySign(authorization);
            if (session == null) {
                throw new SigningException("Phiên ký không hợp lệ hoặc đã hết hạn");
            }

            MultipartFile uploadedFile = request.getFile();
            log.info("Nhan yeu cau ky - File: {}, User: {}",
                    uploadedFile.getOriginalFilename(), request.getUsername());
            String validationError = validateFile(uploadedFile);
            if (validationError != null) {
                return ResponseFactory.error(validationError, HttpStatus.BAD_REQUEST, 400);
            }
            byte[] documentBytes;

            if (Objects.equals(uploadedFile.getContentType(), "application/pdf")) {
                documentBytes = uploadedFile.getBytes();
            } else if (uploadedFile.getOriginalFilename().toLowerCase().endsWith(".xlsx")
                    || uploadedFile.getOriginalFilename().toLowerCase().endsWith(".xls")) {
                documentBytes = signLocalService.convertXlsxToPdf(uploadedFile);
            } else {
                documentBytes = signLocalService.convertDocxToPdf(uploadedFile);
            }

            boolean isUserExists = ejbcaService.endEntityExists(request.getUsername());

            if (!isUserExists) {
                boolean result = signingService.revokeCache(request.getUsername());
                if (result) {
                    log.info("Đã thu hồi .p12 (chung chi) cache cho người dùng không tồn tại: {}",
                            request.getUsername());
                }
                throw new SigningException(
                        "Người dùng: " + request.getUsername() + " không tồn tại trong hệ thống chứng chỉ");
            }

            byte[] signedPdf = signingService.signPDF(documentBytes, request);

            String signedFilename = getOutputFilename(uploadedFile.getOriginalFilename());
            String asciiName = Normalizer
                    .normalize(signedFilename, Normalizer.Form.NFD)
                    .replaceAll("[^\\p{ASCII}]", "");

            String encoded = URLEncoder.encode(signedFilename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.add(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded);
            headers.setContentLength(signedPdf.length);

            log.info("Ky thanh cong - File: {}, User: {}", signedFilename, request.getUsername());
            boolean isDeleteRateLimit = otpService.deleteRateLimit(request.getUsername());

            log.info("Xóa rate limit sau khi ký thành công cho người dùng: {} | Kết quả: {}", request.getUsername(),
                    isDeleteRateLimit);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(signedPdf);

        } catch (Exception e) {
            log.error("Signing failed", e);
            throw e;
        }
    }

    /**
     * Ký PDF document từ JSON (base64)
     * POST /api/v1/sign/pdf-json
     */
    @PostMapping(value = "/document-json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> signPDFJson(@RequestBody SignPDFJsonRequestDTO request) {
        try {
            log.info("Received PDF signing request (JSON) - User: {}", request.getUsername());

            // Decode base64 PDF
            byte[] pdfBytes = java.util.Base64.getDecoder().decode(request.getDocumentBase64());

            // Create sign request
            SignRequestDTO signRequest = new SignRequestDTO();
            signRequest.setUsername(request.getUsername());
            signRequest.setPassword(request.getPassword());
            signRequest.setSignatureFormat("PAdES");
            signRequest.setSignatureLevel(request.getSignatureLevel());
            signRequest.setReason(request.getReason());
            signRequest.setLocation(request.getLocation());
            signRequest.setContactInfo(request.getContactInfo());

            // Sign the document
            byte[] signedPdf = signingService.signPDF(pdfBytes, signRequest);

            // Return base64 encoded signed PDF
            String signedBase64 = java.util.Base64.getEncoder().encodeToString(signedPdf);

            SignPDFJsonResponseDTO response = new SignPDFJsonResponseDTO();
            response.setSignedDocumentBase64(signedBase64);
            response.setMessage("Document signed successfully");
            response.setSigningTime(LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Signing failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Signing failed: " + e.getMessage());
        }
    }

    /**
     * Ký Pdf chèn ảnh
     */

    @PostMapping(value = "/document-with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> sign(
            @RequestPart(value = "file") MultipartFile file,
            @RequestPart(value = "username") String username,
            @RequestPart(value = "password") String password,
            @RequestPart(value = "reason", required = false) String reason,
            @RequestPart(value = "location", required = false) String location,
            @RequestPart(value = "imageMetadata") String imageMetadataJson,
            @RequestPart(value = "signatureLevel", required = false) String signatureLevel,
            @RequestHeader(value = "Token-Signing") String authorization) throws Exception {

        if (imageMetadataJson == null || imageMetadataJson.trim().isEmpty()) {
            throw new SigningException("Thông tin imageMetadata không được để trống");
        }

        SigningSession session = otpService.verifySign(authorization);
        if (session == null) {
            throw new SigningException("Phiên ký không hợp lệ hoặc đã hết hạn");
        }

        ObjectMapper mapper = new ObjectMapper();
        List<ImageInsertRequest> imageRequests;
        try {
            imageRequests = mapper.readValue(imageMetadataJson, new TypeReference<List<ImageInsertRequest>>() {
            });
        } catch (Exception e) {
            throw new SigningException("Định dạng imageMetadata không hợp lệ: " + e.getMessage());
        }
        try {

            log.info("Nhan yeu cau ky chen anh - File: {}, User: {}",
                    file.getOriginalFilename(), username);
            String validationError = validateFile(file);
            if (validationError != null) {
                return ResponseFactory.error(validationError, HttpStatus.BAD_REQUEST, 400);
            }

            byte[] documentBytes;

            if (Objects.equals(file.getContentType(), "application/pdf")) {
                documentBytes = file.getBytes();
            } else if (file.getOriginalFilename().toLowerCase().endsWith(".xlsx")
                    || file.getOriginalFilename().toLowerCase().endsWith(".xls")) {
                documentBytes = signLocalService.convertXlsxToPdf(file);
            } else {
                documentBytes = signLocalService.convertDocxToPdf(file);
            }

            boolean isUserExists = ejbcaService.endEntityExists(username);

            if (!isUserExists) {
                boolean result = signingService.revokeCache(username);
                if (result) {
                    log.info("Đã thu hồi .p12 cache cho người dùng không tồn tại: {}", username);
                }
                throw new SigningException("Người dùng: " + username + " không tồn tại trong hệ thống chứng chỉ");
            }

            // Ký chèn ảnh dùng hàm dùng chung signPdfWithImage
            byte[] currentPdf = signLocalService.signPdfWithImage(documentBytes, username, password, reason, location,
                    signatureLevel,
                    imageRequests);

            // Generate filename
            String signedFilename = getOutputFilename(file.getOriginalFilename());
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
            headers.setContentLength(currentPdf.length);

            log.info("Ky thanh cong - File: {}, User: {}", signedFilename, username);
            boolean isDeleteRateLimit = otpService.deleteRateLimit(username);

            log.info("Xóa rate limit sau khi ký chèn ảnh thành công cho người dùng: {} | Kết quả: {}", username,
                    isDeleteRateLimit);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(currentPdf);

        } catch (Exception e) {
            log.error("Signing failed", e);
            throw e;
        }
    }

    @PostMapping(value = "/documents-with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<HttpResponse<MultiSignResponseDTO>> signMultipleDocumentsWithImage(
            @RequestPart(value = "files") MultipartFile[] files,
            @RequestPart(value = "username") String username,
            @RequestPart(value = "password") String password,
            @RequestPart(value = "imageMetadata") String imageMetadataJson,
            @RequestPart(value = "reason", required = false) String reason,
            @RequestPart(value = "location", required = false) String location,
            @RequestPart(value = "signatureLevel", required = false) String signatureLevel,
            @RequestHeader(value = "Token-Signing") String authorization) throws Exception {

        SigningSession session = otpService.verifySign(authorization);
        if (session == null) {
            throw new SigningException("Phiên ký không hợp lệ hoặc đã hết hạn");
        }

        List<ImageInsertRequest> imageRequests;
        try {
            imageRequests = new ObjectMapper().readValue(imageMetadataJson,
                    new TypeReference<List<ImageInsertRequest>>() {
                    });
        } catch (Exception e) {
            throw new SigningException("imageMetadata không hợp lệ: " + e.getMessage());
        }
        if (imageRequests == null || imageRequests.isEmpty()) {
            throw new SigningException("imageMetadata phải có ít nhất một phần tử (keyword + ảnh)");
        }

        if (files == null || files.length == 0) {
            throw new SigningException("Danh sách file trống");
        }
        if (files.length > MAX_MULTI_SIGN_FILES) {
            throw new SigningException("Vượt quá số file cho phép (tối đa " + MAX_MULTI_SIGN_FILES + ")");
        }

        boolean isUserExists = ejbcaService.endEntityExists(username);
        if (!isUserExists) {
            boolean result = signingService.revokeCache(username);
            if (result) {
                log.info("Đã thu hồi .p12 cache cho người dùng không tồn tại (multi with image): {}", username);
            }
            throw new SigningException("Người dùng: " + username + " không tồn tại trong hệ thống chứng chỉ");
        }

        final List<ImageInsertRequest> imageRequestsFinal = imageRequests;
        final String reasonFinal = reason;
        final String locationFinal = location;
        final String signatureLevelFinal = signatureLevel != null ? signatureLevel : "B";

        // Chuẩn bị danh sách (index, filename, pdfBytes)
        List<DocEntry> entries = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            String validationError = validateFile(file);
            if (validationError != null) {
                entries.add(DocEntry.failed(i, file.getOriginalFilename(), validationError));
                continue;
            }
            byte[] pdfBytes;
            try {
                if (!Objects.equals(file.getContentType(), "application/pdf")) {
                    pdfBytes = signLocalService.convertDocxToPdf(file);
                } else {
                    pdfBytes = file.getBytes();
                }
            } catch (Exception e) {
                log.warn("Convert file sang PDF lỗi: index={}, file={}", i, file.getOriginalFilename(), e);
                entries.add(DocEntry.failed(i, file.getOriginalFilename(),
                        "Không thể chuyển đổi sang PDF: " + e.getMessage()));
                continue;
            }
            String filename = file.getOriginalFilename();
            if (filename == null || filename.trim().isEmpty()) {
                filename = "document_" + (i + 1) + ".pdf";
            }
            filename = getOutputFilename(filename);
            entries.add(DocEntry.ok(i, filename, pdfBytes));
        }

        // Gửi từng task ký chèn ảnh vào executor
        List<Future<SignedDocumentItemDTO>> futures = new ArrayList<>();
        for (DocEntry entry : entries) {
            if (entry.getError() != null) {
                futures.add(null);
                continue;
            }
            final byte[] pdfBytes = entry.getPdfBytes();
            final int index = entry.getIndex();
            final String filename = entry.getFilename();
            Future<SignedDocumentItemDTO> future = signingExecutor.submit(() -> {
                try {
                    byte[] signed = signLocalService.signPdfWithImage(pdfBytes, username, password,
                            reasonFinal, locationFinal, signatureLevelFinal, imageRequestsFinal);
                    String base64 = Base64.getEncoder().encodeToString(signed);
                    return SignedDocumentItemDTO.builder()
                            .index(index)
                            .filename(filename)
                            .status(SignedDocumentItemDTO.STATUS_SUCCESS)
                            .signedBase64(base64)
                            .build();
                } catch (Exception e) {
                    log.warn("Ký chèn ảnh file lỗi: index={}, file={}", index, filename, e);
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    return SignedDocumentItemDTO.builder()
                            .index(index)
                            .filename(filename)
                            .status(SignedDocumentItemDTO.STATUS_FAILED)
                            .error(msg)
                            .build();
                }
            });
            futures.add(future);
        }

        // Gom kết quả theo thứ tự
        List<SignedDocumentItemDTO> documents = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            DocEntry entry = entries.get(i);
            if (entry.getError() != null) {
                documents.add(SignedDocumentItemDTO.builder()
                        .index(entry.getIndex())
                        .filename(entry.getFilename())
                        .status(SignedDocumentItemDTO.STATUS_FAILED)
                        .error(entry.getError())
                        .build());
            } else {
                try {
                    documents.add(futures.get(i).get());
                } catch (Exception e) {
                    log.warn("Lấy kết quả ký chèn ảnh lỗi: index={}", entry.getIndex(), e);
                    documents.add(SignedDocumentItemDTO.builder()
                            .index(entry.getIndex())
                            .filename(entry.getFilename())
                            .status(SignedDocumentItemDTO.STATUS_FAILED)
                            .error(e.getMessage() != null ? e.getMessage() : "Lỗi khi xử lý ký")
                            .build());
                }
            }
        }

        int succeeded = (int) documents.stream().filter(d -> SignedDocumentItemDTO.STATUS_SUCCESS.equals(d.getStatus()))
                .count();
        int failed = documents.size() - succeeded;
        String overallStatus = failed == 0 ? MultiSignResponseDTO.STATUS_ALL_SUCCESS
                : (succeeded == 0 ? MultiSignResponseDTO.STATUS_ALL_FAILED
                        : MultiSignResponseDTO.STATUS_PARTIAL_SUCCESS);

        MultiSignResponseDTO response = MultiSignResponseDTO.builder()
                .total(documents.size())
                .succeeded(succeeded)
                .failed(failed)
                .status(overallStatus)
                .documents(documents)
                .build();

        if (succeeded > 0) {
            boolean deleted = otpService.deleteRateLimit(username);
            log.info("Xóa rate limit sau khi ký multi with image thành công cho user: {} | Kết quả: {}", username,
                    deleted);
        }

        log.info("Ký multi with image hoàn tất - total={}, succeeded={}, failed={}, user={}", documents.size(),
                succeeded, failed, username);
        return ResponseFactory.success("Ký số thành công cho người dùng " + username, response);
    }

    /**
     * Ký nháy
     */

    @PostMapping(value = "/document-initial-signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> InitialSignature(
            @RequestPart(value = "file") MultipartFile file,
            @RequestPart(value = "username") String username,
            @RequestPart(value = "password") String password,
            @RequestPart(value = "reason", required = false) String reason,
            @RequestPart(value = "location", required = false) String location,
            @RequestPart(value = "keyword", required = false) String keyword,
            @RequestPart(value = "base64Image", required = false) String base64Image,
            @RequestPart(value = "signatureLevel", required = false) String signatureLevel,
            @RequestHeader("Token-Signing") String authorization) throws Exception {
        try {
            SigningSession session = otpService.verifySign(authorization);
            if (session == null) {
                throw new SigningException("Phiên ký không hợp lệ hoặc đã hết hạn");
            }

            String keywordSign = keyword != null ? keyword : "./.";
            log.info("Nhan yeu cau ky nhay - File: {}, User: {}, Keyword: {}",
                    file.getOriginalFilename(), username, keywordSign);

            String validationError = validateFile(file);
            if (validationError != null) {
                return ResponseFactory.error(validationError, HttpStatus.BAD_REQUEST, 400);
            }

            byte[] documentBytes;
            if (Objects.equals(file.getContentType(), "application/pdf")) {
                documentBytes = file.getBytes();
            } else if (file.getOriginalFilename().toLowerCase().endsWith(".xlsx")
                    || file.getOriginalFilename().toLowerCase().endsWith(".xls")) {
                documentBytes = signLocalService.convertXlsxToPdf(file);
            } else {
                documentBytes = signLocalService.convertDocxToPdf(file);
            }

            boolean isUserExists = ejbcaService.endEntityExists(username);
            if (!isUserExists) {
                boolean result = signingService.revokeCache(username);
                if (result) {
                    log.info("Đã thu hồi .p12 cache cho người dùng không tồn tại (ký nháy): {}", username);
                }
                throw new SigningException("Người dùng: " + username + " không tồn tại trong hệ thống chứng chỉ");
            }

            // Build ImageInsertRequest từ keyword + base64Image rồi gọi hàm dùng chung
            ImageInsertRequest imgReq = new ImageInsertRequest(keywordSign, base64Image, 0f, 0f);
            byte[] currentPdf = signLocalService.signPdfInitialSignature(documentBytes, username, password, reason,
                    location,
                    signatureLevel, Collections.singletonList(imgReq));

            // Generate filename
            String signedFilename = getOutputFilename(file.getOriginalFilename());
            String asciiName = Normalizer
                    .normalize(signedFilename, Normalizer.Form.NFD)
                    .replaceAll("[^\\p{ASCII}]", "");
            String encoded = URLEncoder.encode(signedFilename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.add(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded);
            headers.setContentLength(currentPdf.length);

            log.info("Ky thanh cong ky nhay - File: {}, User: {}", signedFilename, username);
            boolean isDeleteRateLimit = otpService.deleteRateLimit(username);
            log.info("Xóa rate limit sau khi ký nháy thành công cho người dùng: {} | Kết quả: {}", username,
                    isDeleteRateLimit);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(currentPdf);

        } catch (Exception e) {
            log.error("Ký nháy thất bại", e);
            throw e;
        }
    }

    /**
     * Ký nháy nhiều file (cùng imageMetadata cho mọi file). Multi-thread, trả về
     * list base64 kèm status, giữ thứ tự.
     */
    @PostMapping(value = "/documents-initial-signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<HttpResponse<MultiSignResponseDTO>> signMultipleDocumentsInitialSignature(
            @RequestPart(value = "files") MultipartFile[] files,
            @RequestPart(value = "username") String username,
            @RequestPart(value = "password") String password,
            @RequestPart(value = "keyword", required = false) String keyword,
            @RequestPart(value = "base64Image", required = false) String base64Image,
            @RequestPart(value = "reason", required = false) String reason,
            @RequestPart(value = "location", required = false) String location,
            @RequestPart(value = "signatureLevel", required = false) String signatureLevel,
            @RequestHeader(value = "Token-Signing") String authorization) throws Exception {

        SigningSession session = otpService.verifySign(authorization);
        if (session == null) {
            throw new SigningException("Phiên ký không hợp lệ hoặc đã hết hạn");
        }

        if (files == null || files.length == 0) {
            throw new SigningException("Danh sách file trống");
        }
        if (files.length > MAX_MULTI_SIGN_FILES) {
            throw new SigningException("Vượt quá số file cho phép (tối đa " + MAX_MULTI_SIGN_FILES + ")");
        }

        boolean isUserExists = ejbcaService.endEntityExists(username);
        if (!isUserExists) {
            boolean result = signingService.revokeCache(username);
            if (result) {
                log.info("Đã thu hồi .p12 cache cho người dùng không tồn tại (multi initial-signature): {}", username);
            }
            throw new SigningException("Người dùng: " + username + " không tồn tại trong hệ thống chứng chỉ");
        }

        // Build ImageInsertRequest từ keyword + base64Image (giống
        // /document-initial-signature)
        String keywordSign = keyword != null ? keyword : "./.";
        final List<ImageInsertRequest> imageRequestsFinal = Collections.singletonList(
                new ImageInsertRequest(keywordSign, base64Image, 0f, 0f));
        final String reasonFinal = reason;
        final String locationFinal = location;
        final String signatureLevelFinal = signatureLevel != null ? signatureLevel : "B";

        List<DocEntry> entries = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            String validationError = validateFile(file);
            if (validationError != null) {
                entries.add(DocEntry.failed(i, file.getOriginalFilename(), validationError));
                continue;
            }
            byte[] pdfBytes;
            try {
                if (!Objects.equals(file.getContentType(), "application/pdf")) {
                    pdfBytes = signLocalService.convertDocxToPdf(file);
                } else {
                    pdfBytes = file.getBytes();
                }
            } catch (Exception e) {
                log.warn("Convert file sang PDF lỗi: index={}, file={}", i, file.getOriginalFilename(), e);
                entries.add(DocEntry.failed(i, file.getOriginalFilename(),
                        "Không thể chuyển đổi sang PDF: " + e.getMessage()));
                continue;
            }
            String filename = file.getOriginalFilename();
            if (filename == null || filename.trim().isEmpty()) {
                filename = "document_" + (i + 1) + ".pdf";
            }
            filename = getOutputFilename(filename);
            entries.add(DocEntry.ok(i, filename, pdfBytes));
        }

        List<Future<SignedDocumentItemDTO>> futures = new ArrayList<>();
        for (DocEntry entry : entries) {
            if (entry.getError() != null) {
                futures.add(null);
                continue;
            }
            final byte[] pdfBytes = entry.getPdfBytes();
            final int index = entry.getIndex();
            final String filename = entry.getFilename();
            Future<SignedDocumentItemDTO> future = signingExecutor.submit(() -> {
                try {
                    byte[] signed = signLocalService.signPdfInitialSignature(pdfBytes, username, password,
                            reasonFinal, locationFinal, signatureLevelFinal, imageRequestsFinal);
                    String base64 = Base64.getEncoder().encodeToString(signed);
                    return SignedDocumentItemDTO.builder()
                            .index(index)
                            .filename(filename)
                            .status(SignedDocumentItemDTO.STATUS_SUCCESS)
                            .signedBase64(base64)
                            .build();
                } catch (Exception e) {
                    log.warn("Ký nháy file lỗi: index={}, file={}", index, filename, e);
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    return SignedDocumentItemDTO.builder()
                            .index(index)
                            .filename(filename)
                            .status(SignedDocumentItemDTO.STATUS_FAILED)
                            .error(msg)
                            .build();
                }
            });
            futures.add(future);
        }

        List<SignedDocumentItemDTO> documents = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            DocEntry entry = entries.get(i);
            if (entry.getError() != null) {
                documents.add(SignedDocumentItemDTO.builder()
                        .index(entry.getIndex())
                        .filename(entry.getFilename())
                        .status(SignedDocumentItemDTO.STATUS_FAILED)
                        .error(entry.getError())
                        .build());
            } else {
                try {
                    documents.add(futures.get(i).get());
                } catch (Exception e) {
                    log.warn("Lấy kết quả ký nháy lỗi: index={}", entry.getIndex(), e);
                    documents.add(SignedDocumentItemDTO.builder()
                            .index(entry.getIndex())
                            .filename(entry.getFilename())
                            .status(SignedDocumentItemDTO.STATUS_FAILED)
                            .error(e.getMessage() != null ? e.getMessage() : "Lỗi khi xử lý ký")
                            .build());
                }
            }
        }

        int succeeded = (int) documents.stream().filter(d -> SignedDocumentItemDTO.STATUS_SUCCESS.equals(d.getStatus()))
                .count();
        int failed = documents.size() - succeeded;
        String overallStatus = failed == 0 ? MultiSignResponseDTO.STATUS_ALL_SUCCESS
                : (succeeded == 0 ? MultiSignResponseDTO.STATUS_ALL_FAILED
                        : MultiSignResponseDTO.STATUS_PARTIAL_SUCCESS);

        MultiSignResponseDTO response = MultiSignResponseDTO.builder()
                .total(documents.size())
                .succeeded(succeeded)
                .failed(failed)
                .status(overallStatus)
                .documents(documents)
                .build();

        if (succeeded > 0) {
            boolean deleted = otpService.deleteRateLimit(username);
            log.info("Xóa rate limit sau khi ký multi initial-signature thành công cho user: {} | Kết quả: {}",
                    username, deleted);
        }

        log.info("Ký multi initial-signature hoàn tất - total={}, succeeded={}, failed={}, user={}", documents.size(),
                succeeded, failed, username);
        return ResponseFactory.success("Ký số thành công cho người dùng " + username, response);
    }

    /**
     * Ký nháy formal theo format Nơi nhận: ... .
     */
    @PostMapping(value = "/document-formal-initial-signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> formalInitialSignature(
            @RequestPart(value = "file") MultipartFile file,
            @RequestPart(value = "username") String username,
            @RequestPart(value = "password") String password,
            @RequestPart(value = "reason", required = false) String reason,
            @RequestPart(value = "location", required = false) String location,
            @RequestPart(value = "base64Image", required = false) String base64Image,
            @RequestPart(value = "signatureLevel", required = false) String signatureLevel,
            @RequestHeader("Token-Signing") String authorization) throws Exception {
        try {
            SigningSession session = otpService.verifySign(authorization);
            if (session == null) {
                throw new SigningException("Phiên ký không hợp lệ hoặc đã hết hạn");
            }

            log.info("Nhan yeu cau ky nhay (formal) - File: {}, User: {}",
                    file.getOriginalFilename(), username);

            String validationError = validateFile(file);
            if (validationError != null) {
                return ResponseFactory.error(validationError, HttpStatus.BAD_REQUEST, 400);
            }

            byte[] documentBytes;
            if (Objects.equals(file.getContentType(), "application/pdf")) {
                documentBytes = file.getBytes();
            } else if (file.getOriginalFilename().toLowerCase().endsWith(".xlsx")
                    || file.getOriginalFilename().toLowerCase().endsWith(".xls")) {
                documentBytes = signLocalService.convertXlsxToPdf(file);
            } else {
                documentBytes = signLocalService.convertDocxToPdf(file);
            }

            boolean isUserExists = ejbcaService.endEntityExists(username);
            if (!isUserExists) {
                boolean result = signingService.revokeCache(username);
                if (result) {
                    log.info("Đã thu hồi .p12 cache cho người dùng không tồn tại (formal ky nhay): {}", username);
                }
                throw new SigningException("Người dùng: " + username + " không tồn tại trong hệ thống chứng chỉ");
            }

            byte[] currentPdf = signLocalService.signPdfFormalInitialSignature(documentBytes, username, password,
                    reason, location,
                    signatureLevel, base64Image);

            String signedFilename = getOutputFilename(file.getOriginalFilename());
            String asciiName = Normalizer
                    .normalize(signedFilename, Normalizer.Form.NFD)
                    .replaceAll("[^\\p{ASCII}]", "");
            String encoded = URLEncoder.encode(signedFilename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.add(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded);
            headers.setContentLength(currentPdf.length);

            log.info("Ky thanh cong formal ky nhay - File: {}, User: {}", signedFilename, username);
            boolean isDeleteRateLimit = otpService.deleteRateLimit(username);
            log.info("Xóa rate limit sau khi ký nháy formal thành công cho người dùng: {} | Kết quả: {}", username,
                    isDeleteRateLimit);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(currentPdf);

        } catch (Exception e) {
            log.error("Ký nháy formal thất bại", e);
            throw e;
        }
    }

    /**
     * Ký formal nháy cho nhiều file (documents-formal-initial-signature).
     */
    @PostMapping(value = "/documents-formal-initial-signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<HttpResponse<MultiSignResponseDTO>> documentsFormalInitialSignature(
            @RequestPart(value = "files") MultipartFile[] files,
            @RequestPart(value = "username") String username,
            @RequestPart(value = "password") String password,
            @RequestPart(value = "reason", required = false) String reason,
            @RequestPart(value = "location", required = false) String location,
            @RequestPart(value = "base64Image", required = false) String base64Image,
            @RequestPart(value = "signatureLevel", required = false) String signatureLevel,
            @RequestHeader(value = "Token-Signing") String authorization) throws Exception {

        SigningSession session = otpService.verifySign(authorization);
        if (session == null) {
            throw new SigningException("Phiên ký không hợp lệ hoặc đã hết hạn");
        }

        if (files == null || files.length == 0) {
            throw new SigningException("Danh sách file trống");
        }
        if (files.length > MAX_MULTI_SIGN_FILES) {
            throw new SigningException("Vượt quá số file cho phép (tối đa " + MAX_MULTI_SIGN_FILES + ")");
        }

        boolean isUserExists = ejbcaService.endEntityExists(username);
        if (!isUserExists) {
            boolean result = signingService.revokeCache(username);
            if (result) {
                log.info("Đã thu hồi .p12 cache cho người dùng không tồn tại (multi formal): {}", username);
            }
            throw new SigningException("Người dùng: " + username + " không tồn tại trong hệ thống chứng chỉ");
        }

        final String reasonFinal = reason;
        final String locationFinal = location;
        final String signatureLevelFinal = signatureLevel != null ? signatureLevel : "B";

        List<DocEntry> entries = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            String validationError = validateFile(file);
            if (validationError != null) {
                entries.add(DocEntry.failed(i, file.getOriginalFilename(), validationError));
                continue;
            }
            byte[] pdfBytes;
            try {
                if (!Objects.equals(file.getContentType(), "application/pdf")) {
                    pdfBytes = signLocalService.convertDocxToPdf(file);
                } else {
                    pdfBytes = file.getBytes();
                }
            } catch (Exception e) {
                log.warn("Convert file sang PDF lỗi: index={}, file={}", i, file.getOriginalFilename(), e);
                entries.add(DocEntry.failed(i, file.getOriginalFilename(),
                        "Không thể chuyển đổi sang PDF: " + e.getMessage()));
                continue;
            }
            String filename = file.getOriginalFilename();
            if (filename == null || filename.trim().isEmpty()) {
                filename = "document_" + (i + 1) + ".pdf";
            }
            filename = getOutputFilename(filename);
            entries.add(DocEntry.ok(i, filename, pdfBytes));
        }

        List<Future<SignedDocumentItemDTO>> futures = new ArrayList<>();
        for (DocEntry entry : entries) {
            if (entry.getError() != null) {
                futures.add(null);
                continue;
            }
            final byte[] pdfBytes = entry.getPdfBytes();
            final int index = entry.getIndex();
            final String filename = entry.getFilename();

            Future<SignedDocumentItemDTO> future = signingExecutor.submit(() -> {
                try {
                    byte[] signed = signLocalService.signPdfFormalInitialSignature(pdfBytes, username, password,
                            reasonFinal, locationFinal, signatureLevelFinal, base64Image);
                    String base64 = Base64.getEncoder().encodeToString(signed);
                    return SignedDocumentItemDTO.builder()
                            .index(index)
                            .filename(filename)
                            .status(SignedDocumentItemDTO.STATUS_SUCCESS)
                            .signedBase64(base64)
                            .build();
                } catch (Exception e) {
                    log.warn("Ký formal nháy file lỗi: index={}, file={}", index, filename, e);
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    return SignedDocumentItemDTO.builder()
                            .index(index)
                            .filename(filename)
                            .status(SignedDocumentItemDTO.STATUS_FAILED)
                            .error(msg)
                            .build();
                }
            });
            futures.add(future);
        }

        List<SignedDocumentItemDTO> documents = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            DocEntry entry = entries.get(i);
            if (entry.getError() != null) {
                documents.add(SignedDocumentItemDTO.builder()
                        .index(entry.getIndex())
                        .filename(entry.getFilename())
                        .status(SignedDocumentItemDTO.STATUS_FAILED)
                        .error(entry.getError())
                        .build());
            } else {
                try {
                    Future<SignedDocumentItemDTO> future = futures.get(i);
                    SignedDocumentItemDTO docRes = future.get();
                    documents.add(docRes);
                } catch (Exception ex) {
                    log.error("Lấy kết quả từ future lỗi, file={}", entry.getFilename(), ex);
                    documents.add(SignedDocumentItemDTO.builder()
                            .index(entry.getIndex())
                            .filename(entry.getFilename())
                            .status(SignedDocumentItemDTO.STATUS_FAILED)
                            .error("Lỗi nội bộ khi lấy kết quả ký: " + ex.getMessage())
                            .build());
                }
            }
        }

        MultiSignResponseDTO response = new MultiSignResponseDTO();
        response.setDocuments(documents);
        response.setTotal(documents.size());
        response.setSuccessCount((int) documents.stream()
                .filter(d -> SignedDocumentItemDTO.STATUS_SUCCESS.equals(d.getStatus())).count());
        response.setFailedCount((int) documents.stream()
                .filter(d -> SignedDocumentItemDTO.STATUS_FAILED.equals(d.getStatus())).count());

        boolean isDeleteRateLimit = otpService.deleteRateLimit(username);
        log.info("Xóa rate limit sau khi ký (multi formal) cho người dùng: {} | Kết quả: {}", username,
                isDeleteRateLimit);

        return ResponseFactory.success("Ký số thành công cho người dùng " + username, response);
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

            if ("application/pdf".equals(contentType)) {
                // do nothing
            } else if (originalFilenameApi.toLowerCase().endsWith(".xlsx")
                    || originalFilenameApi.toLowerCase().endsWith(".xls")) {
                fileBytes = signLocalService.convertXlsxToPdf(fileBytes);
            } else {
                fileBytes = signLocalService.convertDocxToPdf(fileBytes);
            }

            boolean isUserExists = ejbcaService.endEntityExists(username);

            if (!isUserExists) {
                boolean result = signingService.revokeCache(username);
                if (result) {
                    log.info("Đã thu hồi .p12 (chung chi) khi ký hỗ trợ url cache cho người dùng không tồn tại: {}",
                            username);
                }
                throw new SigningException("Người dùng: " + username + " không tồn tại trong hệ thống chứng chỉ");
            }

            SignRequestDTO signRequest = new SignRequestDTO();
            signRequest.setUsername(username);
            signRequest.setPassword(password);
            signRequest.setReason(reason);
            signRequest.setLocation(location);
            signRequest.setSignatureFormat(signatureLevel);

            // Sign the document
            byte[] signedPdf = signingService.signPDF(fileBytes, signRequest);

            String signedFilename = getOutputFilename(originalFilenameApi);
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

            log.info("Ky thanh cong hỗ trợ url - File: {}, User: {}", signedFilename, username);
            boolean isDeleteRateLimit = otpService.deleteRateLimit(username);

            log.info("Xóa rate limit sau khi ký hỗ trợ url thành công cho người dùng: {} | Kết quả: {}", username,
                    isDeleteRateLimit);

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
     * Thu hồi .p12 cache của user
     * DELETE /api/sign/revoke-p12/{username}
     */
    @DeleteMapping("/revoke-p12/{username}")
    public ResponseEntity<HttpResponse<String>> revokeP12Cache(
            @PathVariable("username") String username) {
        log.info("Revoking .p12 cache for user: {}", username);
        boolean revoked = signingService.revokeCache(username);
        if (revoked) {
            return ResponseFactory.success("Revoked .p12 cache for user: " + username);
        } else {
            return ResponseFactory.error("No .p12 cache found for user: " + username, HttpStatus.NOT_FOUND, 404);
        }
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
                !lowerFilename.endsWith(".doc") &&
                !lowerFilename.endsWith(".xlsx") &&
                !lowerFilename.endsWith(".xls")) {
            return "Chỉ hỗ trợ file PDF, DOCX, DOC, XLSX hoặc XLS";
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
                "application/msword",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel");

        if (!allowedTypes.contains(contentType)) {
            return "Loại file không được hỗ trợ: " + contentType;
        }

        return null;
    }

    private String getOutputFilename(MultipartFile file) {
        return getOutputFilename(file.getOriginalFilename());
    }

    private String getOutputFilename(String originName) {
        if (originName == null || originName.trim().isEmpty()) {
            originName = "document.pdf";
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        if (!originName.toLowerCase().matches(".*\\.(docx?|xlsx?|pdf)$")) {
            return originName + "_signed_" + timestamp + ".pdf";
        }
        return originName.replaceAll("(?i)\\.(docx?|xlsx?|pdf)$", "_signed_" + timestamp + ".pdf");
    }

    private String extractFilename(HttpHeaders headers) {
        ContentDisposition disposition = headers.getContentDisposition();
        if (disposition.getFilename() != null) {
            return disposition.getFilename();
        }
        return "unknown-file";
    }

}
