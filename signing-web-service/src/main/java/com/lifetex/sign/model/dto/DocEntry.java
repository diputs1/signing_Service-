package com.lifetex.sign.model.dto;

public class DocEntry {
    private final int index;
    private final String filename;
    private final byte[] pdfBytes;
    private final String error;

    public DocEntry(int index, String filename, byte[] pdfBytes, String error) {
        this.index = index;
        this.filename = filename;
        this.pdfBytes = pdfBytes;
        this.error = error;
    }

    public static DocEntry ok(int index, String filename, byte[] pdfBytes) {
        return new DocEntry(index, filename, pdfBytes, null);
    }

    public static DocEntry failed(int index, String filename, String error) {
        return new DocEntry(index, filename != null ? filename : "file_" + (index + 1), null, error);
    }

    public int getIndex() {
        return index;
    }

    public String getFilename() {
        return filename;
    }

    public byte[] getPdfBytes() {
        return pdfBytes;
    }

    public String getError() {
        return error;
    }
}
