package com.example.dto;

import com.example.entity.TeacherAcademyRole;
import lombok.Data;

@Data
public class InviteTeacherRequest {
    private String username;
    private String name;
    private String tempPin;
    private TeacherAcademyRole role;
}
