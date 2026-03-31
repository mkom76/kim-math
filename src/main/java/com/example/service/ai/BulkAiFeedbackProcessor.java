package com.example.service.ai;

import com.example.dto.AiFeedbackRequest;
import com.example.dto.AiFeedbackResponse;
import com.example.dto.BulkAiFeedbackRequest;
import com.example.dto.BulkAiFeedbackResponse;
import com.example.dto.DailyFeedbackDto;
import com.example.entity.AttendanceStatus;
import com.example.service.DailyFeedbackService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class BulkAiFeedbackProcessor {

    private final AiFeedbackService aiFeedbackService;
    private final DailyFeedbackService dailyFeedbackService;

    public BulkAiFeedbackProcessor(@Lazy AiFeedbackService aiFeedbackService, DailyFeedbackService dailyFeedbackService) {
        this.aiFeedbackService = aiFeedbackService;
        this.dailyFeedbackService = dailyFeedbackService;
    }

    private static final Set<AttendanceStatus> SKIP_STATUSES = Set.of(
            AttendanceStatus.ABSENT, AttendanceStatus.VIDEO);

    @Data
    @AllArgsConstructor
    public static class StudentSnapshot {
        private Long studentId;
        private String studentName;
        private AttendanceStatus attendanceStatus;
        private String instructorFeedback;
    }

    @Async
    public void processAsync(BulkAiFeedbackRequest request, List<StudentSnapshot> students, BulkAiFeedbackResponse status) {
        Long lessonId = request.getLessonId();

        List<String> skippedStudents = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        int processedCount = 0;

        for (StudentSnapshot student : students) {
            String studentName = student.getStudentName();
            Long studentId = student.getStudentId();

            // 결석/인강 스킵
            if (student.getAttendanceStatus() != null && SKIP_STATUSES.contains(student.getAttendanceStatus())) {
                String reason = student.getAttendanceStatus() == AttendanceStatus.ABSENT ? "결석" : "인강";

                skippedStudents.add(studentName + " (" + reason + ")");
                processedCount++;
                updateStatus(status, processedCount, successCount, failCount, skippedStudents);
                continue;
            }

            // 이미 피드백이 있으면 스킵
            if (student.getInstructorFeedback() != null && !student.getInstructorFeedback().isBlank()) {

                skippedStudents.add(studentName + " (피드백 기작성)");
                processedCount++;
                updateStatus(status, processedCount, successCount, failCount, skippedStudents);
                continue;
            }

            try {
                // 시험/숙제 데이터 없으면 스킵
                DailyFeedbackDto feedbackData = dailyFeedbackService.getDailyFeedback(studentId, lessonId);
                if (feedbackData.getTodayTest() == null && feedbackData.getTodayHomework() == null) {

                    skippedStudents.add(studentName + " (시험/숙제 없음)");
                    processedCount++;
                    updateStatus(status, processedCount, successCount, failCount, skippedStudents);
                    continue;
                }


                AiFeedbackRequest individualRequest = new AiFeedbackRequest();
                individualRequest.setStudentId(studentId);
                individualRequest.setLessonId(lessonId);
                individualRequest.setTeacherId(request.getTeacherId());
                individualRequest.setModel(request.getModel());

                AiFeedbackResponse response = aiFeedbackService.generateFeedback(individualRequest);

                dailyFeedbackService.updateInstructorFeedback(
                        studentId, lessonId,
                        response.getGeneratedFeedback(), "AI", true);

                successCount++;
            } catch (Exception e) {
                log.error("[Bulk AI] 생성 실패 - {} (studentId: {}): {}", studentName, studentId, e.getMessage(), e);
                failCount++;
            }

            processedCount++;
            updateStatus(status, processedCount, successCount, failCount, skippedStudents);
        }

        status.setStatus("COMPLETED");
    }

    private void updateStatus(BulkAiFeedbackResponse status, int processed, int success, int fail, List<String> skipped) {
        status.setProcessedCount(processed);
        status.setSuccessCount(success);
        status.setFailCount(fail);
        status.setSkippedCount(skipped.size());
        status.setSkippedStudents(new ArrayList<>(skipped));
    }
}
