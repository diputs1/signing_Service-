package com.lifetex.sign.service;

import com.lifetex.sign.model.dto.ImageInsertRequest;
import com.lifetex.sign.util.PDFKeywordScanner;
import com.lifetex.sign.util.PDFFormalSignatureScanner;
import com.lifetex.sign.model.domain.RectPosition;
import com.aspose.words.Document;
import com.aspose.words.PdfCompliance;
import com.aspose.words.PdfSaveOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import com.aspose.cells.SaveFormat;
import com.aspose.cells.Workbook;
import com.lifetex.sign.model.domain.SignaturePlacement;
import com.lifetex.sign.model.domain.SignaturePosition;
import com.lifetex.sign.model.dto.SignRequestDTO;
import com.lifetex.sign.exception.SigningException;

@Slf4j
@Service
public class SignLocalService {

    private final DSSSigningService signingService;

    public SignLocalService(DSSSigningService signingService) {
        this.signingService = signingService;
    }

    public byte[] insertImagesIntoPdf(byte[] pdfBytes, List<ImageInsertRequest> imageRequests) throws Exception {

        try (PDDocument document = PDDocument.load(pdfBytes)) {

            // Tập hợp tất cả keyword
            List<String> allKeywords = imageRequests.stream()
                    .flatMap(req -> Arrays.stream(req.getKeyWord().split(",")))
                    .map(String::trim)
                    .toList();

            // Scan PDF 1 lần duy nhất
            PDFKeywordScanner scanner = new PDFKeywordScanner(allKeywords);
            scanner.setStartPage(1);
            scanner.setEndPage(document.getNumberOfPages());
            scanner.getText(document);

            // Chèn ảnh
            for (ImageInsertRequest req : imageRequests) {

                byte[] imgBytes = Base64.getDecoder().decode(req.getImagesBase());
                PDImageXObject image = PDImageXObject.createFromByteArray(
                        document, imgBytes, "inserted-image");

                String key = req.getKeyWord();
                List<RectPosition> positions = scanner.getKeywordPositions().get(key);

                if (positions == null || positions.isEmpty())
                    continue;

                for (RectPosition pos : positions) {
                    PDPage pageObj = document.getPage(pos.getPage() - 1);
                    float pageHeight = pageObj.getMediaBox().getHeight();
                    float adjustedX = pos.getX();
                    float adjustedY = pageHeight - pos.getY() - pos.getHeight() - 50;

                    float imgWidthPt = req.getWidth() * 72f / 96f;
                    float imgHeightPt = req.getHeight() * 72f / 96f;

                    System.out.printf("[DBG] key at X=%.2f Y=%.2f W=%.2f H=%.2f%n", pos.getX(), pos.getY(),
                            pos.getWidth(), pos.getHeight());

                    try (PDPageContentStream cs = new PDPageContentStream(
                            document,
                            document.getPage(pos.getPage() - 1),
                            PDPageContentStream.AppendMode.APPEND,
                            true)) {
                        cs.drawImage(image, adjustedX, adjustedY, imgWidthPt, imgHeightPt);
                        // draw image bbox (blue)
                        // cs.setNonStrokingColor(Color.BLUE);
                        // cs.addRect(adjustedX, adjustedY, imgWidthPt, imgHeightPt);
                        // cs.fill();

                    }
                    // System.out.printf(
                    // "[%s] Page=%d X=%.2f Y=%.2f W=%.2f H=%.2f%n",
                    // key, pos.getPage(), pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight()
                    // );

                }
            }

            ByteArrayOutputStream baoS = new ByteArrayOutputStream();
            document.save(baoS);
            document.close();

            return baoS.toByteArray();
        } catch (Exception e) {
            log.error("Loi khi chen anh chu ky vao file pdf: {}", e.getMessage());
            throw e;
        }
    }

    public byte[] insertImagesToInitialSignature(byte[] pdfBytes, ImageInsertRequest imageRequests) throws IOException {
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            String keyWord = imageRequests.getKeyWord();

            PDFKeywordScanner scanner = new PDFKeywordScanner(Collections.singletonList(keyWord));
            scanner.setStartPage(1);
            scanner.setEndPage(document.getNumberOfPages());
            scanner.getText(document);

            byte[] imgBytes = Base64.getDecoder().decode(imageRequests.getImagesBase());
            PDImageXObject image = PDImageXObject.createFromByteArray(
                    document, imgBytes, "inserted-image");

            List<RectPosition> positions = scanner.getKeywordPositions().get(keyWord);

            if (positions == null || positions.isEmpty())
                return pdfBytes;
            for (RectPosition pos : positions) {
                PDPage pageObj = document.getPage(pos.getPage() - 1);
                float pageHeight = pageObj.getMediaBox().getHeight();
                float adjustedX = pos.getX() + 10;
                float adjustedY = pageHeight - pos.getY() - pos.getHeight() + 5;

                float imgWidthPt = imageRequests.getWidth() * 72f / 96f;
                float imgHeightPt = imageRequests.getHeight() * 72f / 96f;

                System.out.printf("[DBG] key at X=%.2f Y=%.2f W=%.2f H=%.2f%n", pos.getX(), pos.getY(), pos.getWidth(),
                        pos.getHeight());

                try (PDPageContentStream cs = new PDPageContentStream(
                        document,
                        document.getPage(pos.getPage() - 1),
                        PDPageContentStream.AppendMode.APPEND,
                        true)) {
                    cs.drawImage(image, adjustedX, adjustedY, imgWidthPt, imgHeightPt);
                    // draw image bbox (blue)
                    // cs.setNonStrokingColor(Color.BLUE);
                    // cs.addRect(adjustedX, adjustedY, imgWidthPt, imgHeightPt);
                    // cs.fill();

                }
            }
            ByteArrayOutputStream baoS = new ByteArrayOutputStream();
            document.save(baoS);
            document.close();

            return baoS.toByteArray();

        } catch (Exception e) {
            log.error("Loi khi chen ký nhay vao van ban");
            throw e;
        }
    }

    public List<RectPosition> getKeywordPositionsInPdf(byte[] pdfBytes, String keyword) throws IOException {
        try (PDDocument document = PDDocument.load(pdfBytes)) {

            PDFKeywordScanner scanner = new PDFKeywordScanner(Collections.singletonList(keyword));
            scanner.setStartPage(1);
            scanner.setEndPage(document.getNumberOfPages());
            scanner.getText(document);

            List<RectPosition> positions = scanner.getKeywordPositions().get(keyword);

            if (positions != null) {
                // log.info("Tim thay {} vi tri cho keyword '{}' trong PDF", positions.size(),
                // keyword);
                return positions;
            }

            log.info("Khong tim thay keyword '{}' trong PDF", keyword);
            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Loi khi lay vi tri keyword trong pdf: {}", e.getMessage());
            throw e;
        }
    }

    public RectPosition getFormalInitialSignaturePosition(byte[] pdfBytes) throws IOException {
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDFFormalSignatureScanner scanner = new PDFFormalSignatureScanner();
            scanner.setStartPage(1);
            scanner.setEndPage(document.getNumberOfPages());
            scanner.getText(document);
            return scanner.getLastDotPosition();
        } catch (Exception e) {
            log.error("Loi khi lay vi tri ky nhay chinh thuc trong pdf: {}", e.getMessage());
            throw e;
        }
    }

    public byte[] convertDocxToPdf(MultipartFile file) throws Exception {
        try {
            Document doc = new Document(file.getInputStream());

            // Thiết lập options
            PdfSaveOptions options = new PdfSaveOptions();
            options.setEmbedFullFonts(true);
            options.setCompliance(PdfCompliance.PDF_17);

            // Convert sang PDF và trả về byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            doc.save(outputStream, options);

            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Loi khi chuyen docx sang pdf: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể chuyển đổi file DOCX sang PDF", e);
        }
    }

    public byte[] convertDocxToPdf(byte[] fileBytes) throws Exception {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Document doc = new Document(inputStream);

            PdfSaveOptions options = new PdfSaveOptions();
            options.setEmbedFullFonts(true);
            options.setCompliance(PdfCompliance.PDF_17);

            doc.save(outputStream, options);

            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Lỗi khi chuyển DOCX sang PDF", e);
            throw new RuntimeException("Không thể chuyển đổi file DOCX sang PDF", e);
        }
    }

    public byte[] convertXlsxToPdf(MultipartFile file) throws Exception {
        try {
            Workbook workbook = new Workbook(file.getInputStream());
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.save(outputStream, SaveFormat.PDF);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Loi khi chuyen xlsx sang pdf: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể chuyển đổi file XLSX sang PDF", e);
        }
    }

    public byte[] convertXlsxToPdf(byte[] fileBytes) throws Exception {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Workbook workbook = new Workbook(inputStream);
            workbook.save(outputStream, SaveFormat.PDF);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Lỗi khi chuyển XLSX sang PDF", e);
            throw new RuntimeException("Không thể chuyển đổi file XLSX sang PDF", e);
        }
    }

    /**
     * Tính toán vị trí chèn chữ ký dựa trên vị trí keyword và kiểu placement
     *
     * @param keywordPos      Vị trí keyword từ PDFBox (top-left origin)
     * @param placement       Kiểu placement (RIGHT, LEFT, TOP, BOTTOM, OVERLAY)
     * @param signatureWidth  Chiều rộng ảnh chữ ký (points)
     * @param signatureHeight Chiều cao ảnh chữ ký (points)
     * @param margin          Khoảng cách giữa keyword và ảnh chữ ký (points)
     * @return SignaturePosition với tọa độ DSS (bottom-left origin)
     */
    public SignaturePosition calculateSignaturePosition(
            RectPosition keywordPos,
            SignaturePlacement placement,
            float signatureWidth,
            float signatureHeight,
            float margin) throws Exception {

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
                dssY = keywordPos.getY() - keywordPos.getHeight();
            }
            case BOTTOM -> {
                dssX = keywordPos.getX() + (keywordPos.getWidth() / 4);
                dssY = keywordPos.getY() + keywordPos.getHeight();
            }
            default -> throw new SigningException("Không hỗ trợ: " + placement);
        }

        SignaturePosition sigPos = new SignaturePosition();
        sigPos.setPage(keywordPos.getPage());
        sigPos.setX(dssX);
        sigPos.setY(dssY);
        sigPos.setWidth(signatureWidth);
        sigPos.setHeight(signatureHeight);

        return sigPos;
    }

    /**
     * Ký một PDF với ảnh chèn tại các keyword (logic dùng chung cho
     * document-with-image và documents-with-image).
     */
    public byte[] signPdfWithImage(byte[] documentBytes, String username, String password,
            String reason, String location, String signatureLevel,
            List<ImageInsertRequest> imageRequests) throws Exception {
        String level = signatureLevel != null ? signatureLevel : "B";
        byte[] currentPdf = documentBytes;
        int totalSignatures = 0;

        for (ImageInsertRequest imgReq : imageRequests) {
            String keyword = imgReq.getKeyWord();
            if (keyword == null || keyword.isEmpty())
                continue;

            List<RectPosition> positions = getKeywordPositionsInPdf(currentPdf, keyword);
            if (positions == null || positions.isEmpty()) {
                log.warn("Khong tim thay keyword: {}", keyword);
                continue;
            }

            byte[] imageBytes = Base64.getDecoder().decode(imgReq.getImagesBase());
            float imgWidth = (imgReq.getWidth() > 0) ? imgReq.getWidth() : 80f;
            float imgHeight = (imgReq.getHeight() > 0) ? imgReq.getHeight() : 50f;
            float margin = 5f;
            SignaturePlacement placement = SignaturePlacement.OVERLAY;

            for (RectPosition pos : positions) {
                SignRequestDTO signRequest = new SignRequestDTO();
                signRequest.setUsername(username);
                signRequest.setPassword(password);
                signRequest.setReason(reason);
                signRequest.setLocation(location);
                signRequest.setSignatureFormat(level);
                signRequest.setSignatureImage(imageBytes);

                SignaturePosition sigPos = calculateSignaturePosition(pos, placement, imgWidth, imgHeight, margin);
                signRequest.setPositions(Collections.singletonList(sigPos));

                currentPdf = signingService.signPDF(currentPdf, signRequest);
                totalSignatures++;
            }
        }

        if (totalSignatures == 0) {
            SignRequestDTO defaultRequest = new SignRequestDTO();
            defaultRequest.setUsername(username);
            defaultRequest.setPassword(password);
            defaultRequest.setSignatureFormat(level);
            currentPdf = signingService.signPDF(currentPdf, defaultRequest);
        }

        return currentPdf;
    }

    /**
     * Ký nháy một PDF với imageMetadata (list keyword + ảnh). Placement RIGHT, size
     * 40x20 hoặc từ request.
     * Dùng chung cho document-initial-signature và documents-initial-signature.
     */
    public byte[] signPdfInitialSignature(byte[] documentBytes, String username, String password,
            String reason, String location, String signatureLevel,
            List<ImageInsertRequest> imageRequests) throws Exception {
        String level = signatureLevel != null ? signatureLevel : "B";
        byte[] currentPdf = documentBytes;
        int totalSignatures = 0;

        for (ImageInsertRequest imgReq : imageRequests) {
            String keyword = imgReq.getKeyWord();
            if (keyword == null || keyword.isEmpty())
                continue;

            List<RectPosition> positions = getKeywordPositionsInPdf(currentPdf, keyword);
            if (positions == null || positions.isEmpty()) {
                log.warn("Ký nháy: không tìm thấy keyword: {}", keyword);
                continue;
            }

            float imgWidth = (imgReq.getWidth() > 0) ? imgReq.getWidth() : 40f;
            float imgHeight = (imgReq.getHeight() > 0) ? imgReq.getHeight() : 20f;
            float margin = 8f;
            SignaturePlacement placement = SignaturePlacement.RIGHT;

            byte[] imageBytes = null;
            if (imgReq.getImagesBase() != null && !imgReq.getImagesBase().isEmpty()) {
                imageBytes = Base64.getDecoder().decode(imgReq.getImagesBase());
            }

            for (RectPosition pos : positions) {
                SignRequestDTO signRequest = new SignRequestDTO();
                signRequest.setUsername(username);
                signRequest.setPassword(password);
                signRequest.setReason(reason);
                signRequest.setLocation(location);
                signRequest.setSignatureFormat(level);
                if (imageBytes != null) {
                    signRequest.setSignatureImage(imageBytes);
                    SignaturePosition sigPos = calculateSignaturePosition(pos, placement, imgWidth, imgHeight, margin);
                    signRequest.setPositions(Collections.singletonList(sigPos));
                }
                currentPdf = signingService.signPDF(currentPdf, signRequest);
                totalSignatures++;
            }
        }

        if (totalSignatures == 0) {
            SignRequestDTO defaultRequest = new SignRequestDTO();
            defaultRequest.setUsername(username);
            defaultRequest.setPassword(password);
            defaultRequest.setSignatureFormat(level);
            currentPdf = signingService.signPDF(currentPdf, defaultRequest);
        }

        return currentPdf;
    }

    /**
     * Ký formal nháy PDF (cho 1 file), dùng chung cho
     * /document-formal-initial-signature và /documents-formal-initial-signature.
     */
    public byte[] signPdfFormalInitialSignature(byte[] documentBytes, String username, String password,
            String reason, String location, String signatureLevel, String base64Image) throws Exception {
        RectPosition dotPos = getFormalInitialSignaturePosition(documentBytes);
        byte[] currentPdf = documentBytes;

        if (dotPos == null) {
            log.warn("Ký formal nháy: không tìm thấy vị trí Nơi nhận và dấu .");
            String level = signatureLevel != null ? signatureLevel : "B";
            SignRequestDTO defaultRequest = new SignRequestDTO();
            defaultRequest.setUsername(username);
            defaultRequest.setPassword(password);
            defaultRequest.setReason(reason);
            defaultRequest.setLocation(location);
            defaultRequest.setSignatureFormat(level);
            currentPdf = signingService.signPDF(currentPdf, defaultRequest);
        } else {
            String level = signatureLevel != null ? signatureLevel : "B";
            float imgWidth = 40f;
            float imgHeight = 20f;
            float margin = 8f;
            SignaturePlacement placement = SignaturePlacement.RIGHT;

            byte[] imageBytes = null;
            if (base64Image != null && !base64Image.isEmpty()) {
                imageBytes = Base64.getDecoder().decode(base64Image);
            }

            SignRequestDTO signRequest = new SignRequestDTO();
            signRequest.setUsername(username);
            signRequest.setPassword(password);
            signRequest.setReason(reason);
            signRequest.setLocation(location);
            signRequest.setSignatureFormat(level);

            if (imageBytes != null) {
                signRequest.setSignatureImage(imageBytes);
                SignaturePosition sigPos = calculateSignaturePosition(dotPos, placement, imgWidth, imgHeight, margin);
                signRequest.setPositions(Collections.singletonList(sigPos));
            }
            currentPdf = signingService.signPDF(currentPdf, signRequest);
        }

        return currentPdf;
    }
}