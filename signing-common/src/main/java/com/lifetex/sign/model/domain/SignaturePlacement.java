package com.lifetex.sign.model.domain;

/**
 * Enum định nghĩa các kiểu vị trí chèn chữ ký
 */
public enum SignaturePlacement {
    /**
     * Chèn ảnh đè lên vị trí keyword
     */
    OVERLAY,

    /**
     * Chèn ảnh bên phải keyword
     */
    RIGHT,

    /**
     * Chèn ảnh bên trái keyword
     */
    LEFT,

    /**
     * Chèn ảnh phía trên keyword
     */
    TOP,

    /**
     * Chèn ảnh phía dưới keyword
     */
    BOTTOM
}
