package com.lifetex.sign.scheduled;

import com.lifetex.sign.model.OTPAuditLog;
import com.lifetex.sign.repository.OTPAuditLogRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@EnableScheduling
@Slf4j
public class OTPMaintenanceScheduler {

    @Autowired
    private OTPAuditLogRepository auditLogRepository;

    // Số ngày giữ log (có thể config trong application.yml)
    @Value("${otp.audit-log.retention-days:90}")
    private int retentionDays;

    /**
     * Tự động xóa log cũ hơn N ngày
     * Chạy mỗi ngày lúc 2:00 AM
     */
    @Scheduled(cron = "${otp.audit-log.cleanup-cron:0 0 2 * * ?}")
    @Transactional
    public void cleanupOldLogs() {
        log.info("Bắt đầu xóa log trong {} ngày", retentionDays);

        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
            // Đếm số log sẽ xóa
            long countToDelete = auditLogRepository.countByTimestampBefore(cutoffDate);

            if (countToDelete == 0) {
                log.info("Không có log nào cần xóa");
                return;
            }

            log.info("Tìm {} log cũ để xóa (trong {})", countToDelete, cutoffDate);

            // Xóa logs
            int deletedCount = auditLogRepository.deleteOldLogs(cutoffDate);

            log.info("Xóa thành công {} OTP audit logs", deletedCount);

            // Log thống kê sau khi xóa
            long remainingCount = auditLogRepository.count();
            OTPAuditLog oldest = auditLogRepository.findFirstByOrderByTimestampAsc();
            OTPAuditLog newest = auditLogRepository.findFirstByOrderByTimestampDesc();

            log.info("Sau khi xóa, còn lại {} log | Oldest: {} | Newest: {}",
                    remainingCount,
                    oldest != null ? oldest.getTimestamp() : "N/A",
                    newest != null ? newest.getTimestamp() : "N/A");
        } catch (Exception e) {
            log.error("Lỗi khi chạy job xóa log cũ ", e);
            // Có thể gửi alert email ở đây
        }
    }

    /**
     * Thống kê rate limiting và failed attempts
     * Chạy mỗi giờ
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void generateHourlyStats() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime oneHourAgo = now.minusHours(1);

            List<OTPAuditLog> recentLogs = auditLogRepository.findLogsBetween(oneHourAgo, now);

            // Nhóm theo action
            Map<String, Long> actionCounts = recentLogs.stream()
                    .collect(Collectors.groupingBy(
                            OTPAuditLog::getAction,
                            Collectors.counting()));

            log.info("Mỗi giờ: Tổng {} log trong 1 giờ qua | Thống kê: {}",
                    recentLogs.size(), actionCounts);

            // Cảnh báo nếu có quá nhiều failed attempts
            Long failedCount = actionCounts.getOrDefault("OTP_VERIFY_FAILED", 0L);
            if (failedCount > 50) {
                log.warn("Số lượng thất bại lớn: {}", failedCount);
                // Có thể gửi alert ở đây
            }

        } catch (Exception e) {
            log.error("Lỗi khi thống kê báo cáo log", e);
        }
    }

    /**
     * Giám sát hoạt động đáng ngờ
     * Chạy mỗi 15 phút
     */
    @Scheduled(fixedRate = 900000)
    public void monitorSuspiciousActivity() {
        try {
            LocalDateTime fifteenMinutesAgo = LocalDateTime.now().minusMinutes(15);
            LocalDateTime now = LocalDateTime.now();

            List<OTPAuditLog> recentLogs = auditLogRepository.findLogsBetween(fifteenMinutesAgo, now);

            // Nhóm theo userId và đếm failed attempts
            Map<String, Long> failedAttemptsByUser = recentLogs.stream()
                    .filter(log -> "OTP_VERIFY_FAILED".equals(log.getAction()))
                    .collect(Collectors.groupingBy(
                            OTPAuditLog::getUserId,
                            Collectors.counting()));

            // Cảnh báo nếu user có hơn 5 lần failed trong 15 phút
            failedAttemptsByUser.forEach((userId, count) -> {
                if (count >= 5) {
                    log.warn("Thông báo bảo mật | người dùng {} có {} lần xác thực OTP thất bại trong 15 phút qua",
                            userId, count);

                    // Có thể:
                    // 1. Gửi email alert
                    // 2. Gửi Slack notification
                    // 3. Tự động khóa user thêm thời gian
                }
            });

            // Phát hiện nhiều request từ cùng IP
            Map<String, Long> requestsByIp = recentLogs.stream()
                    .filter(log -> log.getIpAddress() != null)
                    .collect(Collectors.groupingBy(
                            OTPAuditLog::getIpAddress,
                            Collectors.counting()));

            requestsByIp.forEach((ip, count) -> {
                if (count >= 20) {
                    log.warn("Thông báo báo mật | IP {} có {} lần request trong 15 phút qua",
                            ip, count);
                }
            });

        } catch (Exception e) {
            log.error("Lỗi khi giám sát hoạt động bất ngờ", e);
        }
    }
}
