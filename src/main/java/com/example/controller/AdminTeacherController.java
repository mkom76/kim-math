package com.example.controller;

import com.example.config.security.TenantContext;
import com.example.dto.AdminTeacherDto;
import com.example.dto.InviteTeacherRequest;
import com.example.dto.UpdateRoleRequest;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.exception.ForbiddenException;
import com.example.repository.AcademyClassRepository;
import com.example.repository.TeacherAcademyRepository;
import com.example.repository.TeacherRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/teachers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ACADEMY_ADMIN')")
public class AdminTeacherController {

    private final TeacherRepository teacherRepository;
    private final TeacherAcademyRepository teacherAcademyRepository;
    private final AcademyClassRepository academyClassRepository;

    @GetMapping
    public List<AdminTeacherDto> listMembers() {
        Long academyId = TenantContext.current().academyId();
        List<TeacherAcademy> memberships = teacherAcademyRepository.findByAcademyId(academyId);
        if (memberships.isEmpty()) return List.of();

        List<Long> teacherIds = memberships.stream().map(TeacherAcademy::getTeacherId).toList();
        Map<Long, Teacher> teachers = teacherRepository.findAllById(teacherIds).stream()
                .collect(java.util.stream.Collectors.toMap(Teacher::getId, java.util.function.Function.identity()));

        return memberships.stream()
                .map(m -> {
                    Teacher t = teachers.get(m.getTeacherId());
                    if (t == null) return null;
                    int classCount = academyClassRepository
                            .countByAcademy_IdAndOwnerTeacherId(academyId, t.getId());
                    return AdminTeacherDto.builder()
                            .teacherId(t.getId())
                            .name(t.getName())
                            .username(t.getUsername())
                            .role(m.getRole())
                            .ownedClassCount(classCount)
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> invite(@Valid @RequestBody InviteTeacherRequest req) {
        Long academyId = TenantContext.current().academyId();

        if (req.getUsername() == null || req.getUsername().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "username이 필요합니다"));
        }

        Optional<Teacher> existing = teacherRepository.findByUsername(req.getUsername());
        Teacher teacher = existing.orElseGet(() -> {
            if (req.getName() == null || req.getName().isBlank() ||
                req.getTempPin() == null || req.getTempPin().isBlank()) {
                throw new IllegalArgumentException("새 선생님 생성 시 name과 tempPin이 필요합니다");
            }
            Teacher t = Teacher.builder()
                    .username(req.getUsername())
                    .name(req.getName())
                    .pin(req.getTempPin())
                    .build();
            return teacherRepository.save(t);
        });

        if (teacherAcademyRepository.findByTeacherIdAndAcademyId(teacher.getId(), academyId).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "이미 멤버입니다"));
        }

        TeacherAcademyRole role = req.getRole() != null ? req.getRole() : TeacherAcademyRole.TEACHER;
        TeacherAcademy membership = TeacherAcademy.builder()
                .teacherId(teacher.getId())
                .academyId(academyId)
                .role(role)
                .build();
        teacherAcademyRepository.save(membership);

        return ResponseEntity.ok(Map.of(
                "teacherId", teacher.getId(),
                "username", teacher.getUsername(),
                "role", role.name()
        ));
    }

    @PatchMapping("/{teacherId}/role")
    @Transactional
    public ResponseEntity<?> updateRole(@PathVariable Long teacherId, @Valid @RequestBody UpdateRoleRequest req) {
        Long academyId = TenantContext.current().academyId();
        Long adminId = TenantContext.current().teacherId();

        TeacherAcademy m = teacherAcademyRepository.findByTeacherIdAndAcademyId(teacherId, academyId)
                .orElseThrow(() -> new ForbiddenException("멤버가 아닙니다"));

        // Prevent admin from demoting themselves if they are the only admin
        if (teacherId.equals(adminId) && req.getRole() == TeacherAcademyRole.TEACHER) {
            long adminCount = teacherAcademyRepository.findByAcademyId(academyId).stream()
                    .filter(ta -> ta.getRole() == TeacherAcademyRole.ACADEMY_ADMIN)
                    .count();
            if (adminCount <= 1) {
                throw new ForbiddenException("본인은 마지막 관리자이므로 강등할 수 없습니다");
            }
        }

        m.setRole(req.getRole());
        teacherAcademyRepository.save(m);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{teacherId}")
    @Transactional
    public ResponseEntity<?> remove(@PathVariable Long teacherId) {
        Long adminId = TenantContext.current().teacherId();
        Long academyId = TenantContext.current().academyId();

        if (teacherId.equals(adminId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "본인은 제거할 수 없습니다"));
        }

        // Verify the target teacher is actually a member
        if (teacherAcademyRepository.findByTeacherIdAndAcademyId(teacherId, academyId).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "멤버가 아닙니다"));
        }

        // Auto-transfer ownership of any classes owned by this teacher to the admin
        academyClassRepository.transferOwnership(academyId, teacherId, adminId);

        // Remove membership
        teacherAcademyRepository.deleteByTeacherIdAndAcademyId(teacherId, academyId);

        return ResponseEntity.ok().build();
    }
}
