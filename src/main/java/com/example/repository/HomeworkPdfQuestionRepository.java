package com.example.repository;

import com.example.entity.HomeworkPdfQuestion;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface HomeworkPdfQuestionRepository extends JpaRepository<HomeworkPdfQuestion, String> {
    List<HomeworkPdfQuestion> findByImportIdOrderByPositionAsc(String importId);
    void deleteByImportId(String importId);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from HomeworkPdfQuestion q where q.importId = :id")
    void deleteDraftRows(@Param("id") String id);
}
