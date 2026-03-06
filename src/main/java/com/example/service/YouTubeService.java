package com.example.service;

import com.example.dto.YouTubeVideoInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class YouTubeService {
    @Value("${youtube.api.key:}")
    private String apiKey;

    private static final String API_URL = "https://www.googleapis.com/youtube/v3/videos";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * YouTube URL에서 video ID 추출
     */
    public String extractVideoId(String youtubeUrl) {
        String videoId = null;

        // Pattern 1: youtube.com/watch?v=VIDEO_ID
        Pattern pattern1 = Pattern.compile("(?:youtube\\.com/watch\\?v=)([\\w-]+)");
        Matcher matcher1 = pattern1.matcher(youtubeUrl);
        if (matcher1.find()) {
            videoId = matcher1.group(1);
        }

        // Pattern 2: youtu.be/VIDEO_ID
        if (videoId == null) {
            Pattern pattern2 = Pattern.compile("(?:youtu\\.be/)([\\w-]+)");
            Matcher matcher2 = pattern2.matcher(youtubeUrl);
            if (matcher2.find()) {
                videoId = matcher2.group(1);
            }
        }

        // Pattern 3: youtube.com/embed/VIDEO_ID
        if (videoId == null) {
            Pattern pattern3 = Pattern.compile("(?:youtube\\.com/embed/)([\\w-]+)");
            Matcher matcher3 = pattern3.matcher(youtubeUrl);
            if (matcher3.find()) {
                videoId = matcher3.group(1);
            }
        }

        // Pattern 4: youtube.com/live/VIDEO_ID
        if (videoId == null) {
            Pattern pattern4 = Pattern.compile("(?:youtube\\.com/live/)([\\w-]+)");
            Matcher matcher4 = pattern4.matcher(youtubeUrl);
            if (matcher4.find()) {
                videoId = matcher4.group(1);
            }
        }

        if (videoId == null) {
            throw new IllegalArgumentException("유효하지 않은 YouTube URL입니다");
        }

        return videoId;
    }

    /**
     * YouTube Data API v3로 영상 정보 가져오기
     */
    public YouTubeVideoInfo fetchVideoInfo(String videoId) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("YouTube API 키가 설정되지 않았습니다");
        }

        String url = API_URL + "?id=" + videoId
                + "&key=" + apiKey
                + "&part=snippet,contentDetails";

        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.get("items");

            if (items == null || items.size() == 0) {
                throw new RuntimeException("YouTube 영상을 찾을 수 없습니다");
            }

            JsonNode item = items.get(0);
            JsonNode snippet = item.get("snippet");
            JsonNode contentDetails = item.get("contentDetails");
            JsonNode thumbnails = snippet.get("thumbnails");

            // 썸네일: high > medium > default 순서로 선택
            String thumbnailUrl = null;
            if (thumbnails.has("high")) {
                thumbnailUrl = thumbnails.get("high").get("url").asText();
            } else if (thumbnails.has("medium")) {
                thumbnailUrl = thumbnails.get("medium").get("url").asText();
            } else if (thumbnails.has("default")) {
                thumbnailUrl = thumbnails.get("default").get("url").asText();
            }

            return YouTubeVideoInfo.builder()
                    .videoId(videoId)
                    .title(snippet.get("title").asText())
                    .thumbnailUrl(thumbnailUrl)
                    .duration(contentDetails.get("duration").asText())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("YouTube API 호출 실패: " + e.getMessage(), e);
        }
    }
}
