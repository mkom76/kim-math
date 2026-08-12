package com.example.service.ai;

import com.example.dto.DailyFeedbackDto;
import com.example.entity.Student;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiFeedbackServiceTest {

    private final AiFeedbackService service = new AiFeedbackService(
            null, null, null, null, null, null, null, null);

    @Test
    void includesTopicAndResultForEveryQuestionInAiInput() {
        DailyFeedbackDto.TestFeedback test = DailyFeedbackDto.TestFeedback.builder()
                .testTitle("중간고사")
                .studentScore(80)
                .classAverage(70.0)
                .rank(3)
                .incorrectQuestions(List.of(2))
                .questionAccuracyRates(List.of(
                        DailyFeedbackDto.QuestionAccuracy.builder()
                                .questionNumber(1).correctRate(90.0).topic("함수 › 일차함수").build(),
                        DailyFeedbackDto.QuestionAccuracy.builder()
                                .questionNumber(2).correctRate(40.0).topic("도형 › 닮음").build(),
                        DailyFeedbackDto.QuestionAccuracy.builder()
                                .questionNumber(3).correctRate(60.0).topic(null).build()))
                .essayDetails(List.of(
                        DailyFeedbackDto.EssayDetail.builder()
                                .questionNumber(4).topic("확률과 통계").maxPoints(10.0)
                                .earnedPoints(6.0).teacherComment("풀이 과정 보완").build()))
                .build();
        DailyFeedbackDto feedback = DailyFeedbackDto.builder().todayTest(test).build();
        Student student = Student.builder().name("김학생").build();

        String input = service.buildCurrentStudentData(feedback, student);

        assertThat(input)
                .contains("1번: 정답 / 유형: 함수 › 일차함수 / 반 정답률: 90%")
                .contains("2번: 오답 / 유형: 도형 › 닮음 / 반 정답률: 40%")
                .contains("3번: 정답 / 유형: 유형 미지정 / 반 정답률: 60%")
                .contains("4번 / 유형: 확률과 통계: 6.0/10.0점 (코멘트: 풀이 과정 보완)");
    }
}
