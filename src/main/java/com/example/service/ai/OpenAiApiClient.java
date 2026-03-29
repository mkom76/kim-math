package com.example.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiApiClient {

    private final WebClient openAiWebClient;

    @Value("${openai.api.model:gpt-4o}")
    private String model;

    @Value("${openai.api.max-tokens:1024}")
    private int maxTokens;

    /**
     * OpenAI Chat Completions API 호출
     * @param systemPrompt 시스템 프롬프트
     * @param messages 대화 메시지 목록 (role: "user" | "assistant", content: text)
     * @return 생성된 텍스트
     */
    public String sendMessage(String systemPrompt, List<Map<String, String>> messages) {
        // system 메시지를 맨 앞에 추가
        List<Map<String, String>> allMessages = new java.util.ArrayList<>();
        allMessages.add(Map.of("role", "system", "content", systemPrompt));
        allMessages.addAll(messages);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_completion_tokens", maxTokens,
                "messages", allMessages
        );

        Map<String, Object> response;
        try {
            response = openAiWebClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
        } catch (WebClientResponseException e) {
            log.error("OpenAI API error! - status: {}, body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("OpenAI API error: " + e.getResponseBodyAsString(), e);
        }

        if (response == null || !response.containsKey("choices")) {
            throw new RuntimeException("OpenAI API returned empty response");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices.isEmpty()) {
            throw new RuntimeException("No choices in OpenAI API response");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }
}
