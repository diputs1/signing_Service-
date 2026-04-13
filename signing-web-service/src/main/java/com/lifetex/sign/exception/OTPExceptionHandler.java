package com.lifetex.sign.exception;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class OTPExceptionHandler extends Exception {
        @ExceptionHandler(MailSendException.class)
        public ResponseEntity<?> handleMailSendException(MailSendException e) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body(Map.of(
                                                "error", "Không thể gửi email",
                                                "message", "Vui lòng thử lại sau"));
        }

        @ExceptionHandler(RedisConnectionFailureException.class)
        public ResponseEntity<?> handleRedisException(RedisConnectionFailureException e) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body(Map.of(
                                                "error", "Dịch vụ tạm thời không khả dụng",
                                                "message", "Vui lòng thử lại sau"));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<?> handleGenericException(Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of(
                                                "error", "Đã xảy ra lỗi",
                                                "message", e.getMessage()));
        }
}
