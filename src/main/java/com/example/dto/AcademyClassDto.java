package com.example.dto;

import com.example.entity.AcademyClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademyClassDto {
    private Long id;
    private String name;
    private Long academyId;
    private String academyName;
    private Long ownerTeacherId;
    private DayOfWeek clinicDayOfWeek;
    private LocalTime clinicTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AcademyClassDto from(AcademyClass academyClass) {
        return AcademyClassDto.builder()
                .id(academyClass.getId())
                .name(academyClass.getName())
                .academyId(academyClass.getAcademy() != null ? academyClass.getAcademy().getId() : null)
                .academyName(academyClass.getAcademy() != null ? academyClass.getAcademy().getName() : null)
                .ownerTeacherId(academyClass.getOwnerTeacherId())
                .clinicDayOfWeek(academyClass.getClinicDayOfWeek())
                .clinicTime(academyClass.getClinicTime())
                .createdAt(academyClass.getCreatedAt())
                .updatedAt(academyClass.getUpdatedAt())
                .build();
    }
}
