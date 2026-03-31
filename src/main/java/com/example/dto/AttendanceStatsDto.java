package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceStatsDto {
    private Long studentId;
    private String studentName;
    private int totalLessons;
    private int presentCount;
    private int absentCount;
    private int lateCount;
    private int earlyLeaveCount;
    private int videoCount;
    private int uncheckedCount;
    private double attendanceRate;
}
