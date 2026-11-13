package com.example.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exam_pdf_questions", uniqueConstraints = @UniqueConstraint(name = "uk_exam_pdf_question_position", columnNames = {"import_id", "position"}))
@Getter @Setter @NoArgsConstructor
public class ExamPdfQuestion {
    @Id @Column(length = 36) private String id;
    @Column(name="import_id",nullable = false, length = 36) private String importId;
    @Column(name="test_question_id",unique = true) private Long testQuestionId;
    @Column(nullable = false) private Integer position;
    @Column(nullable = false, length = 40) private String sourceNumber;
    @Column(nullable = false, columnDefinition = "TEXT") private String regionsJson;
    @Column(nullable = false, columnDefinition = "LONGTEXT") private String extractedText;
    @Column(length = 64) private String suggestedTypeId;
    @Column(length = 64) private String confirmedTypeId;
    @Column(columnDefinition = "TEXT") private String typePathSnapshot;
    @Column(nullable = false, length = 24) private String reviewState;
    @Column(nullable = false, length = 500) private String classificationReason;
    @Column(columnDefinition="TEXT") private String answer;
    private Double points;
    @Column(length=24) private String format;
    private Boolean multipleAnswers;
    @Version private Long version;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="import_id",insertable=false,updatable=false,foreignKey=@ForeignKey(name="fk_exam_pdf_question_import"))
    private ExamPdfImport importReference;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="test_question_id",insertable=false,updatable=false,foreignKey=@ForeignKey(name="fk_exam_pdf_question_slot"))
    private TestQuestion problemReference;
}
