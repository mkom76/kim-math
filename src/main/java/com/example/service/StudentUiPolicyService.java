package com.example.service;

import com.example.entity.Student;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudentUiPolicyService {
    public static final String LEGACY = "legacy";
    public static final String V2 = "v2";

    private final Set<Long> v2DefaultAcademyIds;

    public StudentUiPolicyService(
            @Value("${app.student-ui.v2-default-academy-ids:}") String academyIds) {
        this.v2DefaultAcademyIds = parseAcademyIds(academyIds);
    }

    public String defaultModeForStudent(Student student) {
        if (student == null) return LEGACY;
        if (student.getAcademy() != null) {
            return defaultModeForAcademy(student.getAcademy().getId());
        }
        if (student.getAcademyClass() != null && student.getAcademyClass().getAcademy() != null) {
            return defaultModeForAcademy(student.getAcademyClass().getAcademy().getId());
        }
        return LEGACY;
    }

    public String defaultModeForAcademy(Long academyId) {
        return academyId != null && v2DefaultAcademyIds.contains(academyId) ? V2 : LEGACY;
    }

    private Set<Long> parseAcademyIds(String academyIds) {
        if (academyIds == null || academyIds.isBlank()) return Set.of();
        return Arrays.stream(academyIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }
}
