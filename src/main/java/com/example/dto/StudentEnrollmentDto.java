package com.example.dto;

import com.example.entity.StudentClassEnrollmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StudentEnrollmentDto {
    private Long classId;
    private String className;
    private StudentClassEnrollmentStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String endReason;
    private boolean canManage;
}
