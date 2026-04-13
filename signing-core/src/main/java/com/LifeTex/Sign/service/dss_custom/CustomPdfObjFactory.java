package com.lifetex.sign.service.dss_custom;

import eu.europa.esig.dss.pdf.*;
import eu.europa.esig.dss.pdf.modifications.PdfDifferencesFinder;
import eu.europa.esig.dss.pdf.modifications.PdfObjectModificationsFinder;
import eu.europa.esig.dss.pdf.pdfbox.CustomPdfBoxSignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomPdfObjFactory implements IPdfObjFactory {

    // private static final Logger LOG =
    // LoggerFactory.getLogger(CustomPdfObjFactory.class);

    @Override
    public PDFSignatureService newPAdESSignatureService() {
        return new CustomPdfBoxSignatureService(PDFServiceMode.SIGNATURE);
    }

    @Override
    public PDFSignatureService newContentTimestampService() {
        return null;
    }

    @Override
    public PDFSignatureService newSignatureTimestampService() {
        return null;
    }

    @Override
    public PDFSignatureService newArchiveTimestampService() {
        return null;
    }

    @Override
    public void setPdfDifferencesFinder(PdfDifferencesFinder finder) {
    }

    @Override
    public void setPdfPermissionsChecker(PdfPermissionsChecker checker) {
    }

    @Override
    public void setPdfSignatureFieldPositionChecker(PdfSignatureFieldPositionChecker checker) {
    }

    @Override
    public void setPdfObjectModificationsFinder(
            PdfObjectModificationsFinder finder) {
    }

    @Override
    public void setResourcesHandlerBuilder(eu.europa.esig.dss.signature.resources.DSSResourcesHandlerBuilder builder) {
    }
}
