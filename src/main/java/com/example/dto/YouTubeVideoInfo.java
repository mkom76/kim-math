package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YouTubeVideoInfo {
    private String videoId;
    private String title;
    private String thumbnailUrl;
    private String duration;
}
