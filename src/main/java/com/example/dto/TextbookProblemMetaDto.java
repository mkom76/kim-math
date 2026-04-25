package com.example.dto;

import com.example.entity.TextbookProblem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 시험/숙제 응답에 인라인으로 포함되는 라이브 메타데이터.
 * 본 DTO는 권한 체크 없이 전수 — 부모 시험/숙제의 권한이 이미 확보된 상태에서 노출됨.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TextbookProblemMetaDto {
    private Long id;
    private Long textbookId;
    private Integer number;
    private String topic;
    private String videoLink;

    public static TextbookProblemMetaDto fromOrNull(TextbookProblem entity) {
        if (entity == null) return null;
        return TextbookProblemMetaDto.builder()
                .id(entity.getId())
                .textbookId(entity.getTextbook() != null ? entity.getTextbook().getId() : null)
                .number(entity.getNumber())
                .topic(entity.getTopic())
                .videoLink(entity.getVideoLink())
                .build();
    }
}
