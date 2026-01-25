package com.example.dto;

import com.example.entity.Clinic;
import com.example.entity.ClinicRegistrationStatus;
import com.example.entity.ClinicStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicDto {
    private Long id;
    private Long classId;
    private String className;
    private Long academyId;
    private String academyName;
    private LocalDate clinicDate;
    private LocalTime clinicTime;
    private ClinicStatus status;
    private Integer registrationCount;

    public static ClinicDto from(Clinic clinic) {
        return ClinicDto.builder()
                .id(clinic.getId())
                .classId(clinic.getAcademyClass().getId())
                .className(clinic.getAcademyClass().getName())
                .academyId(clinic.getAcademyClass().getAcademy().getId())
                .academyName(clinic.getAcademyClass().getAcademy().getName())
                .clinicDate(clinic.getClinicDate())
                .clinicTime(clinic.getClinicTime())
                .status(clinic.getStatus())
                .registrationCount((int) clinic.getRegistrations().stream()
                        .filter(r -> r.getStatus() == ClinicRegistrationStatus.REGISTERED ||
                                     r.getStatus() == ClinicRegistrationStatus.ATTENDED)
                        .count())
                .build();
    }
}
