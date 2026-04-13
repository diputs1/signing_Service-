package com.lifetex.sign.desktop.response;

import com.lifetex.sign.response.HttpResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class DesktopResponseFactory {

    public static <T> ResponseEntity<HttpResponse<T>> success(T data) {
        return ResponseEntity.ok(new HttpResponse<>(200, true, "OK", data));
    }

    @SuppressWarnings("unchecked")
    public static <T> ResponseEntity<HttpResponse<T>> error(String message, HttpStatus status) {
        HttpResponse<T> body = HttpResponse.<T>builder()
                .success(false)
                .message(message)
                .data(null)
                .build();

        return ResponseEntity.status(status).body(body);
    }
}
