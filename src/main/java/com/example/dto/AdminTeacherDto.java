package com.example.dto;

import com.example.entity.TeacherAcademyRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminTeacherDto {
    private Long teacherId;
    private String name;
    private String username;
    private TeacherAcademyRole role;
    private Integer ownedClassCount;
}
