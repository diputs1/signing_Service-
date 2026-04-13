package com.lifetex.sign.desktop.exception;

import com.lifetex.sign.exception.EJBCAException;
import com.lifetex.sign.exception.SigningException;
import com.lifetex.sign.response.HttpResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import java.util.Map;

@RestControllerAdvice
public class DesktopGlobalExceptionHandler {

    @ExceptionHandler(EJBCAException.class)
    public ResponseEntity<HttpResponse<?>> handleEJBCAException(EJBCAException ex) {
        HttpResponse<?> error = HttpResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .data(null)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(SigningException.class)
    public ResponseEntity<HttpResponse<?>> handleSigningException(SigningException ex) {
        HttpResponse<?> error = HttpResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .data(null)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<HttpResponse<?>> handleGenericException(Exception ex) {
        HttpResponse<?> error = HttpResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .data(null)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<?> handleResourceAccess(ResourceAccessException ex) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "success", false,
                        "message", "Không thể kết nối đến máy chủ chứng thư. Vui lòng thử lại sau."));
    }
}
