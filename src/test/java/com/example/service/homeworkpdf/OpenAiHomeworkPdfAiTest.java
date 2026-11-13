package com.example.service.homeworkpdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class OpenAiHomeworkPdfAiTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Map<String,Object>> candidates = List.of(Map.of("id", "sample-type", "path", List.of("공통수학1", "다항식", "연산", "덧셈")));

    private OpenAiHomeworkPdfAi provider(ExchangeFunction exchange) {
        var environment = new MockEnvironment().withProperty("openai.api.key", "unit-test-only")
                .withProperty("app.homework-pdf.model", "unit-test-model");
        return new OpenAiHomeworkPdfAi(WebClient.builder().baseUrl("https://api.openai.com").exchangeFunction(exchange).build(), mapper, environment);
    }

    private ClientResponse response(String body) {
        return ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(body).build();
    }

    @Test void sends_private_pdf_and_selected_catalog_with_strict_output_contract() throws Exception {
        var sent = new AtomicReference<String>();
        String analysis = """
                {"complete":true,"questions":[{"number":"101","text":"synthetic problem","regions":[{"page":1,"x":0,"y":0,"width":1,"height":1}],"typeId":"sample-type","reason":"synthetic","needsReview":false}]}
                """;
        String envelope = mapper.writeValueAsString(Map.of("status", "completed", "output", List.of(
                Map.of("content", List.of(Map.of("type", "output_text", "text", analysis))))));
        var ai = provider(request -> {
            assertThat(request.url().toString()).isEqualTo("https://api.openai.com/v1/responses");
            var output = new MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(output, ExchangeStrategies.withDefaults())
                    .then(Mono.defer(output::getBodyAsString)).doOnNext(sent::set).map(ignored -> response(envelope));
        });
        var result = ai.analyze("%PDF-synthetic".getBytes(java.nio.charset.StandardCharsets.UTF_8), candidates);
        assertThat(result.questions().getFirst().number()).isEqualTo("101");
        var body = mapper.readTree(sent.get());
        assertThat(body.path("store").asBoolean()).isFalse();
        assertThat(body.path("model").asText()).isEqualTo("unit-test-model");
        assertThat(body.has("tools")).isFalse();
        var content = body.path("input").get(0).path("content");
        assertThat(content.get(0).path("file_data").asText()).startsWith("data:application/pdf;base64,");
        assertThat(content.get(0).path("filename").asText()).isEqualTo("homework.pdf");
        assertThat(content.get(1).path("text").asText()).contains("sample-type", "공통수학1");
        assertThat(body.at("/text/format/strict").asBoolean()).isTrue();
        assertThat(body.at("/text/format/schema/additionalProperties").asBoolean()).isFalse();
    }

    @Test void refusal_and_incomplete_results_are_never_accepted_as_questions() {
        for (String body : List.of("{\"status\":\"incomplete\",\"output\":[]}",
                "{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"refusal\",\"refusal\":\"private-upstream-message\"}]}]}")) {
            var ai = provider(request -> Mono.just(response(body)));
            assertThatThrownBy(() -> ai.analyze(new byte[]{1}, candidates)).isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining("private-upstream-message");
        }
    }

    @Test void upstream_failure_is_sanitized_and_is_not_retried() {
        var calls = new AtomicInteger();
        var ai = provider(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).body("private-upstream-message").build());
        });
        assertThatThrownBy(() -> ai.analyze(new byte[]{1}, candidates)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("private-upstream-message");
        assertThat(calls).hasValue(1);
    }

    @Test void unapproved_api_origin_is_disabled_before_sending_a_pdf() {
        var calls = new AtomicInteger();
        var webClient = WebClient.builder().exchangeFunction(request -> { calls.incrementAndGet(); return Mono.empty(); }).build();
        var env = new MockEnvironment().withProperty("openai.api.key", "unit-test-only").withProperty("openai.api.base-url", "https://example.invalid");
        var ai = new OpenAiHomeworkPdfAi(webClient, mapper, env);
        assertThat(ai.ready()).isFalse();
        assertThatThrownBy(() -> ai.analyze(new byte[]{1}, candidates)).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(0);
    }
}
