package com.example.service.ai;

import com.example.dto.DailyFeedbackDto;
import com.example.entity.Lesson;
import com.example.entity.StudentSubmission;
import com.example.repository.StudentSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiScoreHistoryService {

    private final StudentSubmissionRepository submissionRepository;

    // The caller authorizes the student and lesson before requesting their history.
    public String buildSummary(Long studentId, Lesson lesson,
                               DailyFeedbackDto.TestFeedback currentTest, int historyCount) {
        if (historyCount == 0 || currentTest == null) {
            return "";
        }
        if (currentTest.getStudentScore() == null || (currentTest.getEssayDetails() != null
                && currentTest.getEssayDetails().stream().anyMatch(essay -> essay.getEarnedPoints() == null))) {
            return "\n## 성적 추이\n- 이번 시험은 채점이 완료되지 않아 성적 비교에서 제외합니다.\n";
        }

        List<StudentSubmission> previous = submissionRepository.findRecentGradedBeforeDate(
                studentId, lesson.getAcademy().getId(), lesson.getAcademyClass().getId(),
                lesson.getLessonDate(), PageRequest.of(0, historyCount));
        if (previous.isEmpty()) {
            return "\n## 성적 추이\n- 비교 가능한 이전 시험 기록이 없어 성적 추이를 판단할 수 없습니다.\n";
        }

        List<Long> testIds = previous.stream().map(sub -> sub.getTest().getId()).toList();
        Map<Long, Double> averages = submissionRepository.getAverageScoresByTestIds(testIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Double) row[1]));

        StringBuilder sb = new StringBuilder("\n## 성적 추이 (같은 반의 채점 완료된 이전 시험 "
                + previous.size() + "회 + 이번 시험, 날짜 오름차순)\n");
        for (StudentSubmission submission : previous.reversed()) {
            appendScore(sb, submission.getTest().getLesson().getLessonDate().toString(),
                    submission.getTest().getTitle(), submission.getTotalScore(),
                    averages.get(submission.getTest().getId()));
        }
        appendScore(sb, lesson.getLessonDate() + " (이번)", currentTest.getTestTitle(),
                currentTest.getStudentScore(), currentTest.getClassAverage());

        StudentSubmission last = previous.getFirst();
        int scoreChange = currentTest.getStudentScore() - last.getTotalScore();
        sb.append(String.format(Locale.ROOT, "- 직전 반영 시험 대비 점수 증감: %+d점\n", scoreChange));
        Double previousAverage = averages.get(last.getTest().getId());
        if (previousAverage != null && currentTest.getClassAverage() != null) {
            double gapChange = (currentTest.getStudentScore() - currentTest.getClassAverage())
                    - (last.getTotalScore() - previousAverage);
            sb.append(String.format(Locale.ROOT, "- 직전 반영 시험 대비 반 평균 격차 증감: %+.1f점\n", gapChange));
        }
        return sb.toString();
    }

    private void appendScore(StringBuilder sb, String date, String title, int score, Double average) {
        sb.append("- ").append(date).append(" / ").append(title).append(" / 점수: ").append(score).append("점");
        if (average != null) {
            sb.append(String.format(Locale.ROOT, " / 반 평균: %.1f점 / 평균 대비: %+.1f점", average, score - average));
        }
        sb.append("\n");
    }
}
