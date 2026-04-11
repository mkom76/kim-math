package com.example.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private Long userId;
    private String name;
    private String role; // STUDENT or TEACHER
    private String message;
    private List<MembershipDto> memberships;
    private Long activeAcademyId;
    private String activeRole;
}
