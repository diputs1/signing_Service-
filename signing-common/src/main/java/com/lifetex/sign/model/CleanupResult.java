package com.lifetex.sign.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleanupResult {
    private long totalBefore;
    private int deletedCount;
    private long totalAfter;
    private LocalDateTime cutoffDate;
    private boolean success;
    private String message;
}
