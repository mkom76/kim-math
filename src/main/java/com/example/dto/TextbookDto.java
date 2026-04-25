package com.example.dto;

import com.example.entity.Textbook;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TextbookDto {
    private Long id;
    private Long ownerTeacherId;
    private String title;
    private Integer problemCount;
    private List<TextbookProblemDto> problems;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TextbookDto summary(Textbook entity) {
        return TextbookDto.builder()
                .id(entity.getId())
                .ownerTeacherId(entity.getOwnerTeacherId())
                .title(entity.getTitle())
                .problemCount(entity.getProblems() != null ? entity.getProblems().size() : 0)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static TextbookDto withProblems(Textbook entity) {
        TextbookDto dto = summary(entity);
        if (entity.getProblems() != null) {
            dto.setProblems(entity.getProblems().stream()
                    .sorted((a, b) -> Integer.compare(a.getNumber(), b.getNumber()))
                    .map(TextbookProblemDto::from)
                    .collect(Collectors.toList()));
        }
        return dto;
    }
}
