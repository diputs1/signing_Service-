package com.lifetex.sign.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiSignResponseDTO {
    /** Total number of files in request */
    private int total;
    /** Number of files signed successfully */
    private int succeeded;
    /** Number of files that failed to sign */
    private int failed;
    /** Number of files signed successfully (alias for succeeded) */
    private int successCount;
    /** Number of files that failed to sign (alias for failed) */
    private int failedCount;
    /** Overall status: ALL_SUCCESS, PARTIAL_SUCCESS, ALL_FAILED */
    private String status;
    /** Per-document results (order preserved) */
    private List<SignedDocumentItemDTO> documents;

    public static final String STATUS_ALL_SUCCESS = "ALL_SUCCESS";
    public static final String STATUS_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    public static final String STATUS_ALL_FAILED = "ALL_FAILED";
}
