package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.TextbookProblemDto;
import com.example.entity.QuestionType;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademyRole;
import com.example.entity.Textbook;
import com.example.exception.ForbiddenException;
import com.example.repository.TextbookProblemRepository;
import com.example.repository.TextbookRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TextbookProblemServiceTest {

    @Autowired
    private TextbookProblemService textbookProblemService;

    @Autowired
    private TextbookRepository textbookRepository;

    @Autowired
    private TextbookProblemRepository textbookProblemRepository;

    @PersistenceContext
    private EntityManager em;

    private Teacher teacherA;
    private Teacher teacherB;
    private Textbook textbookA;
    private Textbook textbookB;
    private Long academyId = 100L;

    @BeforeEach
    void setUp() {
        teacherA = new Teacher();
        teacherA.setName("선생A"); teacherA.setUsername("a"); teacherA.setPin("000001");
        em.persist(teacherA);
        teacherB = new Teacher();
        teacherB.setName("선생B"); teacherB.setUsername("b"); teacherB.setPin("000002");
        em.persist(teacherB);

        textbookA = textbookRepository.save(Textbook.builder().ownerTeacherId(teacherA.getId()).title("A 교재").build());
        textbookB = textbookRepository.save(Textbook.builder().ownerTeacherId(teacherB.getId()).title("B 교재").build());
        em.flush();

        TenantContext.set(teacherA.getId(), academyId, TeacherAcademyRole.TEACHER);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_persists_problem_with_full_metadata() {
        TextbookProblemDto dto = textbookProblemService.create(textbookA.getId(),
                TextbookProblemDto.builder()
                        .number(7).answer("3").questionType(QuestionType.OBJECTIVE)
                        .topic("일차함수").videoLink("https://y/x").build());

        assertThat(dto.getId()).isNotNull();
        assertThat(dto.getTopic()).isEqualTo("일차함수");
        assertThat(dto.getVideoLink()).isEqualTo("https://y/x");
    }

    @Test
    void create_throws_when_textbook_owned_by_other_teacher() {
        assertThatThrownBy(() -> textbookProblemService.create(textbookB.getId(),
                TextbookProblemDto.builder().number(1).questionType(QuestionType.OBJECTIVE).build()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void list_returns_problems_in_owned_textbook() {
        textbookProblemService.create(textbookA.getId(),
                TextbookProblemDto.builder().number(2).questionType(QuestionType.OBJECTIVE).build());
        textbookProblemService.create(textbookA.getId(),
                TextbookProblemDto.builder().number(1).questionType(QuestionType.OBJECTIVE).build());

        List<TextbookProblemDto> list = textbookProblemService.listByTextbook(textbookA.getId());
        assertThat(list).extracting(TextbookProblemDto::getNumber).containsExactly(1, 2);
    }

    @Test
    void list_throws_when_textbook_owned_by_other_teacher() {
        assertThatThrownBy(() -> textbookProblemService.listByTextbook(textbookB.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_changes_fields_for_owner() {
        TextbookProblemDto created = textbookProblemService.create(textbookA.getId(),
                TextbookProblemDto.builder().number(1).answer("1").questionType(QuestionType.OBJECTIVE).build());

        TextbookProblemDto updated = textbookProblemService.update(created.getId(),
                TextbookProblemDto.builder().number(1).answer("5")
                        .questionType(QuestionType.SUBJECTIVE).topic("이차방정식").build());

        assertThat(updated.getAnswer()).isEqualTo("5");
        assertThat(updated.getQuestionType()).isEqualTo(QuestionType.SUBJECTIVE);
        assertThat(updated.getTopic()).isEqualTo("이차방정식");
    }

    @Test
    void update_throws_for_other_teachers_problem() {
        var bsProblem = textbookProblemRepository.save(com.example.entity.TextbookProblem.builder()
                .textbook(textbookB).number(1).questionType(QuestionType.OBJECTIVE).build());

        assertThatThrownBy(() -> textbookProblemService.update(bsProblem.getId(),
                TextbookProblemDto.builder().number(1).questionType(QuestionType.OBJECTIVE).build()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void delete_removes_problem_for_owner() {
        TextbookProblemDto created = textbookProblemService.create(textbookA.getId(),
                TextbookProblemDto.builder().number(1).questionType(QuestionType.OBJECTIVE).build());

        textbookProblemService.delete(created.getId());

        assertThat(textbookProblemRepository.findById(created.getId())).isEmpty();
    }

    // ON DELETE SET NULL 동작은 라이브 QA에서 검증 (JPA 세션 내 참조 정리 이슈로
    // 단위 테스트 작성이 까다로움 — @OnDelete(SET_NULL) 자체는 Hibernate가 보장).

    @Test
    void delete_throws_for_other_teachers_problem() {
        var bsProblem = textbookProblemRepository.save(com.example.entity.TextbookProblem.builder()
                .textbook(textbookB).number(1).questionType(QuestionType.OBJECTIVE).build());

        assertThatThrownBy(() -> textbookProblemService.delete(bsProblem.getId()))
                .isInstanceOf(ForbiddenException.class);
    }
}
