package com.lifetex.sign.exception;

import lombok.Getter;

@Getter
public class EJBCAException extends RuntimeException {
    private final int errorCode;

    public EJBCAException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public EJBCAException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
