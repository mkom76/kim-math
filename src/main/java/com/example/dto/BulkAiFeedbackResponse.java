package com.example.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BulkAiFeedbackResponse {
    private String status; // PROCESSING, COMPLETED
    private int totalCount;
    private int processedCount;
    private int successCount;
    private int failCount;
    private int skippedCount;
    private List<String> skippedStudents;
}
