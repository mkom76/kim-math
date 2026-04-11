package com.example.service;

import com.example.dto.MembershipDto;
import com.example.entity.Academy;
import com.example.entity.TeacherAcademy;
import com.example.repository.AcademyRepository;
import com.example.repository.TeacherAcademyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private final TeacherAcademyRepository teacherAcademyRepository;
    private final AcademyRepository academyRepository;

    public List<MembershipDto> getMembershipsForTeacher(Long teacherId) {
        List<TeacherAcademy> memberships = teacherAcademyRepository.findByTeacherId(teacherId);
        if (memberships.isEmpty()) return List.of();

        Map<Long, Academy> academies = academyRepository.findAllById(
            memberships.stream().map(TeacherAcademy::getAcademyId).toList()
        ).stream().collect(Collectors.toMap(Academy::getId, Function.identity()));

        return memberships.stream()
            .map(m -> {
                Academy academy = academies.get(m.getAcademyId());
                if (academy == null) return null;  // skip orphaned membership
                return MembershipDto.builder()
                    .academyId(m.getAcademyId())
                    .academyName(academy.getName())
                    .role(m.getRole().name())
                    .build();
            })
            .filter(Objects::nonNull)
            .toList();
    }

    public TeacherAcademy requireMembership(Long teacherId, Long academyId) {
        return teacherAcademyRepository.findByTeacherIdAndAcademyId(teacherId, academyId)
            .orElseThrow(() -> new IllegalArgumentException("멤버십이 없습니다"));
    }
}
