package com.example.dto;

import com.example.entity.StudentClassEnrollment;
import com.example.entity.StudentClassEnrollmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentClassMembershipDto {
    private Long classId;
    private String className;
    private Long academyId;
    private String academyName;
    private StudentClassEnrollmentStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private boolean selectable;
    private boolean readOnly;

    public static StudentClassMembershipDto from(StudentClassEnrollment enrollment, boolean selectable) {
        return StudentClassMembershipDto.builder()
                .classId(enrollment.getAcademyClass().getId())
                .className(enrollment.getAcademyClass().getName())
                .academyId(enrollment.getAcademyClass().getAcademy().getId())
                .academyName(enrollment.getAcademyClass().getAcademy().getName())
                .status(enrollment.getStatus())
                .startedAt(enrollment.getStartedAt())
                .endedAt(enrollment.getEndedAt())
                .selectable(selectable)
                .readOnly(enrollment.getStatus() != StudentClassEnrollmentStatus.ACTIVE)
                .build();
    }
}
