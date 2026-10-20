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
public class StudentClinicInfoDto {
    private ClinicDto upcomingClinic; // null if no upcoming clinic
    private ClinicRegistrationDto myRegistration; // null if not registered
    private Boolean shouldAttend; // true if completion < 90%
    private List<IncompleteHomeworkDto> incompleteHomeworks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IncompleteHomeworkDto {
        private Long studentHomeworkId;
        private Long homeworkId;
        private String homeworkTitle;
        private Integer completion;
        private String lessonDate;
    }
}
