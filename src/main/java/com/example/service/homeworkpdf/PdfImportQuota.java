package com.example.service.homeworkpdf;
import com.example.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.function.Supplier;

@Component @RequiredArgsConstructor
public class PdfImportQuota {
    private final HomeworkPdfImportRepository homeworks;
    private final ExamPdfImportRepository exams;
    public synchronized <T>T start(Supplier<T> operation){return operation.get();}
    public void check(Long teacher,int daily,int global,LocalDateTime now){
        var since=now.toLocalDate().atStartOfDay();
        if(homeworks.countByTeacherIdAndCreatedAtAfter(teacher,since)+exams.countByTeacherIdAndCreatedAtAfter(teacher,since)>=daily
                ||homeworks.countByCreatedAtAfter(since)+exams.countByCreatedAtAfter(since)>=global)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"오늘의 PDF 분석 한도를 사용했습니다. 숙제와 시험의 한도를 함께 계산합니다.");
        if(homeworks.countByState("PROCESSING")+exams.countByState("PROCESSING")>=2)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"다른 PDF를 분석 중입니다. 잠시 후 시작해 주세요.");
    }
}
