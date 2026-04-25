package com.example.dto;

import com.example.entity.HomeworkProblem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeworkProblemDto {
    private Long id;
    private Integer position;
    private TextbookProblemMetaDto textbookProblem; // null = 수동 슬롯

    public static HomeworkProblemDto from(HomeworkProblem entity) {
        return HomeworkProblemDto.builder()
                .id(entity.getId())
                .position(entity.getPosition())
                .textbookProblem(TextbookProblemMetaDto.fromOrNull(entity.getTextbookProblem()))
                .build();
    }
}
