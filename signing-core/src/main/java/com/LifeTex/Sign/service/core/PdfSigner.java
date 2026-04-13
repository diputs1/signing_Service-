package com.lifetex.sign.service.core;

import com.lifetex.sign.exception.SigningException;
import com.lifetex.sign.model.domain.SignaturePosition;
import com.lifetex.sign.model.dto.SignRequestDTO;
import com.lifetex.sign.service.dss_custom.CustomPdfObjFactory;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.SignatureFieldParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.token.AbstractSignatureTokenConnection;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.validation.CertificateVerifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Service
public class PdfSigner {

    private final CertificateVerifier certificateVerifier;

    public PdfSigner(CertificateVerifier certificateVerifier) {
        this.certificateVerifier = certificateVerifier;
    }

    /**
     * Phương thức lõi để ký PDF sử dụng DSS
     */
    public byte[] signPdf(
            DSSDocument toSignDocument,
            AbstractSignatureTokenConnection signingToken,
            DSSPrivateKeyEntry privateKeyEntry,
            SignRequestDTO signRequest,
            String defaultSignatureLevel,
            String defaultDigestAlgorithm) throws Exception {

        try {
            // 7. Cấu hình tham số ký PAdES
            PAdESSignatureParameters parameters = new PAdESSignatureParameters();
            parameters.setSignatureLevel(resolveSignatureLevel(signRequest.getSignatureLevel(), defaultSignatureLevel));
            parameters.setDigestAlgorithm(DigestAlgorithm.valueOf(defaultDigestAlgorithm));
            parameters.setSigningCertificate(privateKeyEntry.getCertificate());
            parameters.setCertificateChain(privateKeyEntry.getCertificateChain());

            // tham số bổ sung
            if (signRequest.getReason() != null) {
                parameters.setReason(signRequest.getReason());
            }
            if (signRequest.getLocation() != null) {
                parameters.setLocation(signRequest.getLocation());
            }
            if (signRequest.getContactInfo() != null) {
                parameters.setContactInfo(signRequest.getContactInfo());
            }

            // Cấu hình visible signature nếu có ảnh và vị trí
            if (signRequest.getSignatureImage() != null
                    && signRequest.getPositions() != null
                    && !signRequest.getPositions().isEmpty()) {

                byte[] cleanImage = normalizeSignatureImage(signRequest.getSignatureImage());

                SignatureImageParameters imageParameters = new SignatureImageParameters();

                // Set image từ byte array
                imageParameters.setImage(new InMemoryDocument(cleanImage, "signature.png"));

                // Lấy vị trí đầu tiên
                SignaturePosition originalPos = signRequest.getPositions().get(0);

                // Kiểm tra và điều chỉnh vị trí nếu cần
                byte[] pdfBytes = toSignDocument.openStream().readAllBytes();
                SignaturePosition position = checkAndShiftPosition(pdfBytes, originalPos);

                SignatureFieldParameters fieldParameters = new SignatureFieldParameters();
                fieldParameters.setPage(position.getPage());
                fieldParameters.setOriginX(position.getX());
                fieldParameters.setOriginY(position.getY());
                fieldParameters.setWidth(position.getWidth());
                fieldParameters.setHeight(position.getHeight());

                imageParameters.setFieldParameters(fieldParameters);

                parameters.setImageParameters(imageParameters);
            } else {
                log.info("Không có ảnh hoặc vị trí chữ ký, ký ẩn danh");
            }

            // 8. Tạo PAdESService
            PAdESService service = new PAdESService(certificateVerifier);
            service.setPdfObjFactory(new CustomPdfObjFactory());

            // 9. Lấy dữ liệu cần ký
            ToBeSigned dataToSign = service.getDataToSign(toSignDocument, parameters);

            // 10. Ký dữ liệu
            SignatureValue signatureValue = signingToken.sign(dataToSign, parameters.getDigestAlgorithm(),
                    privateKeyEntry);

            // 11. Tạo tài liệu đã ký
            DSSDocument signedDocument = service.signDocument(toSignDocument, parameters, signatureValue);

            // 12. Trả về tài liệu đã ký dưới dạng byte array
            return signedDocument.openStream().readAllBytes();

        } catch (Exception e) {
            log.error("Error inside PdfSigner", e);
            throw new SigningException("Error inside PdfSigner: " + e.getMessage(), e);
        }
    }

    private SignatureLevel resolveSignatureLevel(String requestedLevel, String defaultLevel) {
        String level = (requestedLevel != null && !requestedLevel.isEmpty()) ? requestedLevel : defaultLevel;
        if (level == null)
            level = "PAdES_BASELINE_B";

        // Handle short names "B", "T" etc or full names
        if (level.length() <= 3) {
            return switch (level.toUpperCase()) {
                case "B" -> SignatureLevel.PAdES_BASELINE_B;
                case "T" -> SignatureLevel.PAdES_BASELINE_T;
                case "LT" -> SignatureLevel.PAdES_BASELINE_LT;
                case "LTA" -> SignatureLevel.PAdES_BASELINE_LTA;
                default -> SignatureLevel.PAdES_BASELINE_B;
            };
        }

        try {
            return SignatureLevel.valueOf(level);
        } catch (Exception e) {
            return SignatureLevel.PAdES_BASELINE_B;
        }
    }

    private SignaturePosition checkAndShiftPosition(byte[] pdfBytes, SignaturePosition pos) {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            int pageIndex = pos.getPage() - 1;
            if (pageIndex < 0 || pageIndex >= doc.getNumberOfPages()) {
                return pos;
            }

            PDPage page = doc.getPage(pageIndex);

            float x = pos.getX();
            float y = pos.getY();
            float w = pos.getWidth();
            float h = pos.getHeight();

            float pageHeight = page.getMediaBox().getHeight();

            int maxRetries = 5;
            float toleranceX = w / 4;
            float toleranceY = h * 0.9f;

            for (int attempt = 0; attempt < maxRetries; attempt++) {
                float shiftAmount = attempt * (w / 8);
                float testX = x - shiftAmount;
                float pdfY = pageHeight - y - h;

                boolean slotOccupied = false;

                for (PDAnnotation annot : page.getAnnotations()) {
                    PDRectangle rect = annot.getRectangle();
                    if (rect == null)
                        continue;

                    float deltaX = Math.abs(rect.getLowerLeftX() - testX);
                    float deltaY = Math.abs(rect.getLowerLeftY() - pdfY);

                    if (deltaX < toleranceX && deltaY < toleranceY) {
                        slotOccupied = true;
                        break;
                    }
                }

                if (!slotOccupied) {
                    SignaturePosition newPos = new SignaturePosition();
                    newPos.setPage(pos.getPage());
                    newPos.setX(testX);
                    newPos.setY(y);
                    newPos.setWidth(w);
                    newPos.setHeight(h);
                    return newPos;
                }
            }
            return pos;

        } catch (Exception e) {
            log.warn("Lỗi khi kiểm tra vị trí chữ ký: {}", e.getMessage());
        }
        return pos;
    }

    private byte[] normalizeSignatureImage(byte[] rawImage) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(rawImage));
        if (src == null) {
            throw new IllegalArgumentException("Invalid signature image");
        }

        BufferedImage argbImage = new BufferedImage(
                src.getWidth(),
                src.getHeight(),
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = argbImage.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(argbImage, "png", baos);
        return baos.toByteArray();
    }
}
