package com.lifetex.sign.util;

import com.lifetex.sign.model.domain.RectPosition;
import lombok.Getter;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.List;

public class PDFFormalSignatureScanner extends PDFTextStripper {

    @Getter
    private RectPosition lastDotPosition = null;
    private boolean foundNoiNhan = false;
    private final String keyword = "Nơi nhận";

    public PDFFormalSignatureScanner() throws IOException {
        super.setSortByPosition(true);
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {

        int keyLen = keyword.length();

        for (int i = 0; i < textPositions.size(); i++) {
            TextPosition pos = textPositions.get(i);
            String unicode = pos.getUnicode();

            if (unicode != null) {
                if (!foundNoiNhan) {
                    if (i <= textPositions.size() - keyLen) {
                        boolean match = true;
                        for (int k = 0; k < keyLen; k++) {
                            String expected = String.valueOf(keyword.charAt(k));
                            String actual = textPositions.get(i + k).getUnicode();
                            if (actual == null || !actual.equalsIgnoreCase(expected)) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            foundNoiNhan = true;
                            i += keyLen - 1;
                            continue;
                        }
                    }
                }

                if (foundNoiNhan) {
                    if (unicode.contains(".")) {
                        lastDotPosition = new RectPosition(
                                pos.getXDirAdj(),
                                pos.getYDirAdj(),
                                pos.getWidthDirAdj(),
                                pos.getHeightDir(),
                                getCurrentPageNo());
                    }
                }
            }
        }
    }
}
