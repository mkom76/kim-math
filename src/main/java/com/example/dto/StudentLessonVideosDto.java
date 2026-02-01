package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentLessonVideosDto {
    private Long lessonId;
    private String lessonDate;
    private String className;
    private List<LessonVideoDto> videos;
}
