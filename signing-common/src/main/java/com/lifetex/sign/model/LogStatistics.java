package com.lifetex.sign.model;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogStatistics {
    private long totalLogs;
    private LocalDateTime oldestLogDate;
    private LocalDateTime newestLogDate;
    private long logsLast24h;
    private Map<String, Long> actionCountsLast24h;
}
