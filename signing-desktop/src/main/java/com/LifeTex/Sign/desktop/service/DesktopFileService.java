package com.lifetex.sign.desktop.service;

import com.lifetex.sign.model.domain.RectPosition;
import com.lifetex.sign.util.PDFKeywordScanner;
import com.aspose.words.Document;
import com.aspose.words.PdfCompliance;
import com.aspose.words.PdfSaveOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aspose.cells.SaveFormat;
import com.aspose.cells.Workbook;

@Slf4j
@Service
public class DesktopFileService {

    public List<RectPosition> getKeywordPositionsInPdf(byte[] pdfBytes, String keyword) throws IOException {
        try (PDDocument document = PDDocument.load(pdfBytes)) {

            PDFKeywordScanner scanner = new PDFKeywordScanner(Collections.singletonList(keyword));
            scanner.setStartPage(1);
            scanner.setEndPage(document.getNumberOfPages());
            scanner.getText(document);

            List<RectPosition> positions = scanner.getKeywordPositions().get(keyword);

            if (positions != null) {
                return positions;
            }

            log.info("Khong tim thay keyword '{}' trong PDF", keyword);
            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Loi khi lay vi tri keyword trong pdf: {}", e.getMessage());
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
}
