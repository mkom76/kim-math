package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentClinicResultDto {
    private Long clinicId;
    private String clinicDate;
    private String clinicTime;
    private Integer improvedHomeworkCount;
    private Integer averageCompletionBefore;
    private Integer averageCompletionAfter;
    private Integer averageCompletionChange;
    private List<RecentClinicHomeworkDto> homeworks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentClinicHomeworkDto {
        private Long homeworkId;
        private String homeworkTitle;
        private Integer completionBefore;
        private Integer completionAfter;
        private Integer completionChange;
    }
}