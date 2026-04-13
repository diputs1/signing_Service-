package com.lifetex.sign.service;

import com.lifetex.sign.model.CleanupResult;
import com.lifetex.sign.model.LogStatistics;
import com.lifetex.sign.model.OTPAuditLog;
import com.lifetex.sign.repository.OTPAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OTPAuditLogService {

    @Autowired
    private OTPAuditLogRepository auditLogRepository;

    /**
     * Xóa thủ công logs cũ hơn N ngày
     */
    public CleanupResult manualCleanup(int daysToKeep) {
        log.info("Manual cleanup triggered - keeping logs from last {} days", daysToKeep);

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        long countBefore = auditLogRepository.count();
        long countToDelete = auditLogRepository.countByTimestampBefore(cutoffDate);

        int deletedCount = auditLogRepository.deleteOldLogs(cutoffDate);
        long countAfter = auditLogRepository.count();

        CleanupResult result = CleanupResult.builder()
                .totalBefore(countBefore)
                .deletedCount(deletedCount)
                .totalAfter(countAfter)
                .cutoffDate(cutoffDate)
                .success(true)
                .build();

        log.info("Manual cleanup completed - {}", result);
        return result;
    }

    /**
     * Lấy thống kê logs
     */
    public LogStatistics getStatistics() {
        long totalLogs = auditLogRepository.count();
        OTPAuditLog oldest = auditLogRepository.findFirstByOrderByTimestampAsc();
        OTPAuditLog newest = auditLogRepository.findFirstByOrderByTimestampDesc();

        // Thống kê 24h gần nhất
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        List<OTPAuditLog> last24h = auditLogRepository.findLogsBetween(oneDayAgo, LocalDateTime.now());

        Map<String, Long> actionCounts = last24h.stream()
                .collect(Collectors.groupingBy(
                        OTPAuditLog::getAction,
                        Collectors.counting()));

        return LogStatistics.builder()
                .totalLogs(totalLogs)
                .oldestLogDate(oldest != null ? oldest.getTimestamp() : null)
                .newestLogDate(newest != null ? newest.getTimestamp() : null)
                .logsLast24h(last24h.size())
                .actionCountsLast24h(actionCounts)
                .build();
    }

    /**
     * Xóa tất cả logs của một user (GDPR compliance)
     */
    @Transactional
    public int deleteUserLogs(String userId) {
        log.info("Deleting all logs for user: {}", userId);
        List<OTPAuditLog> userLogs = auditLogRepository.findByUserIdOrderByTimestampDesc(userId);
        auditLogRepository.deleteAll(userLogs);
        return userLogs.size();
    }
}
