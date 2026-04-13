package com.lifetex.sign.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ResponseFactory {

    public static <T> ResponseEntity<HttpResponse<T>> success(T data) {
        return ResponseEntity.ok(new HttpResponse<>(200, true, "OK", data));
    }

    public static <T> ResponseEntity<HttpResponse<T>> success(String message, T data) {
        HttpResponse<T> body = HttpResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();

        return ResponseEntity.ok(body);
    }

    public static <T> ResponseEntity<HttpResponse<T>> error(String message, HttpStatus status, Integer code) {
        HttpResponse<T> body = HttpResponse.<T>builder()
                .errorCode(code)
                .success(false)
                .message(message)
                .data(null)
                .build();

        return ResponseEntity.status(status).body(body);
    }
}
