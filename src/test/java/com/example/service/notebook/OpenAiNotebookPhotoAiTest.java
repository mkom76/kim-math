package com.example.service.notebook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OpenAiNotebookPhotoAiTest {
    @Test void serializedRequestsUseOnlyApprovedModelsFixedImageSettingsAndSelectedReferenceBytes() throws Exception {
        var mapper=new ObjectMapper();var bodies=new ArrayList<String>();var paths=new ArrayList<String>();var types=new ArrayList<String>();
        byte[] png=NotebookPhotoTrialServiceTest.photo();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            paths.add(exchange.getRequestURI().getPath());types.add(exchange.getRequestHeaders().getFirst("Content-Type"));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.ISO_8859_1));
            int i=bodies.size();String content=i==1?"{\"readable\":true,\"question\":\"1+1?\",\"solution\":{\"title\":\"sum\",\"steps\":[\"1+1=2\"],\"answer\":\"2\",\"diagram\":\"\"}}":"{\"verdict\":\"PASSED\",\"issues\":[]}";
            String response=i==3?mapper.writeValueAsString(Map.of("data",List.of(Map.of("b64_json",Base64.getEncoder().encodeToString(png))),"usage",Map.of("input_tokens",100,"output_tokens",200))):mapper.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",content))),"usage",Map.of("prompt_tokens",50,"completion_tokens",60)));
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var provider=new OpenAiNotebookPhotoAi(WebClient.builder().baseUrl("http://127.0.0.1:"+server.getAddress().getPort()).defaultHeader("Authorization","Bearer test-key").build(),mapper,true,List.of(png,png,png));
            var draft=provider.solve(NotebookPhotoCodec.photo(png,0));assertThat(provider.verifyMath(NotebookPhotoCodec.photo(png,0),draft)).isTrue();byte[] image=provider.render(draft);assertThat(provider.verifyImage(image,draft)).isTrue();
            assertThat(paths).containsExactly("/v1/chat/completions","/v1/chat/completions","/v1/images/edits","/v1/chat/completions");
            for(int i:List.of(0,1,3)){var json=mapper.readTree(bodies.get(i));assertThat(json.path("model").asText()).isEqualTo("gpt-6-luna");assertThat(json.path("store").asBoolean()).isFalse();assertThat(json.path("max_completion_tokens").asInt()).isLessThanOrEqualTo(6000);assertThat(bodies.get(i)).doesNotContain("studentId","sessionId","fileName");}
            assertThat(types.get(2)).startsWith("multipart/form-data");assertThat(bodies.get(2)).contains("gpt-image-2","1024x1536","medium","reference-1.jpg","reference-2.jpg").doesNotContain("reference-3.jpg","file_id","http://","https://");
            assertThat(provider.usage()).hasSize(4);assertThat(provider.usage().get(2).outputTokens()).isEqualTo(200);
        } finally {server.stop(0);}
    }
    @Test void detailedVerificationDistinguishesMismatchUncertaintyMalformedAndUnavailableWithoutRetries() throws Exception {
        var mapper = new ObjectMapper(); int[] calls = {0};
        var response = new java.util.concurrent.atomic.AtomicReference<>("{\"verdict\":\"MISMATCH\",\"issues\":[{\"category\":\"SIGN\",\"location\":\"두 번째 줄\",\"expected\":\"-3\",\"observed\":\"3\",\"reason\":\"음수 부호가 없습니다.\"}]}");
        var status = new java.util.concurrent.atomic.AtomicInteger(200);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            calls[0]++;
            var request = mapper.readTree(exchange.getRequestBody().readAllBytes());
            assertThat(request.path("max_completion_tokens").asInt()).isEqualTo(1800);
            assertThat(request.toString()).contains("MISMATCH", "UNCERTAIN", "observed");
            String body = status.get() == 200 ? mapper.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",response.get()))))) : "PRIVATE upstream response";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(status.get(),bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });server.start();
        try {
            byte[] png = NotebookPhotoTrialServiceTest.photo();
            var provider = new OpenAiNotebookPhotoAi(WebClient.create("http://127.0.0.1:"+server.getAddress().getPort()),mapper,true,List.of(png,png));
            var draft = new NotebookPhotoAi.Draft("가상 문제",new NotebookTypes.Solution("제목",List.of("y=-3"),"-3",""));
            var report = provider.verifyImageReport(png,draft);
            assertThat(report.stage()).isEqualTo(NotebookVerification.Stage.IMAGE);
            assertThat(report.verdict()).isEqualTo(NotebookVerification.Verdict.MISMATCH);
            assertThat(report.issues().getFirst().reason()).isEqualTo("음수 부호가 없습니다.");
            response.set("{\"verdict\":\"UNCERTAIN\",\"issues\":[{\"category\":\"UNREADABLE\",\"location\":\"지수\",\"expected\":\"x²\",\"observed\":\"\",\"reason\":\"겹쳐 보입니다.\"}]}");
            assertThat(provider.verifyMathReport(png,draft).verdict()).isEqualTo(NotebookVerification.Verdict.UNCERTAIN);
            response.set("{\"passed\":true}");
            assertThat(provider.verifyImageReport(png,draft).verdict()).isEqualTo(NotebookVerification.Verdict.INVALID_RESPONSE);
            response.set("not-json PRIVATE");
            assertThat(provider.verifyImageReport(png,draft).verdict()).isEqualTo(NotebookVerification.Verdict.INVALID_RESPONSE);
            status.set(503);
            var unavailable = provider.verifyImageReport(png,draft);
            assertThat(unavailable.verdict()).isEqualTo(NotebookVerification.Verdict.UNAVAILABLE);
            assertThat(mapper.writeValueAsString(unavailable)).doesNotContain("PRIVATE");
            assertThat(calls[0]).isEqualTo(5);
        } finally {server.stop(0);}
    }
    @Test void providerErrorsDoNotRetryOrExposeResponseBody() throws Exception {
        int[] count={0};var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{count[0]++;byte[] bytes="{\"error\":{\"code\":\"credit_balance_exhausted\",\"message\":\"PRIVATE credit balance exhausted\",\"param\":null}}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(429,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try {byte[] png=NotebookPhotoTrialServiceTest.photo();var provider=new OpenAiNotebookPhotoAi(WebClient.create("http://127.0.0.1:"+server.getAddress().getPort()),new ObjectMapper(),true,List.of(png,png,png));assertThatThrownBy(()->provider.solve(png)).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("PRIVATE");assertThat(count[0]).isEqualTo(1);assertThat(provider.failures()).containsExactly(new OpenAiNotebookPhotoAi.Failure("solve",429,"credit_balance_exhausted","","billing_or_quota"));}finally{server.stop(0);}
    }
}
