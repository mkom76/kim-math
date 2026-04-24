package com.example.repository;

import com.example.config.JpaAuditingConfig;
import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Homework;
import com.example.entity.Student;
import com.example.entity.StudentHomework;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class StudentHomeworkRepositoryTest {

    @Autowired
    private StudentHomeworkRepository studentHomeworkRepository;

    @Autowired
    private org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager em;

    @Test
    void persists_questioned_questions_field() {
        Academy academy = em.persist(Academy.builder().name("학원A").build());
        AcademyClass academyClass = em.persist(AcademyClass.builder().name("반1").academy(academy).build());
        Student student = em.persist(Student.builder()
                .name("학생1").grade("중3").school("학교A")
                .academy(academy).academyClass(academyClass).build());
        Homework homework = em.persist(Homework.builder()
                .title("숙제1").questionCount(20)
                .academy(academy).academyClass(academyClass).build());

        StudentHomework sh = StudentHomework.builder()
                .student(student)
                .homework(homework)
                .questionedQuestions("3,7,12")
                .build();
        StudentHomework saved = studentHomeworkRepository.saveAndFlush(sh);
        em.clear();

        StudentHomework loaded = studentHomeworkRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getQuestionedQuestions()).isEqualTo("3,7,12");
    }
}
