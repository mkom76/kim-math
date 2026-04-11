package com.example.dto;

import com.example.entity.TeacherAcademyRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRoleRequest {
    @NotNull
    private TeacherAcademyRole role;
}
