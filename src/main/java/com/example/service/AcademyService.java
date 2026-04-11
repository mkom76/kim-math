package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.AcademyDto;
import com.example.entity.Academy;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.exception.ForbiddenException;
import com.example.repository.AcademyRepository;
import com.example.repository.TeacherAcademyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AcademyService {
    private final AcademyRepository academyRepository;
    private final TeacherAcademyRepository teacherAcademyRepository;

    public Page<AcademyDto> getAcademies(Pageable pageable) {
        Long teacherId = requireTeacherId();
        List<Long> academyIds = teacherAcademyRepository.findByTeacherId(teacherId)
                .stream()
                .map(TeacherAcademy::getAcademyId)
                .toList();
        if (academyIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return academyRepository.findByIdIn(academyIds, pageable).map(AcademyDto::from);
    }

    public AcademyDto getAcademy(Long id) {
        requireMembership(id);
        Academy academy = academyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Academy not found"));
        return AcademyDto.from(academy);
    }

    public AcademyDto updateAcademy(Long id, AcademyDto dto) {
        TeacherAcademy membership = requireMembership(id);
        if (membership.getRole() != TeacherAcademyRole.ACADEMY_ADMIN) {
            throw new ForbiddenException("학원 수정은 어드민만 가능합니다");
        }
        Academy academy = academyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Academy not found"));

        academy.setName(dto.getName());
        academy = academyRepository.save(academy);
        return AcademyDto.from(academy);
    }

    private Long requireTeacherId() {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.teacherId() == null) {
            throw new ForbiddenException("인증 컨텍스트가 없습니다");
        }
        return ctx.teacherId();
    }

    private TeacherAcademy requireMembership(Long academyId) {
        Long teacherId = requireTeacherId();
        return teacherAcademyRepository.findByTeacherIdAndAcademyId(teacherId, academyId)
                .orElseThrow(() -> new ForbiddenException("이 학원에 접근할 수 없습니다"));
    }
}
