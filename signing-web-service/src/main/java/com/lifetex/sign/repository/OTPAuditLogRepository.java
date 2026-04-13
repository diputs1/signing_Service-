package com.lifetex.sign.repository;

import com.lifetex.sign.model.OTPAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OTPAuditLogRepository extends JpaRepository<OTPAuditLog, Long> {

        // Tìm tất cả log của một user
        List<OTPAuditLog> findByUserIdOrderByTimestampDesc(String userId);

        // Tìm log theo action
        List<OTPAuditLog> findByActionOrderByTimestampDesc(String action);

        // Tìm log trong khoảng thời gian
        @Query("SELECT l FROM OTPAuditLog l WHERE l.timestamp BETWEEN :start AND :end ORDER BY l.timestamp DESC")
        List<OTPAuditLog> findLogsBetween(
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        // Đếm số lần failed attempts trong khoảng thời gian
        @Query("SELECT COUNT(l) FROM OTPAuditLog l WHERE l.userId = :userId " +
                        "AND l.action = 'OTP_VERIFY_FAILED' " +
                        "AND l.timestamp > :since")
        long countFailedAttemptsSince(
                        @Param("userId") String userId,
                        @Param("since") LocalDateTime since);

        @Modifying
        @Transactional
        @Query("DELETE FROM OTPAuditLog l WHERE l.timestamp < :cutoffDate")
        int deleteOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);

        @Transactional
        int deleteByTimestampBefore(LocalDateTime cutoffDate);

        // Đếm số log cũ hơn cutoffDate
        long countByTimestampBefore(LocalDateTime cutoffDate);

        // Tìm log cũ nhất
        OTPAuditLog findFirstByOrderByTimestampAsc();

        // Tìm log mới nhất
        OTPAuditLog findFirstByOrderByTimestampDesc();
}
