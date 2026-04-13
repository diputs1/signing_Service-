package com.lifetex.sign.service;

import com.lifetex.sign.exception.SigningException;
import com.lifetex.sign.model.EmailJob;
import com.lifetex.sign.model.SigningSession;
import com.lifetex.sign.response.OTPResponse;
import com.lifetex.sign.response.OTPVerificationResponse;
import com.lifetex.sign.model.OTPAuditLog;
import com.lifetex.sign.repository.OTPAuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import domain.email.EmailProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedOTPService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private OTPAuditLogRepository auditLogRepository;

    private static final int OTP_LENGTH = 6;
    public static final int OTP_EXPIRY_MINUTES = 10;
    private static final int MAX_OTP_REQUESTS = 3;
    private static final int RATE_LIMIT_WINDOW_MINUTES = 15;
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int LOCKOUT_DURATION_MINUTES = 30;

    private static final int TOKEN_EXPIRY_MINUTES = 3;
    private final ObjectMapper objectMapper;

    /**
     * Tạo và gửi OTP với rate limiting
     */
    public OTPResponse generateOTP(String userName, String email) {
        logAction(userName, "OTP_REQUEST", "User requested OTP");

        // 1. Kiểm tra xem user có bị khóa không
        if (isUserLocked(userName)) {
            long remainingMinutes = getRemainingLockoutTime(userName);
            logAction(userName, "OTP_REQUEST_BLOCKED",
                    "User is locked out. Remaining: " + remainingMinutes + " minutes");

            return OTPResponse.builder()
                    .success(false)
                    .message("Tài khoản tạm thời bị khóa")
                    .remainingLockoutMinutes((int) remainingMinutes)
                    .build();
        }

        // 2. Kiểm tra rate limit
        if (!checkRateLimit(userName)) {
            int remainingRequests = getRemainingRequests(userName);
            logAction(userName, "OTP_RATE_LIMIT_EXCEEDED",
                    "Rate limit exceeded. Remaining requests: " + remainingRequests);

            return OTPResponse.builder()
                    .success(false)
                    .message("Quá số lần yêu cầu. Vui lòng thử lại sau")
                    .remainingRequests(remainingRequests)
                    .remainingLockoutMinutes(LOCKOUT_DURATION_MINUTES)
                    .build();
        }

        // 3. Tạo OTP
        String otp = generateRandomOTP();

        // 4. Lưu OTP vào Redis
        String otpKey = "otp:" + userName;
        redisTemplate.opsForValue().setIfAbsent(
                otpKey,
                otp,
                Duration.ofMinutes(OTP_EXPIRY_MINUTES));

        // 5. Reset failed attempts counter khi tạo OTP mới
        resetFailedAttempts(userName);

        // 6. Tăng counter cho rate limit
        incrementRateLimitCounter(userName);

        // 7. Gửi OTP qua email
        try {
            // sendOTPEmail(email, otp);
            EmailProducer producer = new EmailProducer(redisTemplate);
            producer.pushJob(EmailJob.builder()
                    .email(email)
                    .otp(otp)
                    .retry(0)
                    .timestamp(System.currentTimeMillis())
                    .build());
            logAction(userName, "OTP_SENT", "OTP sent successfully to " + maskEmail(email));

            int remainingRequests = getRemainingRequests(userName);

            return OTPResponse.builder()
                    .success(true)
                    .message("OTP đã được gửi đến email " + maskEmail(email))
                    .remainingRequests(remainingRequests)
                    .expiryMinutes(OTP_EXPIRY_MINUTES)
                    .remainingLockoutMinutes(LOCKOUT_DURATION_MINUTES)
                    .build();

        } catch (Exception e) {
            logAction(userName, "OTP_SEND_FAILED", "Failed to send OTP: " + e.getMessage());
            log.error("Lỗi khi gủi email đến: {}", userName, e);

            return OTPResponse.builder()
                    .remainingLockoutMinutes(LOCKOUT_DURATION_MINUTES)
                    .build();
        }
    }

    /**
     * Xác thực OTP với tracking failed attempts
     */
    public OTPVerificationResponse verifyOTP(String userName, String otp) {
        try {
            logAction(userName, "OTP_VERIFY_ATTEMPT", "User attempting to verify OTP");
            System.out.println("OTP:" + otp);
            // 1. Kiểm tra user có bị khóa không
            if (isUserLocked(userName)) {
                long remainingMinutes = getRemainingLockoutTime(userName);
                logAction(userName, "OTP_VERIFY_BLOCKED", "User is locked out");

                return OTPVerificationResponse.builder()
                        .success(false)
                        .message("Tài khoản tạm thời bị khóa")
                        .remainingLockoutMinutes((int) remainingMinutes)
                        .build();
            }

            // 2. Lấy OTP từ Redis
            String otpKey = "otp:" + userName;
            String storedOTP = redisTemplate.opsForValue().get(otpKey);

            if (storedOTP == null) {
                logAction(userName, "OTP_EXPIRED", "OTP not found or expired");

                int failedAttempts = incrementFailedAttempts(userName);
                int remainingAttempts = MAX_FAILED_ATTEMPTS - failedAttempts;

                logAction(userName, "OTP_VERIFY_FAILED",
                        "Wrong OTP. Failed attempts: " + failedAttempts);

                if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                    // Khóa user
                    lockUser(userName);
                    logAction(userName, "USER_LOCKED",
                            "User locked due to too many failed attempts");

                    return OTPVerificationResponse.builder()
                            .success(false)
                            .message("Bạn đã nhập sai OTP quá 3 lần. Tài khoản bị khóa trong 30 phút")
                            .remainingAttempts(0)
                            .remainingLockoutMinutes(LOCKOUT_DURATION_MINUTES)
                            .build();
                }
                return OTPVerificationResponse.builder()
                        .success(false)
                        .message("OTP không tồn tại hoặc đã hết hạn, bạn còn " + remainingAttempts + " lần nhập")
                        .remainingLockoutMinutes(LOCKOUT_DURATION_MINUTES).build();
            }

            // 3. Kiểm tra OTP
            if (storedOTP.equals(otp)) {
                // OTP đúng
                redisTemplate.delete(otpKey);
                resetFailedAttempts(userName);

                // Tạo signing token
                String signingToken = createSigningToken(userName);

                logAction(userName, "OTP_VERIFY_SUCCESS", "OTP verified successfully");

                return OTPVerificationResponse.builder()
                        .success(true)
                        .message("Xác thực thành công")
                        .signingToken(signingToken)
                        .tokenExpiryMinutes(TOKEN_EXPIRY_MINUTES)
                        .remainingLockoutMinutes(LOCKOUT_DURATION_MINUTES).build();
            } else {
                // OTP sai
                int failedAttempts = incrementFailedAttempts(userName);
                int remainingAttempts = MAX_FAILED_ATTEMPTS - failedAttempts;

                logAction(userName, "OTP_VERIFY_FAILED",
                        "Wrong OTP. Failed attempts: " + failedAttempts);

                if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                    // Khóa user
                    lockUser(userName);
                    logAction(userName, "USER_LOCKED",
                            "User locked due to too many failed attempts");

                    return OTPVerificationResponse.builder()
                            .success(false)
                            .message("Bạn đã nhập sai OTP quá 3 lần. Tài khoản bị khóa trong 30 phút")
                            .remainingAttempts(0)
                            .remainingLockoutMinutes(LOCKOUT_DURATION_MINUTES)
                            .build();
                }

                return OTPVerificationResponse.builder()
                        .success(false)
                        .message("OTP không đúng bạn còn " + remainingAttempts + " lần nhập")
                        .remainingAttempts(remainingAttempts)
                        .remainingLockoutMinutes(LOCKOUT_DURATION_MINUTES).build();
            }
        } catch (Exception e) {
            logAction(userName, "OTP_VERIFY_FAILED", "Failed to verify OTP: " + e.getMessage());
            log.error("Lỗi khi xác thực OTP: {}", userName, e);
            throw new SigningException("Lỗi khi xác thực OTP");
        }
    }

    // ============ RATE LIMITING ============

    /**
     * Kiểm tra rate limit (tối đa 3 lần trong 15 phút)
     */
    private boolean checkRateLimit(String userId) {
        String key = "rate_limit:" + userId;
        String countStr = redisTemplate.opsForValue().get(key);

        if (countStr == null) {
            return true; // Chưa có request nào
        }

        int count = Integer.parseInt(countStr);
        return count < MAX_OTP_REQUESTS;
    }

    /**
     * Tăng counter cho rate limit
     */
    private void incrementRateLimitCounter(String userId) {
        String key = "rate_limit:" + userId;
        String countStr = redisTemplate.opsForValue().get(key);

        if (countStr == null) {
            // Lần đầu tiên, set counter = 1 với TTL 15 phút
            redisTemplate.opsForValue().set(key, "1",
                    Duration.ofMinutes(RATE_LIMIT_WINDOW_MINUTES));
        } else {
            // Increment counter
            redisTemplate.opsForValue().increment(key);
        }
    }

    /**
     * Lấy số request còn lại
     */
    private int getRemainingRequests(String userId) {
        String key = "rate_limit:" + userId;
        String countStr = redisTemplate.opsForValue().get(key);

        if (countStr == null) {
            return MAX_OTP_REQUESTS;
        }

        int used = Integer.parseInt(countStr);
        return Math.max(0, MAX_OTP_REQUESTS - used);
    }

    // ============ FAILED ATTEMPTS TRACKING ============

    /**
     * Tăng số lần nhập sai
     */
    private int incrementFailedAttempts(String userId) {
        String key = "failed_attempts:" + userId;
        String countStr = redisTemplate.opsForValue().get(key);

        if (countStr == null) {
            redisTemplate.opsForValue().set(key, "1",
                    Duration.ofMinutes(OTP_EXPIRY_MINUTES));
            return 1;
        } else {
            Long count = redisTemplate.opsForValue().increment(key);
            assert count != null;
            return count.intValue();
        }
    }

    /**
     * Reset failed attempts
     */
    private void resetFailedAttempts(String userId) {
        String key = "failed_attempts:" + userId;
        redisTemplate.delete(key);
    }

    /**
     * Khóa user
     */
    private void lockUser(String userId) {
        String key = "user_locked:" + userId;
        redisTemplate.opsForValue().set(key, "true",
                Duration.ofMinutes(LOCKOUT_DURATION_MINUTES));
    }

    /**
     * Kiểm tra user có bị khóa không
     */
    private boolean isUserLocked(String userId) {
        String key = "user_locked:" + userId;
        String locked = redisTemplate.opsForValue().get(key);
        return "true".equals(locked);
    }

    /**
     * Lấy thời gian khóa còn lại (phút)
     */
    private long getRemainingLockoutTime(String userId) {
        String key = "user_locked:" + userId;
        return redisTemplate.getExpire(key, TimeUnit.MINUTES);
    }

    // ============ LOGGING ============

    /**
     * Ghi log mọi hành động
     */
    private void logAction(String userId, String action, String details) {
        OTPAuditLog auditLog = OTPAuditLog.builder()
                .userId(userId)
                .action(action)
                .details(details)
                .ipAddress("127.0.0.1")
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);

        // Log ra console
        log.info("OTP_AUDIT | userId={} | action={} | details={}",
                userId, action, details);
    }

    /**
     * Mask email để bảo mật trong log
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts[1];

        if (username.length() <= 2) {
            return "**@" + domain;
        }

        return username.substring(0, 2) + "***@" + domain;
    }

    // ============ HELPER METHODS ============

    private String generateRandomOTP() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        int value = new Random().nextInt(bound);
        return String.format("%0" + OTP_LENGTH + "d", value);
    }

    private String createSigningToken(String userName) {
        try {
            String token = UUID.randomUUID().toString();
            String key = "signing:session:" + token;

            Instant now = Instant.now();

            SigningSession session = new SigningSession(
                    userName,
                    now,
                    now.plus(Duration.ofMinutes(TOKEN_EXPIRY_MINUTES)));

            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(session),
                    TOKEN_EXPIRY_MINUTES,
                    TimeUnit.MINUTES);

            return token;

        } catch (Exception e) {
            throw new SigningException("Failed to create signing session", e);
        }
    }

    // Các phương thức public khác để controller sử dụng

    public SigningSession verifySign(String token) {
        String key = "signing:session:" + token;

        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }

        try {
            SigningSession session = objectMapper.readValue(json, SigningSession.class);
            redisTemplate.delete(key);

            return session;

        } catch (Exception e) {
            log.error("Loi xac thuc phien ky so voi token: {}", token, e);
            return null;
        }
    }

    // Xóa otp của người dùng
    public boolean deleteOldOTP(String userName) {
        String otpKey = "otp:" + userName;
        return redisTemplate.delete(otpKey);
    }

    // xóa rate_limit của người dùng
    public boolean deleteRateLimit(String userName) {
        String rateLimitKey = "rate_limit:" + userName;
        return redisTemplate.delete(rateLimitKey);
    }
}
