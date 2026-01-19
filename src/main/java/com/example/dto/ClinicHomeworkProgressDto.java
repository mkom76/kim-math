package com.example.dto;

import com.example.entity.ClinicHomeworkProgress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicHomeworkProgressDto {
    private Long id;
    private Long clinicId;
    private Long studentId;
    private String studentName;
    private Long homeworkId;
    private String homeworkTitle;
    private Integer homeworkQuestionCount;

    // 클리닉 전
    private Integer incorrectCountBefore;
    private Integer unsolvedCountBefore;
    private Integer completionBefore; // 계산값

    // 클리닉 후
    private Integer incorrectCountAfter;
    private Integer unsolvedCountAfter;
    private Integer completionAfter; // 계산값

    // 변화량
    private Integer completionChange; // 계산값 (% 포인트)

    public static ClinicHomeworkProgressDto from(ClinicHomeworkProgress progress) {
        return ClinicHomeworkProgressDto.builder()
                .id(progress.getId())
                .clinicId(progress.getClinic().getId())
                .studentId(progress.getStudent().getId())
                .studentName(progress.getStudent().getName())
                .homeworkId(progress.getHomework().getId())
                .homeworkTitle(progress.getHomework().getTitle())
                .homeworkQuestionCount(progress.getHomework().getQuestionCount())
                .incorrectCountBefore(progress.getIncorrectCountBefore())
                .unsolvedCountBefore(progress.getUnsolvedCountBefore())
                .completionBefore(progress.getCompletionBefore())
                .incorrectCountAfter(progress.getIncorrectCountAfter())
                .unsolvedCountAfter(progress.getUnsolvedCountAfter())
                .completionAfter(progress.getCompletionAfter())
                .completionChange(progress.getCompletionChange())
                .build();
    }
}
