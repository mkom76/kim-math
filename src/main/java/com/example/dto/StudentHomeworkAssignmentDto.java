package com.example.dto;

import com.example.entity.StudentHomework;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentHomeworkAssignmentDto {
    private Long studentHomeworkId;
    private Long studentId;
    private String studentName;
    private Long assignedHomeworkId;
    private String assignedHomeworkTitle;
    private Integer incorrectCount;
    private Integer unsolvedCount;
    private String incorrectQuestions;
    private String unsolvedQuestions;
    private Integer completion;
    private Boolean followUpFlag;

    public static StudentHomeworkAssignmentDto from(StudentHomework sh) {
        return StudentHomeworkAssignmentDto.builder()
                .studentHomeworkId(sh.getId())
                .studentId(sh.getStudent().getId())
                .studentName(sh.getStudent().getName())
                .assignedHomeworkId(sh.getHomework().getId())
                .assignedHomeworkTitle(sh.getHomework().getTitle())
                .incorrectCount(sh.getIncorrectCount())
                .unsolvedCount(sh.getUnsolvedCount())
                .incorrectQuestions(sh.getIncorrectQuestions())
                .unsolvedQuestions(sh.getUnsolvedQuestions())
                .completion(sh.getCompletion())
                .followUpFlag(sh.getFollowUpFlag())
                .build();
    }
}
