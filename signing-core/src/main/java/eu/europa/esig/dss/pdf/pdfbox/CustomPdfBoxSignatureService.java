package eu.europa.esig.dss.pdf.pdfbox;

import eu.europa.esig.dss.pdf.PDFServiceMode;
import eu.europa.esig.dss.pdf.pdfbox.visible.defaultdrawer.PdfBoxDefaultSignatureDrawerFactory;
import eu.europa.esig.dss.pdf.PdfDocumentReader;
import eu.europa.esig.dss.pdf.AnnotationBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomPdfBoxSignatureService extends PdfBoxSignatureService {

    private static final Logger LOG = LoggerFactory.getLogger(CustomPdfBoxSignatureService.class);

    public CustomPdfBoxSignatureService(PDFServiceMode serviceMode) {
        super(serviceMode, new PdfBoxDefaultSignatureDrawerFactory());
    }

    @Override
    protected void assertSignatureFieldPositionValid(PdfDocumentReader reader, AnnotationBox box, int page) {
        LOG.warn("Vị trí chữ ký trùng lặp được custom xử lý, bỏ qua kiểm tra vị trí chữ ký.");
    }
}
