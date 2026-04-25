package com.example.repository;

import com.example.config.JpaAuditingConfig;
import com.example.entity.QuestionType;
import com.example.entity.Teacher;
import com.example.entity.Textbook;
import com.example.entity.TextbookProblem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class TextbookProblemRepositoryTest {

    @Autowired
    private TextbookProblemRepository textbookProblemRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void persists_problem_with_full_metadata() {
        Teacher teacher = new Teacher();
        teacher.setName("선생A"); teacher.setUsername("a"); teacher.setPin("000000");
        em.persist(teacher);
        Textbook tb = em.persist(Textbook.builder().ownerTeacherId(teacher.getId()).title("쎈").build());

        TextbookProblem p = TextbookProblem.builder()
                .textbook(tb)
                .number(15)
                .answer("3")
                .questionType(QuestionType.OBJECTIVE)
                .topic("일차함수")
                .videoLink("https://youtube.com/watch?v=abc")
                .build();
        TextbookProblem saved = textbookProblemRepository.saveAndFlush(p);
        em.clear();

        TextbookProblem loaded = textbookProblemRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getNumber()).isEqualTo(15);
        assertThat(loaded.getAnswer()).isEqualTo("3");
        assertThat(loaded.getQuestionType()).isEqualTo(QuestionType.OBJECTIVE);
        assertThat(loaded.getTopic()).isEqualTo("일차함수");
        assertThat(loaded.getVideoLink()).isEqualTo("https://youtube.com/watch?v=abc");
    }

    @Test
    void finds_problems_by_textbook_ordered_by_number() {
        Teacher teacher = new Teacher();
        teacher.setName("선생A"); teacher.setUsername("a"); teacher.setPin("000000");
        em.persist(teacher);
        Textbook tb = em.persist(Textbook.builder().ownerTeacherId(teacher.getId()).title("쎈").build());

        textbookProblemRepository.save(TextbookProblem.builder()
                .textbook(tb).number(3).questionType(QuestionType.OBJECTIVE).build());
        textbookProblemRepository.save(TextbookProblem.builder()
                .textbook(tb).number(1).questionType(QuestionType.OBJECTIVE).build());
        textbookProblemRepository.save(TextbookProblem.builder()
                .textbook(tb).number(2).questionType(QuestionType.OBJECTIVE).build());
        em.flush();

        List<TextbookProblem> problems = textbookProblemRepository.findByTextbookIdOrderByNumberAsc(tb.getId());
        assertThat(problems).extracting(TextbookProblem::getNumber).containsExactly(1, 2, 3);
    }
}
