package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.QuestionType;
import com.example.entity.Student;
import com.example.entity.StudentSubmission;
import com.example.entity.StudentSubmissionDetail;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.entity.TestQuestion;
import com.example.repository.StudentSubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherSubmissionEntryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentSubmissionRepository submissionRepository;
    @Autowired private ObjectMapper objectMapper;
    @PersistenceContext private EntityManager em;

    private Academy academy;
    private AcademyClass clazz;
    private AcademyClass otherClazz;
    private Student studentA;
    private Student studentB;
    private Student otherClassStudent;
    private Teacher teacher;
    private com.example.entity.Test exam;
    private TestQuestion q1;
    private TestQuestion q2;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("학원A").build();
        em.persist(academy);

        teacher = new Teacher();
        teacher.setName("선생A"); teacher.setUsername("t1"); teacher.setPin("000001");
        em.persist(teacher);

        em.persist(TeacherAcademy.builder()
                .teacherId(teacher.getId()).academyId(academy.getId())
                .role(TeacherAcademyRole.TEACHER).build());

        clazz = AcademyClass.builder().name("반1").academy(academy).ownerTeacherId(teacher.getId()).build();
        em.persist(clazz);
        otherClazz = AcademyClass.builder().name("반2").academy(academy).ownerTeacherId(teacher.getId()).build();
        em.persist(otherClazz);

        studentA = Student.builder()
                .name("김민수").grade("중3").school("학교A")
                .academy(academy).academyClass(clazz).build();
        em.persist(studentA);
        studentB = Student.builder()
                .name("박지윤").grade("중3").school("학교A")
                .academy(academy).academyClass(clazz).build();
        em.persist(studentB);
        otherClassStudent = Student.builder()
                .name("이서준").grade("중3").school("학교B")
                .academy(academy).academyClass(otherClazz).build();
        em.persist(otherClassStudent);

        exam = com.example.entity.Test.builder()
                .title("3월 시험").academy(academy).academyClass(clazz).build();
        em.persist(exam);

        q1 = TestQuestion.builder()
                .test(exam).number(1).answer("3").points(50.0)
                .questionType(QuestionType.OBJECTIVE).build();
        em.persist(q1);
        q2 = TestQuestion.builder()
                .test(exam).number(2).answer("x=2").points(50.0)
                .questionType(QuestionType.SUBJECTIVE).build();
        em.persist(q2);

        StudentSubmission submitted = StudentSubmission.builder()
                .student(studentB).test(exam).totalScore(50).build();
        submitted.getDetails().add(StudentSubmissionDetail.builder()
                .submission(submitted).question(q1).studentAnswer("3")
                .isCorrect(true).earnedPoints(50.0).build());
        submitted.getDetails().add(StudentSubmissionDetail.builder()
                .submission(submitted).question(q2).studentAnswer("x=1")
                .isCorrect(false).earnedPoints(0.0).build());
        em.persist(submitted);

        em.flush();
    }

    private MockHttpSession teacherSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", teacher.getId());
        session.setAttribute("userRole", "TEACHER");
        session.setAttribute("activeAcademyId", academy.getId());
        session.setAttribute("activeRole", "TEACHER");
        return session;
    }

    private MockHttpSession studentSession(Student student) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", student.getId());
        session.setAttribute("userRole", "STUDENT");
        session.setAttribute("studentAcademyId", academy.getId());
        return session;
    }

    private String answersRequest(Long testId, String answers) throws Exception {
        em.flush();
        var questionsResponse = mockMvc.perform(get("/api/tests/{id}/questions", testId).session(teacherSession()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var request = objectMapper.createObjectNode();
        request.set("answers", objectMapper.readTree(answers));
        var snapshot = request.putArray("expectedQuestions");
        for (var question : objectMapper.readTree(questionsResponse)) {
            var item = snapshot.addObject();
            item.set("id", question.get("id"));
            item.set("number", question.get("number"));
            item.set("updatedAt", question.get("updatedAt"));
        }
        return request.toString();
    }

    @Test
    void roster_includes_unsubmitted_students_in_test_class_only() throws Exception {
        mockMvc.perform(get("/api/tests/{id}/submission-roster", exam.getId())
                        .session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].studentName", contains("김민수", "박지윤")))
                .andExpect(jsonPath("$[0].submitted").value(false))
                .andExpect(jsonPath("$[0].submissionId").value(nullValue()))
                .andExpect(jsonPath("$[1].submitted").value(true))
                .andExpect(jsonPath("$[1].score").value(50));
    }

    @Test
    void teacher_can_save_answers_for_student() throws Exception {
        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}",
                        studentA.getId(), exam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(answersRequest(exam.getId(), "{\"1\":\"3\",\"2\":\"x=2\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.id").value(studentA.getId()))
                .andExpect(jsonPath("$.totalScore").value(100))
                .andExpect(jsonPath("$.details", hasSize(2)))
                .andExpect(jsonPath("$.details[0].studentAnswer").value("3"))
                .andExpect(jsonPath("$.details[0].isCorrect").value(true))
                .andExpect(jsonPath("$.details[1].studentAnswer").value("x=2"))
                .andExpect(jsonPath("$.details[1].isCorrect").value(true));
    }

    @Test
    void multiple_choice_answers_are_graded_as_an_order_independent_exact_set() throws Exception {
        com.example.entity.Test multipleExam = com.example.entity.Test.builder()
                .title("복수정답 시험").academy(academy).academyClass(clazz).build();
        em.persist(multipleExam);
        em.persist(TestQuestion.builder()
                .test(multipleExam).number(1).answer("1,3").multipleAnswers(true).points(100.0)
                .questionType(QuestionType.OBJECTIVE).build());
        em.flush();

        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}",
                        studentA.getId(), multipleExam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(answersRequest(multipleExam.getId(), "{\"1\":\"3,1\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(100))
                .andExpect(jsonPath("$.details[0].isCorrect").value(true));

        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}",
                        studentA.getId(), multipleExam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(answersRequest(multipleExam.getId(), "{\"1\":\"1\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(0))
                .andExpect(jsonPath("$.details[0].isCorrect").value(false));
    }

    @Test
    void student_question_payload_exposes_multiple_selection_mode_but_not_the_answer() throws Exception {
        q1.setMultipleAnswers(true);
        q1.setAnswer("1,3");
        em.flush();

        mockMvc.perform(get("/api/tests/{id}/questions", exam.getId())
                        .session(studentSession(studentA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].multipleAnswers").value(true))
                .andExpect(jsonPath("$[0].answer").doesNotExist());
    }

    @Test
    void changing_a_question_to_multiple_answers_recalculates_existing_submissions() throws Exception {
        com.example.entity.Test changingExam = com.example.entity.Test.builder()
                .title("재채점 시험").academy(academy).academyClass(clazz).build();
        em.persist(changingExam);
        TestQuestion changingQuestion = TestQuestion.builder()
                .test(changingExam).number(1).answer("1").multipleAnswers(false).points(100.0)
                .questionType(QuestionType.OBJECTIVE).build();
        em.persist(changingQuestion);
        em.flush();

        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}",
                        studentA.getId(), changingExam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(answersRequest(changingExam.getId(), "{\"1\":\"1\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(100));

        mockMvc.perform(put("/api/tests/{id}/answers", changingExam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("""
                                {"expectedQuestionIds":[%d],"answers":[{"id":%d,"number":1,"answer":"3, 1","multipleAnswers":true,"points":100,"questionType":"OBJECTIVE"}]}
                                """.formatted(changingQuestion.getId(), changingQuestion.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/submissions")
                        .param("studentId", String.valueOf(studentA.getId()))
                        .param("testId", String.valueOf(changingExam.getId()))
                        .session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(0));

        mockMvc.perform(get("/api/tests/{id}/questions", changingExam.getId())
                        .session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].answer").value("1,3"))
                .andExpect(jsonPath("$[0].multipleAnswers").value(true));
    }

    @Test
    void saving_new_questions_recalculates_existing_submissions_without_duplicate_details() throws Exception {
        mockMvc.perform(put("/api/tests/{id}/answers", exam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("""
                                {"expectedQuestionIds":[%d,%d],"answers":[
                                  {"id":%d,"number":1,"answer":"3","points":50,"questionType":"OBJECTIVE"},
                                  {"id":%d,"number":2,"answer":"x=2","points":25,"questionType":"SUBJECTIVE"},
                                  {"id":null,"number":3,"answer":"5","points":25,"questionType":"OBJECTIVE"}
                                ]}
                                """.formatted(q1.getId(), q2.getId(), q1.getId(), q2.getId())))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/submissions")
                        .param("studentId", String.valueOf(studentB.getId()))
                        .param("testId", String.valueOf(exam.getId()))
                        .session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(50))
                .andExpect(jsonPath("$.details", hasSize(3)))
                .andExpect(jsonPath("$.details[*].questionNumber", containsInAnyOrder(1, 2, 3)))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 1)].studentAnswer", contains("3")))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 2)].studentAnswer", contains("x=1")))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 3)].studentAnswer", contains(nullValue())))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 3)].isCorrect", contains(false)))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 3)].earnedPoints", contains(0.0)));

        mockMvc.perform(post("/api/tests/{id}/recalculate", exam.getId()).session(teacherSession()))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/submissions")
                        .param("studentId", String.valueOf(studentB.getId()))
                        .param("testId", String.valueOf(exam.getId()))
                        .session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(50))
                .andExpect(jsonPath("$.details", hasSize(3)));
    }

    @Test
    void manual_recalculation_fills_missing_answers_and_preserves_existing_essay_grades() throws Exception {
        q1.setPoints(25.0);
        q2.setPoints(25.0);
        TestQuestion gradedEssay = TestQuestion.builder()
                .test(exam).number(3).points(10.0).questionType(QuestionType.ESSAY).build();
        em.persist(gradedEssay);
        em.persist(TestQuestion.builder()
                .test(exam).number(4).answer("5").points(20.0).questionType(QuestionType.SUBJECTIVE).build());
        em.persist(TestQuestion.builder()
                .test(exam).number(5).points(20.0).questionType(QuestionType.ESSAY).build());
        StudentSubmission submission = submissionRepository
                .findByStudentIdAndTestId(studentB.getId(), exam.getId()).orElseThrow();
        StudentSubmissionDetail gradedDetail = StudentSubmissionDetail.builder()
                .submission(submission).question(gradedEssay).studentAnswer("풀이")
                .earnedPoints(8.0).teacherComment("풀이 일부 인정").build();
        submission.getDetails().add(gradedDetail);
        em.persist(gradedDetail);
        em.flush();
        em.clear();

        mockMvc.perform(post("/api/tests/{id}/recalculate", exam.getId()).session(teacherSession()))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/submissions/{id}", submission.getId()).session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(33))
                .andExpect(jsonPath("$.details", hasSize(5)))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 3)].id", contains(gradedDetail.getId().intValue())))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 3)].studentAnswer", contains("풀이")))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 3)].earnedPoints", contains(8.0)))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 3)].teacherComment", contains("풀이 일부 인정")))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 4)].studentAnswer", contains(nullValue())))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 4)].isCorrect", contains(false)))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 4)].earnedPoints", contains(0.0)))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 5)].isCorrect", contains(nullValue())))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 5)].earnedPoints", contains(nullValue())));

        mockMvc.perform(get("/api/tests/{id}/submission-roster", exam.getId()).session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentA.getId() + ")].submitted", contains(false)))
                .andExpect(jsonPath("$[?(@.studentId == " + studentB.getId() + ")].pendingEssayCount", contains(1)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void deleting_a_question_preserves_remaining_answers_when_numbers_change(int deletedNumber) throws Exception {
        q1.setAnswer("1");
        q2.setAnswer("2");
        q2.setQuestionType(QuestionType.OBJECTIVE);
        TestQuestion q3 = TestQuestion.builder().test(exam).number(3).answer("3")
                .points(50.0).questionType(QuestionType.OBJECTIVE).build();
        em.persist(q3);
        StudentSubmission submission = submissionRepository
                .findByStudentIdAndTestId(studentB.getId(), exam.getId()).orElseThrow();
        for (StudentSubmissionDetail detail : submission.getDetails()) {
            detail.setStudentAnswer(detail.getQuestion().getNumber() == 1 ? "2" : "1");
            detail.setIsCorrect(false);
            detail.setEarnedPoints(0.0);
        }
        StudentSubmissionDetail correctDetail = StudentSubmissionDetail.builder()
                .submission(submission).question(q3).studentAnswer("3").isCorrect(true).earnedPoints(50.0).build();
        submission.getDetails().add(correctDetail);
        em.persist(correctDetail);
        em.flush();

        TestQuestion first = deletedNumber == 1 ? q2 : q1;
        TestQuestion second = deletedNumber == 3 ? q2 : q3;
        String firstStudentAnswer = deletedNumber == 1 ? "1" : "2";
        String secondStudentAnswer = deletedNumber == 3 ? "1" : "3";
        String body = """
                {"expectedQuestionIds":[%d,%d,%d],"answers":[
                  {"id":%d,"number":1,"answer":"%s","points":50,"questionType":"OBJECTIVE"},
                  {"id":%d,"number":2,"answer":"%s","points":50,"questionType":"OBJECTIVE"}
                ]}
                """.formatted(q1.getId(), q2.getId(), q3.getId(), first.getId(), first.getAnswer(), second.getId(), second.getAnswer());
        em.clear();
        mockMvc.perform(put("/api/tests/{id}/answers", exam.getId())
                        .session(teacherSession()).contentType("application/json").content(body))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/tests/{id}/questions", exam.getId()).session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(first.getId().intValue(), second.getId().intValue())));
        mockMvc.perform(get("/api/submissions/{id}", submission.getId()).session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(deletedNumber == 3 ? 0 : 50))
                .andExpect(jsonPath("$.details", hasSize(2)))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 1)].studentAnswer", contains(firstStudentAnswer)))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 2)].studentAnswer", contains(secondStudentAnswer)))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 1)].isCorrect", contains(false)))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 2)].isCorrect", contains(deletedNumber != 3)));
    }

    @Test
    void replacing_all_questions_creates_unanswered_details_instead_of_reusing_old_answers() throws Exception {
        mockMvc.perform(put("/api/tests/{id}/answers", exam.getId())
                        .session(teacherSession()).contentType("application/json")
                        .content("""
                                {"expectedQuestionIds":[%d,%d],"answers":[{"id":null,"number":1,"answer":"3","points":100,"questionType":"OBJECTIVE"}]}
                                """.formatted(q1.getId(), q2.getId())))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/submissions")
                        .param("studentId", String.valueOf(studentB.getId()))
                        .param("testId", String.valueOf(exam.getId())).session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(0))
                .andExpect(jsonPath("$.details", hasSize(1)))
                .andExpect(jsonPath("$.details[0].studentAnswer").value(nullValue()))
                .andExpect(jsonPath("$.details[0].isCorrect").value(false));
    }

    @Test
    void renumbering_an_essay_preserves_its_manual_grade_and_comment() throws Exception {
        q2.setQuestionType(QuestionType.ESSAY);
        StudentSubmission submission = submissionRepository
                .findByStudentIdAndTestId(studentB.getId(), exam.getId()).orElseThrow();
        StudentSubmissionDetail essay = submission.getDetails().stream()
                .filter(detail -> detail.getQuestion().getId().equals(q2.getId())).findFirst().orElseThrow();
        essay.setIsCorrect(null);
        essay.setEarnedPoints(30.0);
        essay.setTeacherComment("풀이 일부 인정");
        em.flush();

        mockMvc.perform(put("/api/tests/{id}/answers", exam.getId())
                        .session(teacherSession()).contentType("application/json")
                        .content("""
                                {"expectedQuestionIds":[%d,%d],"answers":[{"id":%d,"number":1,"answer":"","points":100,"questionType":"ESSAY"}]}
                                """.formatted(q1.getId(), q2.getId(), q2.getId())))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/submissions/{id}", submission.getId()).session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(30))
                .andExpect(jsonPath("$.details", hasSize(1)))
                .andExpect(jsonPath("$.details[0].id").value(essay.getId()))
                .andExpect(jsonPath("$.details[0].questionNumber").value(1))
                .andExpect(jsonPath("$.details[0].studentAnswer").value("x=1"))
                .andExpect(jsonPath("$.details[0].earnedPoints").value(30.0))
                .andExpect(jsonPath("$.details[0].teacherComment").value("풀이 일부 인정"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign_id", "duplicate_id", "duplicate_number", "missing_id"})
    void invalid_question_identity_is_rejected_before_changing_answers(String invalidInput) throws Exception {
        com.example.entity.Test otherExam = com.example.entity.Test.builder()
                .title("다른 시험").academy(academy).academyClass(clazz).build();
        em.persist(otherExam);
        TestQuestion otherQuestion = TestQuestion.builder().test(otherExam).number(1).answer("5")
                .points(100.0).questionType(QuestionType.OBJECTIVE).build();
        em.persist(otherQuestion);
        em.flush();

        Long secondId = switch (invalidInput) {
            case "foreign_id" -> otherQuestion.getId();
            case "duplicate_id" -> q1.getId();
            default -> q2.getId();
        };
        String firstId = invalidInput.equals("missing_id") ? "" : "\"id\":" + q1.getId() + ",";
        int secondNumber = invalidInput.equals("duplicate_number") ? 1 : 2;
        String body = """
                {"expectedQuestionIds":[%d,%d],"answers":[
                  {%s"number":1,"answer":"999","points":50,"questionType":"OBJECTIVE"},
                  {"id":%d,"number":%d,"answer":"999","points":50,"questionType":"SUBJECTIVE"}
                ]}
                """.formatted(q1.getId(), q2.getId(), firstId, secondId, secondNumber);
        mockMvc.perform(put("/api/tests/{id}/answers", exam.getId())
                        .session(teacherSession()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        em.clear();

        mockMvc.perform(get("/api/tests/{id}/questions", exam.getId()).session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].answer").value("3"))
                .andExpect(jsonPath("$[1].answer").value("x=2"));
    }

    @Test
    void retrying_a_committed_new_question_save_does_not_replace_student_answers() throws Exception {
        String body = """
                {"expectedQuestionIds":[%d,%d],"answers":[
                  {"id":%d,"number":1,"answer":"3","points":25,"questionType":"OBJECTIVE"},
                  {"id":%d,"number":2,"answer":"x=2","points":25,"questionType":"SUBJECTIVE"},
                  {"id":null,"number":3,"answer":"5","points":50,"questionType":"OBJECTIVE"}
                ]}
                """.formatted(q1.getId(), q2.getId(), q1.getId(), q2.getId());
        mockMvc.perform(put("/api/tests/{id}/answers", exam.getId())
                        .session(teacherSession()).contentType("application/json").content(body))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        Long addedQuestionId = em.createQuery(
                        "select q.id from TestQuestion q where q.test.id = :testId and q.number = 3", Long.class)
                .setParameter("testId", exam.getId()).getSingleResult();
        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}", studentA.getId(), exam.getId())
                        .session(teacherSession()).contentType("application/json")
                        .content(answersRequest(exam.getId(), "{\"1\":\"3\",\"2\":\"x=2\",\"3\":\"5\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(100));
        em.flush();
        em.clear();

        mockMvc.perform(put("/api/tests/{id}/answers", exam.getId())
                        .session(teacherSession()).contentType("application/json").content(body))
                .andExpect(status().isConflict());
        em.clear();

        mockMvc.perform(get("/api/tests/{id}/questions", exam.getId()).session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].id").value(addedQuestionId));
        mockMvc.perform(get("/api/submissions")
                        .param("studentId", String.valueOf(studentA.getId()))
                        .param("testId", String.valueOf(exam.getId())).session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(100))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 3)].studentAnswer", contains("5")))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 3)].isCorrect", contains(true)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "stale"})
    void invalid_question_snapshot_cannot_change_existing_questions(String invalidSnapshot) throws Exception {
        String snapshot = switch (invalidSnapshot) {
            case "missing" -> "";
            case "duplicate" -> "\"expectedQuestionIds\":[" + q1.getId() + "," + q1.getId() + "],";
            default -> "\"expectedQuestionIds\":[],";
        };
        String body = """
                {%s"answers":[
                  {"id":%d,"number":1,"answer":"999","points":50,"questionType":"OBJECTIVE"},
                  {"id":%d,"number":2,"answer":"999","points":50,"questionType":"SUBJECTIVE"}
                ]}
                """.formatted(snapshot, q1.getId(), q2.getId());
        mockMvc.perform(put("/api/tests/{id}/answers", exam.getId())
                        .session(teacherSession()).contentType("application/json").content(body))
                .andExpect(status().is(invalidSnapshot.equals("stale") ? 409 : 400));
        em.clear();

        mockMvc.perform(get("/api/tests/{id}/questions", exam.getId()).session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].answer").value("3"))
                .andExpect(jsonPath("$[1].answer").value("x=2"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete", "add", "number", "answer", "points", "type", "multiple"})
    void changed_questions_reject_both_teacher_answers_and_student_submissions(String change) throws Exception {
        String staleRequest = answersRequest(exam.getId(), "{\"1\":\"3\",\"2\":\"x=2\"}");
        switch (change) {
            case "delete" -> mockMvc.perform(put("/api/tests/{id}/answers", exam.getId())
                            .session(teacherSession()).contentType("application/json")
                            .content("""
                                    {"expectedQuestionIds":[%d,%d],"answers":[
                                      {"id":%d,"number":1,"answer":"x=2","points":100,"questionType":"SUBJECTIVE"}
                                    ]}
                                    """.formatted(q1.getId(), q2.getId(), q2.getId())))
                    .andExpect(status().isOk());
            case "add" -> em.persist(TestQuestion.builder().test(exam).number(3).answer("5")
                    .points(10.0).questionType(QuestionType.OBJECTIVE).build());
            case "number" -> q1.setNumber(3);
            case "answer" -> q1.setAnswer("5");
            case "points" -> q1.setPoints(25.0);
            case "type" -> q1.setQuestionType(QuestionType.ESSAY);
            case "multiple" -> q1.setMultipleAnswers(true);
        }
        em.flush();
        em.clear();
        var before = objectMapper.readTree(mockMvc.perform(get("/api/submissions")
                        .param("studentId", String.valueOf(studentB.getId()))
                        .param("testId", String.valueOf(exam.getId())).session(teacherSession()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}", studentB.getId(), exam.getId())
                        .session(teacherSession()).contentType("application/json").content(staleRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("새로고침")));
        mockMvc.perform(post("/api/submissions/me/test/{testId}", exam.getId())
                        .session(studentSession(studentA)).contentType("application/json").content(staleRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("새로고침")));
        em.clear();

        var after = objectMapper.readTree(mockMvc.perform(get("/api/submissions")
                        .param("studentId", String.valueOf(studentB.getId()))
                        .param("testId", String.valueOf(exam.getId())).session(teacherSession()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(after.get("details")).isEqualTo(before.get("details"));
        org.assertj.core.api.Assertions.assertThat(after.get("totalScore")).isEqualTo(before.get("totalScore"));
        org.assertj.core.api.Assertions.assertThat(submissionRepository
                .findByStudentIdAndTestId(studentA.getId(), exam.getId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"me", "legacy"})
    void unchanged_question_snapshot_allows_student_submission(String endpoint) throws Exception {
        String body = answersRequest(exam.getId(), "{\"1\":\"3\",\"2\":\"x=2\"}");
        var request = endpoint.equals("me")
                ? post("/api/submissions/me/test/{id}", exam.getId())
                : post("/api/submissions").param("studentId", studentA.getId().toString()).param("testId", exam.getId().toString());
        mockMvc.perform(request.session(studentSession(studentA)).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalScore").value(100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "legacy", "duplicate"})
    void missing_or_invalid_answer_snapshot_cannot_overwrite_existing_answers(String invalidSnapshot) throws Exception {
        String body;
        if (invalidSnapshot.equals("legacy")) {
            body = "{\"1\":\"1\",\"2\":\"wrong\"}";
        } else {
            var request = objectMapper.readTree(answersRequest(exam.getId(), "{\"1\":\"1\",\"2\":\"wrong\"}"));
            if (invalidSnapshot.equals("missing")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) request).remove("expectedQuestions");
            } else {
                var snapshot = (com.fasterxml.jackson.databind.node.ArrayNode) request.get("expectedQuestions");
                snapshot.add(snapshot.get(0).deepCopy());
            }
            body = request.toString();
        }
        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}", studentB.getId(), exam.getId())
                        .session(teacherSession()).contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("새로고침")));
        em.clear();
        mockMvc.perform(get("/api/submissions")
                        .param("studentId", String.valueOf(studentB.getId()))
                        .param("testId", String.valueOf(exam.getId())).session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(50))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 1)].studentAnswer", contains("3")))
                .andExpect(jsonPath("$.details[?(@.questionNumber == 2)].studentAnswer", contains("x=1")));
    }

    @Test
    void student_cannot_submit_for_another_student() throws Exception {
        mockMvc.perform(post("/api/submissions")
                        .param("studentId", String.valueOf(studentB.getId()))
                        .param("testId", String.valueOf(exam.getId()))
                        .session(studentSession(studentA))
                        .contentType("application/json")
                        .content(answersRequest(exam.getId(), "{\"1\":\"3\",\"2\":\"x=2\"}")))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacher_cannot_save_answers_for_student_outside_test_class() throws Exception {
        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}",
                        otherClassStudent.getId(), exam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(answersRequest(exam.getId(), "{\"1\":\"3\",\"2\":\"x=2\"}")))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacher_answer_update_preserves_essay_grade_only_when_answer_is_unchanged() throws Exception {
        com.example.entity.Test essayExam = com.example.entity.Test.builder()
                .title("서술형 시험").academy(academy).academyClass(clazz).build();
        em.persist(essayExam);
        TestQuestion essay = TestQuestion.builder()
                .test(essayExam).number(1).answer(null).points(100.0)
                .questionType(QuestionType.ESSAY).build();
        em.persist(essay);
        em.flush();

        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}",
                        studentA.getId(), essayExam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(answersRequest(essayExam.getId(), "{\"1\":\"풀이\"}")))
                .andExpect(status().isOk());

        StudentSubmission submission = submissionRepository
                .findByStudentIdAndTestId(studentA.getId(), essayExam.getId())
                .orElseThrow();
        Long detailId = submission.getDetails().get(0).getId();

        mockMvc.perform(put("/api/submissions/details/{detailId}/grade", detailId)
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"earnedPoints\":80,\"teacherComment\":\"좋아요\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}",
                        studentA.getId(), essayExam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(answersRequest(essayExam.getId(), "{\"1\":\"풀이\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(80))
                .andExpect(jsonPath("$.details[0].earnedPoints").value(80.0))
                .andExpect(jsonPath("$.details[0].teacherComment").value("좋아요"));

        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}",
                        studentA.getId(), essayExam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(answersRequest(essayExam.getId(), "{\"1\":\"다른 풀이\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(0))
                .andExpect(jsonPath("$.details[0].earnedPoints").value(nullValue()))
                .andExpect(jsonPath("$.details[0].teacherComment").value(nullValue()));
    }
}
