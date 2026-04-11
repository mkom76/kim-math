package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.AcademyClassDto;
import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.exception.ForbiddenException;
import com.example.repository.AcademyRepository;
import com.example.repository.AcademyClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AcademyClassService {
    private final AcademyClassRepository academyClassRepository;
    private final AcademyRepository academyRepository;
    private final MembershipService membershipService;
    private final AuthorizationService authorizationService;

    public Page<AcademyClassDto> getClasses(Pageable pageable) {
        return academyClassRepository.findAll(pageable).map(AcademyClassDto::from);
    }

    public AcademyClassDto createClass(AcademyClassDto dto) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null) {
            throw new ForbiddenException("인증 컨텍스트가 없습니다");
        }

        // Force active academy from session — ignore dto.academyId if it differs
        if (dto.getAcademyId() != null && !dto.getAcademyId().equals(ctx.academyId())) {
            throw new ForbiddenException("활성 학원과 다른 학원에 반을 생성할 수 없습니다");
        }

        Academy academy = academyRepository.findById(ctx.academyId())
                .orElseThrow(() -> new RuntimeException("Academy not found"));

        // Owner: defaults to current teacher. Cross-owner assignment is admin-only.
        Long ownerTeacherId;
        if (dto.getOwnerTeacherId() == null || dto.getOwnerTeacherId().equals(ctx.teacherId())) {
            ownerTeacherId = ctx.teacherId();
        } else {
            authorizationService.assertIsAcademyAdmin();
            ownerTeacherId = dto.getOwnerTeacherId();
        }

        // Validate the owner is a member of the active academy
        try {
            membershipService.requireMembership(ownerTeacherId, academy.getId());
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("선택된 선생님이 해당 학원의 멤버가 아닙니다");
        }

        AcademyClass academyClass = AcademyClass.builder()
                .name(dto.getName())
                .academy(academy)
                .ownerTeacherId(ownerTeacherId)
                .clinicDayOfWeek(dto.getClinicDayOfWeek())
                .clinicTime(dto.getClinicTime())
                .build();

        academyClass = academyClassRepository.save(academyClass);
        return AcademyClassDto.from(academyClass);
    }

    public AcademyClassDto updateClass(Long id, AcademyClassDto dto) {
        AcademyClass academyClass = academyClassRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        authorizationService.assertCanModifyClass(academyClass);

        // Reject academy reassignment — would break tenant isolation
        if (dto.getAcademyId() != null && !dto.getAcademyId().equals(academyClass.getAcademy().getId())) {
            throw new ForbiddenException("반의 소속 학원은 변경할 수 없습니다");
        }

        // Reject owner change via this endpoint — use the admin owner-reassignment endpoint instead
        if (dto.getOwnerTeacherId() != null && !dto.getOwnerTeacherId().equals(academyClass.getOwnerTeacherId())) {
            throw new ForbiddenException("반 담당자 변경은 어드민 전용 엔드포인트를 사용하세요");
        }

        academyClass.setName(dto.getName());
        academyClass.setClinicDayOfWeek(dto.getClinicDayOfWeek());
        academyClass.setClinicTime(dto.getClinicTime());

        academyClass = academyClassRepository.save(academyClass);
        return AcademyClassDto.from(academyClass);
    }

    public void deleteClass(Long id) {
        AcademyClass academyClass = academyClassRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Class not found"));
        authorizationService.assertCanModifyClass(academyClass);

        // Safety: prevent destructive cascade. CASCADE.ALL on students would wipe submissions/homework history.
        if (academyClass.getStudents() != null && !academyClass.getStudents().isEmpty()) {
            throw new ForbiddenException("학생이 등록된 반은 삭제할 수 없습니다. 학생을 다른 반으로 이동한 뒤 다시 시도하세요.");
        }

        academyClassRepository.delete(academyClass);
    }
}
