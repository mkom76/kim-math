package com.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 반-조교 매핑. (class_id, teacher_id) 복합키.
 *
 * <p>조교(ASSISTANT 역할)는 owner_teacher_id 와 별개로 이 테이블에 등록된 반에만 접근.
 * 권한 부여는 Hibernate {@code ownerFilter} 의 UNION 절과 {@code AuthorizationService}
 * 양쪽에서 처리.
 */
@Entity
@Table(name = "class_assistants")
@EntityListeners(AuditingEntityListener.class)
@IdClass(ClassAssistant.PK.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassAssistant {

    @Id
    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Id
    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private Long classId;
        private Long teacherId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(classId, pk.classId) && Objects.equals(teacherId, pk.teacherId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(classId, teacherId);
        }
    }
}
