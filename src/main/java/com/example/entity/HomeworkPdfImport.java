package com.example.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "homework_pdf_imports", uniqueConstraints = @UniqueConstraint(name = "uk_pdf_import_request", columnNames = {"teacher_id", "request_key"}))
@Getter @Setter @NoArgsConstructor
public class HomeworkPdfImport {
    @Id @Column(length = 36) private String id;
    @Column(name="academy_id",nullable = false) private Long academyId;
    @Column(name="class_id",nullable = false) private Long classId;
    @Column(name="teacher_id",nullable = false) private Long teacherId;
    @Column(name="homework_id",unique = true) private Long homeworkId;
    @Column(nullable = false, length = 20) private String state;
    @Column(nullable = false, length = 36) private String storageKey;
    @Column(nullable = false, length = 255) private String originalFileName;
    @Column(nullable = false, length = 64) private String fileSha256;
    @Column(nullable = false) private Long fileSize;
    @Column(nullable = false) private Integer pageCount;
    @Column(nullable = false, length = 32) private String subjectId;
    @Column(nullable = false, length = 64) private String catalogVersion;
    @Column(nullable = false, length = 80) private String aiModel;
    @Column(nullable = false, length = 36) private String requestKey;
    @Column(length = 64) private String registrationHash;
    @Column(length = 40) private String errorCode;
    @Column(nullable = false) private Boolean reviewRequired = false;
    @Column(nullable = false) private LocalDateTime expiresAt;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    @Version private Long version;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="academy_id",insertable=false,updatable=false,foreignKey=@ForeignKey(name="fk_pdf_import_academy"))
    private Academy academyReference;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="class_id",insertable=false,updatable=false,foreignKey=@ForeignKey(name="fk_pdf_import_class"))
    private AcademyClass classReference;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="teacher_id",insertable=false,updatable=false,foreignKey=@ForeignKey(name="fk_pdf_import_teacher"))
    private Teacher teacherReference;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="homework_id",insertable=false,updatable=false,foreignKey=@ForeignKey(name="fk_pdf_import_homework"))
    private Homework homeworkReference;
}
