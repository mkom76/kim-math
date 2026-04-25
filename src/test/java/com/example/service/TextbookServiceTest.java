package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.TextbookDto;
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
class TextbookServiceTest {

    @Autowired
    private TextbookService textbookService;

    @Autowired
    private TextbookRepository textbookRepository;

    @Autowired
    private TextbookProblemRepository textbookProblemRepository;

    @PersistenceContext
    private EntityManager em;

    private Teacher teacherA;
    private Teacher teacherB;
    private Long academyId = 100L; // arbitrary, only needed for context

    @BeforeEach
    void setUp() {
        teacherA = new Teacher();
        teacherA.setName("선생A"); teacherA.setUsername("a"); teacherA.setPin("000001");
        em.persist(teacherA);
        teacherB = new Teacher();
        teacherB.setName("선생B"); teacherB.setUsername("b"); teacherB.setPin("000002");
        em.persist(teacherB);
        em.flush();

        TenantContext.set(teacherA.getId(), academyId, TeacherAcademyRole.TEACHER);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_assigns_current_teacher_as_owner() {
        TextbookDto dto = textbookService.create("쎈 수학 상");
        assertThat(dto.getId()).isNotNull();
        assertThat(dto.getTitle()).isEqualTo("쎈 수학 상");
        assertThat(dto.getOwnerTeacherId()).isEqualTo(teacherA.getId());
    }

    @Test
    void listMine_returns_only_owned_textbooks() {
        textbookService.create("A의 1");
        textbookService.create("A의 2");

        // B의 교재
        textbookRepository.save(Textbook.builder().ownerTeacherId(teacherB.getId()).title("B의 교재").build());

        List<TextbookDto> mine = textbookService.listMine();
        assertThat(mine).extracting(TextbookDto::getTitle).containsExactlyInAnyOrder("A의 1", "A의 2");
    }

    @Test
    void get_returns_for_owner() {
        TextbookDto created = textbookService.create("내 교재");
        TextbookDto fetched = textbookService.get(created.getId());
        assertThat(fetched.getTitle()).isEqualTo("내 교재");
    }

    @Test
    void get_throws_for_non_owner() {
        Textbook bsBook = textbookRepository.save(
                Textbook.builder().ownerTeacherId(teacherB.getId()).title("B의 교재").build());

        assertThatThrownBy(() -> textbookService.get(bsBook.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_changes_title_for_owner() {
        TextbookDto created = textbookService.create("원래 제목");
        TextbookDto updated = textbookService.update(created.getId(), "새 제목");
        assertThat(updated.getTitle()).isEqualTo("새 제목");
    }

    @Test
    void update_throws_for_non_owner() {
        Textbook bsBook = textbookRepository.save(
                Textbook.builder().ownerTeacherId(teacherB.getId()).title("B의 교재").build());

        assertThatThrownBy(() -> textbookService.update(bsBook.getId(), "탈취"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void delete_removes_for_owner() {
        TextbookDto created = textbookService.create("지울 교재");
        textbookService.delete(created.getId());
        assertThat(textbookRepository.findById(created.getId())).isEmpty();
    }

    @Test
    void delete_throws_for_non_owner() {
        Textbook bsBook = textbookRepository.save(
                Textbook.builder().ownerTeacherId(teacherB.getId()).title("B의 교재").build());

        assertThatThrownBy(() -> textbookService.delete(bsBook.getId()))
                .isInstanceOf(ForbiddenException.class);
    }
}
