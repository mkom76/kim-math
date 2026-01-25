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
    private Integer totalIncorrectCountBefore;
    private Integer totalIncorrectCountAfter;
    private Integer totalIncorrectCountChange;
    private Integer totalUnsolvedCountBefore;
    private Integer totalUnsolvedCountAfter;
    private Integer totalUnsolvedCountChange;
    private List<RecentClinicHomeworkDto> homeworks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentClinicHomeworkDto {
        private Long homeworkId;
        private String homeworkTitle;
        private Integer incorrectCountBefore;
        private Integer incorrectCountAfter;
        private Integer incorrectCountChange;
        private Integer unsolvedCountBefore;
        private Integer unsolvedCountAfter;
        private Integer unsolvedCountChange;
    }
}