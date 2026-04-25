package com.example.repository;

import com.example.config.JpaAuditingConfig;
import com.example.entity.Teacher;
import com.example.entity.Textbook;
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
class TextbookRepositoryTest {

    @Autowired
    private TextbookRepository textbookRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void persists_textbook_with_owner_and_title() {
        Teacher teacher = new Teacher();
        teacher.setName("선생A");
        teacher.setUsername("teachera");
        teacher.setPin("000000");
        em.persist(teacher);

        Textbook tb = Textbook.builder()
                .ownerTeacherId(teacher.getId())
                .title("쎈 수학 상")
                .build();
        Textbook saved = textbookRepository.saveAndFlush(tb);
        em.clear();

        Textbook loaded = textbookRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getTitle()).isEqualTo("쎈 수학 상");
        assertThat(loaded.getOwnerTeacherId()).isEqualTo(teacher.getId());
    }

    @Test
    void finds_textbooks_by_owner() {
        Teacher t1 = new Teacher();
        t1.setName("선생A"); t1.setUsername("a"); t1.setPin("111111");
        em.persist(t1);
        Teacher t2 = new Teacher();
        t2.setName("선생B"); t2.setUsername("b"); t2.setPin("222222");
        em.persist(t2);

        textbookRepository.save(Textbook.builder().ownerTeacherId(t1.getId()).title("A의 교재 1").build());
        textbookRepository.save(Textbook.builder().ownerTeacherId(t1.getId()).title("A의 교재 2").build());
        textbookRepository.save(Textbook.builder().ownerTeacherId(t2.getId()).title("B의 교재").build());
        em.flush();

        List<Textbook> mine = textbookRepository.findByOwnerTeacherIdOrderByCreatedAtDesc(t1.getId());
        assertThat(mine).hasSize(2);
        assertThat(mine).extracting(Textbook::getTitle).containsExactlyInAnyOrder("A의 교재 1", "A의 교재 2");
    }
}
