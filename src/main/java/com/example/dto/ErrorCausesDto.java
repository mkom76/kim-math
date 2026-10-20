package com.example.dto;

import com.example.entity.StudentSubmission;

import java.util.stream.Stream;

public record ErrorCausesDto(
        Integer calculationMistake,
        Integer conceptGap,
        Integer conditionMisread,
        Integer ideaGap,
        Integer timeManagement,
        String status) {

    public static ErrorCausesDto from(StudentSubmission submission) {
        long entered = Stream.of(submission.getCalculationMistake(), submission.getConceptGap(),
                submission.getConditionMisread(), submission.getIdeaGap(), submission.getTimeManagement())
                .filter(value -> value != null).count();
        String status = entered == 0 ? "NOT_ENTERED"
                : submission.isErrorCausesNeedReview() ? "NEEDS_REVIEW"
                : entered == 5 ? "COMPLETE" : "PARTIAL";
        return new ErrorCausesDto(submission.getCalculationMistake(), submission.getConceptGap(),
                submission.getConditionMisread(), submission.getIdeaGap(), submission.getTimeManagement(), status);
    }
}
