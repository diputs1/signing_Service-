package com.lifetex.sign.util;

import com.lifetex.sign.model.domain.RectPosition;
import lombok.Getter;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PDFKeywordScanner extends PDFTextStripper {

    @Getter
    private Map<String, List<RectPosition>> keywordPositions = new HashMap<>();
    private final List<String> keywords;

    public PDFKeywordScanner(List<String> keywords) throws IOException {
        super.setSortByPosition(true);
        this.keywords = keywords;
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        super.startPage(page);
        int currentPage = getCurrentPageNo();
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {

        for (String key : keywords) {
            int keyLen = key.length();

            for (int i = 0; i <= textPositions.size() - keyLen; i++) {

                boolean match = true;

                for (int k = 0; k < keyLen; k++) {
                    if (textPositions.get(i + k).getUnicode() == null ||
                            !textPositions.get(i + k).getUnicode().equals(String.valueOf(key.charAt(k)))) {
                        match = false;
                        break;
                    }
                }

                if (match) {
                    TextPosition firstPos = textPositions.get(i);
                    TextPosition lastPos = textPositions.get(i + keyLen - 1);

                    // Tính width = (x của ký tự cuối + width của ký tự cuối) - x của ký tự đầu
                    float totalWidth = (lastPos.getXDirAdj() + lastPos.getWidthDirAdj()) - firstPos.getXDirAdj();

                    keywordPositions
                            .computeIfAbsent(key, t -> new ArrayList<>())
                            .add(new RectPosition(
                                    firstPos.getXDirAdj(),
                                    firstPos.getYDirAdj(),
                                    totalWidth,
                                    firstPos.getHeightDir(),
                                    getCurrentPageNo()));
                }
            }
        }
    }

}