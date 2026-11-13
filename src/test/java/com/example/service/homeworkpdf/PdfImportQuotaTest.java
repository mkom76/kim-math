package com.example.service.homeworkpdf;
import com.example.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class PdfImportQuotaTest {
    @Test void homework_and_exam_usage_share_teacher_global_and_concurrent_limits() {
        var homework=mock(HomeworkPdfImportRepository.class);var exam=mock(ExamPdfImportRepository.class);
        var quota=new PdfImportQuota(homework,exam);var now=LocalDateTime.now();
        when(homework.countByTeacherIdAndCreatedAtAfter(eq(1L),any())).thenReturn(2L);
        when(exam.countByTeacherIdAndCreatedAtAfter(eq(1L),any())).thenReturn(1L);
        assertThatThrownBy(()->quota.check(1L,3,20,now)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        reset(homework,exam);when(homework.countByCreatedAtAfter(any())).thenReturn(19L);when(exam.countByCreatedAtAfter(any())).thenReturn(1L);
        assertThatThrownBy(()->quota.check(1L,3,20,now)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        reset(homework,exam);when(homework.countByState("PROCESSING")).thenReturn(1L);when(exam.countByState("PROCESSING")).thenReturn(1L);
        assertThatThrownBy(()->quota.check(1L,3,20,now)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
