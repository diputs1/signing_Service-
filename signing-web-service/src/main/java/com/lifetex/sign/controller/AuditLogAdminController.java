package com.lifetex.sign.controller;

import com.lifetex.sign.model.CleanupResult;
import com.lifetex.sign.model.LogStatistics;
import com.lifetex.sign.model.OTPAuditLog;
import com.lifetex.sign.repository.OTPAuditLogRepository;
import com.lifetex.sign.service.OTPAuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/audit-logs")
// @PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AuditLogAdminController {

    @Autowired
    private OTPAuditLogService auditLogService;

    @Autowired
    private OTPAuditLogRepository auditLogRepository;

    // Xem thống kê
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        LogStatistics stats = auditLogService.getStatistics();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", stats);
        return ResponseEntity.ok(response);
    }

    // Xóa thủ công logs cũ
    @DeleteMapping("/cleanup")
    public ResponseEntity<CleanupResult> manualCleanup(
            @RequestParam(defaultValue = "90") int daysToKeep) {

        if (daysToKeep < 1 || daysToKeep > 365) {
            return ResponseEntity.badRequest().build();
        }

        CleanupResult result = auditLogService.manualCleanup(daysToKeep);
        return ResponseEntity.ok(result);
    }

    // Xem logs của user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OTPAuditLog>> getUserLogs(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "100") int limit) {

        List<OTPAuditLog> logs = auditLogRepository.findByUserIdOrderByTimestampDesc(userId)
                .stream()
                .limit(limit)
                .collect(Collectors.toList());

        return ResponseEntity.ok(logs);
    }

    // Xóa logs của user (GDPR)
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<?> deleteUserLogs(@PathVariable String userId) {
        int deletedCount = auditLogService.deleteUserLogs(userId);
        return ResponseEntity.ok(Map.of(
                "message", "User logs deleted successfully",
                "deletedCount", deletedCount));
    }

    // Xem logs trong khoảng thời gian
    @GetMapping("/range")
    public ResponseEntity<List<OTPAuditLog>> getLogsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        List<OTPAuditLog> logs = auditLogRepository.findLogsBetween(start, end);
        return ResponseEntity.ok(logs);
    }
}
