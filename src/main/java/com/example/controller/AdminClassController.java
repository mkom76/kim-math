package com.example.controller;

import com.example.config.security.TenantContext;
import com.example.dto.UpdateClassOwnerRequest;
import com.example.entity.AcademyClass;
import com.example.entity.ClassAssistant;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.exception.ForbiddenException;
import com.example.repository.AcademyClassRepository;
import com.example.repository.ClassAssistantRepository;
import com.example.service.MembershipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/classes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ACADEMY_ADMIN')")
public class AdminClassController {

    private final AcademyClassRepository academyClassRepository;
    private final ClassAssistantRepository classAssistantRepository;
    private final MembershipService membershipService;

    @PatchMapping("/{classId}/owner")
    @Transactional
    public ResponseEntity<?> updateOwner(@PathVariable Long classId,
                                         @Valid @RequestBody UpdateClassOwnerRequest req) {
        Long academyId = TenantContext.current().academyId();

        AcademyClass clazz = academyClassRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("반을 찾을 수 없습니다"));

        Long classAcademyId = clazz.getAcademy() != null ? clazz.getAcademy().getId() : null;
        if (classAcademyId == null || !classAcademyId.equals(academyId)) {
            throw new ForbiddenException("다른 학원의 반은 수정할 수 없습니다");
        }

        TeacherAcademy membership;
        try {
            membership = membershipService.requireMembership(req.getTeacherId(), academyId);
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("선택된 선생님이 해당 학원의 멤버가 아닙니다");
        }

        // Assistants cannot own classes — that defeats their purpose.
        if (membership.getRole() == TeacherAcademyRole.ASSISTANT) {
            throw new ForbiddenException("조교는 반의 담당자로 지정할 수 없습니다");
        }

        clazz.setOwnerTeacherId(req.getTeacherId());
        academyClassRepository.save(clazz);

        return ResponseEntity.ok().build();
    }

    /**
     * Return assistant teacher IDs grouped by class for this academy. The
     * frontend uses this to render assistant chips on each class row.
     */
    @GetMapping("/assistants")
    public Map<Long, List<Long>> listAssistantsForAcademy() {
        Long academyId = TenantContext.current().academyId();

        List<Long> classIds = academyClassRepository.findAll().stream()
                .filter(c -> c.getAcademy() != null && academyId.equals(c.getAcademy().getId()))
                .map(AcademyClass::getId)
                .toList();

        if (classIds.isEmpty()) return Map.of();

        Map<Long, List<Long>> grouped = new HashMap<>();
        for (Long id : classIds) grouped.put(id, new java.util.ArrayList<>());
        for (ClassAssistant ca : classAssistantRepository.findByClassIdIn(classIds)) {
            grouped.get(ca.getClassId()).add(ca.getTeacherId());
        }
        return grouped;
    }

    @PostMapping("/{classId}/assistants")
    @Transactional
    public ResponseEntity<?> addAssistant(@PathVariable Long classId,
                                          @Valid @RequestBody AddAssistantRequest req) {
        Long academyId = TenantContext.current().academyId();

        AcademyClass clazz = academyClassRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("반을 찾을 수 없습니다"));
        Long classAcademyId = clazz.getAcademy() != null ? clazz.getAcademy().getId() : null;
        if (classAcademyId == null || !classAcademyId.equals(academyId)) {
            throw new ForbiddenException("다른 학원의 반은 수정할 수 없습니다");
        }

        // The assistant being assigned must be a member of this academy
        try {
            membershipService.requireMembership(req.getTeacherId(), academyId);
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("선택된 선생님이 해당 학원의 멤버가 아닙니다");
        }

        // Cannot assign the class owner as their own assistant (no-op + confusing)
        if (req.getTeacherId().equals(clazz.getOwnerTeacherId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "담당자는 이미 모든 권한을 가집니다"));
        }

        if (classAssistantRepository.existsByClassIdAndTeacherId(classId, req.getTeacherId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "이미 조교로 지정되어 있습니다"));
        }

        classAssistantRepository.save(ClassAssistant.builder()
                .classId(classId)
                .teacherId(req.getTeacherId())
                .build());

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{classId}/assistants/{teacherId}")
    @Transactional
    public ResponseEntity<?> removeAssistant(@PathVariable Long classId,
                                             @PathVariable Long teacherId) {
        Long academyId = TenantContext.current().academyId();

        AcademyClass clazz = academyClassRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("반을 찾을 수 없습니다"));
        Long classAcademyId = clazz.getAcademy() != null ? clazz.getAcademy().getId() : null;
        if (classAcademyId == null || !classAcademyId.equals(academyId)) {
            throw new ForbiddenException("다른 학원의 반은 수정할 수 없습니다");
        }

        long deleted = classAssistantRepository.deleteByClassIdAndTeacherId(classId, teacherId);
        if (deleted == 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "해당 조교 매핑이 없습니다"));
        }
        return ResponseEntity.ok().build();
    }

    @Data
    public static class AddAssistantRequest {
        @NotNull
        private Long teacherId;
    }
}
