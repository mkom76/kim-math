package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.HomeworkDto;
import com.example.dto.HomeworkProblemDto;
import com.example.entity.Academy;
import com.example.entity.Homework;
import com.example.entity.HomeworkProblem;
import com.example.entity.AcademyClass;
import com.example.entity.TextbookProblem;
import com.example.exception.ForbiddenException;
import com.example.repository.AcademyRepository;
import com.example.repository.HomeworkProblemRepository;
import com.example.repository.HomeworkRepository;
import com.example.repository.AcademyClassRepository;
import com.example.repository.TextbookProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class HomeworkService {
    private final HomeworkRepository homeworkRepository;
    private final AcademyRepository academyRepository;
    private final AcademyClassRepository academyClassRepository;
    private final AuthorizationService authorizationService;
    private final HomeworkProblemRepository homeworkProblemRepository;
    private final TextbookProblemRepository textbookProblemRepository;

    public Page<HomeworkDto> getHomeworks(Pageable pageable) {
        return homeworkRepository.findAll(pageable).map(HomeworkDto::from);
    }

    public HomeworkDto createHomework(HomeworkDto dto) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null) {
            throw new ForbiddenException("인증 컨텍스트가 없습니다");
        }
        if (dto.getAcademyId() != null && !dto.getAcademyId().equals(ctx.academyId())) {
            throw new ForbiddenException("활성 학원과 다른 학원에 숙제를 생성할 수 없습니다");
        }

        Academy academy = academyRepository.findById(ctx.academyId())
                .orElseThrow(() -> new RuntimeException("Academy not found"));
        AcademyClass academyClass = academyClassRepository.findById(dto.getClassId())
                .orElseThrow(() -> new RuntimeException("Class not found"));
        authorizationService.assertCanModifyClass(academyClass);

        Homework homework = Homework.builder()
                .title(dto.getTitle())
                .questionCount(dto.getQuestionCount())
                .memo(dto.getMemo())
                .dueDate(dto.getDueDate())
                .academy(academy)
                .academyClass(academyClass)
                .lesson(null)  // Lesson will be attached later via LessonService
                .build();

        homework = homeworkRepository.save(homework);
        return HomeworkDto.from(homework);
    }

    public HomeworkDto updateHomework(Long id, HomeworkDto dto) {
        Homework homework = homeworkRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Homework not found"));
        authorizationService.assertCanAccessHomework(homework);

        homework.setTitle(dto.getTitle());
        homework.setQuestionCount(dto.getQuestionCount());
        homework.setMemo(dto.getMemo());
        homework.setDueDate(dto.getDueDate());

        if (dto.getAcademyId() != null && !dto.getAcademyId().equals(homework.getAcademy().getId())) {
            Academy academy = academyRepository.findById(dto.getAcademyId())
                    .orElseThrow(() -> new RuntimeException("Academy not found"));
            homework.setAcademy(academy);
        }

        if (dto.getClassId() != null && !dto.getClassId().equals(homework.getAcademyClass().getId())) {
            AcademyClass academyClass = academyClassRepository.findById(dto.getClassId())
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            authorizationService.assertCanModifyClass(academyClass);
            homework.setAcademyClass(academyClass);
        }

        homework = homeworkRepository.save(homework);
        return HomeworkDto.from(homework);
    }

    @Transactional(readOnly = true)
    public List<HomeworkProblemDto> getProblems(Long homeworkId) {
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("Homework not found"));
        authorizationService.assertCanAccessHomework(homework);
        return homeworkProblemRepository.findByHomeworkIdOrderByPositionAsc(homeworkId).stream()
                .map(HomeworkProblemDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 숙제의 문제 구성을 일괄 교체. 기존 행 전체 삭제 후 입력 순서대로 position 1..N 부여.
     * items의 textbookProblemId가 null이면 수동 슬롯으로 저장.
     */
    public List<HomeworkProblemDto> replaceProblems(Long homeworkId, List<ReplaceProblemItem> items) {
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("Homework not found"));
        authorizationService.assertCanAccessHomework(homework);

        TenantContext.Context ctx = TenantContext.current();
        Long teacherId = ctx != null ? ctx.teacherId() : null;

        homeworkProblemRepository.deleteByHomeworkId(homeworkId);
        homeworkProblemRepository.flush();

        List<HomeworkProblem> toSave = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ReplaceProblemItem item = items.get(i);
            TextbookProblem tp = null;
            if (item.textbookProblemId() != null) {
                tp = textbookProblemRepository.findById(item.textbookProblemId())
                        .orElseThrow(() -> new ForbiddenException("교재 문제를 찾을 수 없습니다"));
                if (tp.getTextbook() == null
                        || teacherId == null
                        || !teacherId.equals(tp.getTextbook().getOwnerTeacherId())) {
                    throw new ForbiddenException("본인의 교재 문제만 사용할 수 있습니다");
                }
            }
            toSave.add(HomeworkProblem.builder()
                    .homework(homework)
                    .textbookProblem(tp)
                    .position(i + 1)
                    .build());
        }
        List<HomeworkProblem> saved = homeworkProblemRepository.saveAll(toSave);

        // questionCount 자동 동기화
        homework.setQuestionCount(saved.size());
        homeworkRepository.save(homework);

        return saved.stream().map(HomeworkProblemDto::from).collect(Collectors.toList());
    }

    public record ReplaceProblemItem(Long textbookProblemId) {}

    public void deleteHomework(Long id) {
        Homework homework = homeworkRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Homework not found"));
        authorizationService.assertCanAccessHomework(homework);
        homeworkRepository.delete(homework);
    }

    public java.util.List<HomeworkDto> getUnattachedHomeworks(Long academyId, Long classId) {
        return homeworkRepository.findUnattachedByAcademyIdAndClassId(academyId, classId)
                .stream()
                .map(HomeworkDto::from)
                .collect(java.util.stream.Collectors.toList());
    }
}
