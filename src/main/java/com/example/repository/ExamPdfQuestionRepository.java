package com.example.repository;

import com.example.entity.ExamPdfQuestion;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ExamPdfQuestionRepository extends JpaRepository<ExamPdfQuestion, String> {
    List<ExamPdfQuestion> findByImportIdOrderByPositionAsc(String importId);
    void deleteByImportId(String importId);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ExamPdfQuestion q where q.importId = :id")
    void deleteDraftRows(@Param("id") String id);
}
