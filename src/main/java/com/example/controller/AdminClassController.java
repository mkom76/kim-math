package com.example.controller;

import com.example.config.security.TenantContext;
import com.example.dto.UpdateClassOwnerRequest;
import com.example.entity.AcademyClass;
import com.example.exception.ForbiddenException;
import com.example.repository.AcademyClassRepository;
import com.example.service.MembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/classes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ACADEMY_ADMIN')")
public class AdminClassController {

    private final AcademyClassRepository academyClassRepository;
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

        try {
            membershipService.requireMembership(req.getTeacherId(), academyId);
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("선택된 선생님이 해당 학원의 멤버가 아닙니다");
        }

        clazz.setOwnerTeacherId(req.getTeacherId());
        academyClassRepository.save(clazz);

        return ResponseEntity.ok().build();
    }
}
