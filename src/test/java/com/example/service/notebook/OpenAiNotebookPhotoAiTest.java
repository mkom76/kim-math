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
            int i=bodies.size();String content=i==1?"{\"readable\":true,\"question\":\"1+1?\",\"solution\":{\"title\":\"sum\",\"steps\":[\"1+1=2\"],\"answer\":\"2\",\"diagram\":\"\"}}":"{\"passed\":true}";
            String response=i==3?mapper.writeValueAsString(Map.of("data",List.of(Map.of("b64_json",Base64.getEncoder().encodeToString(png))),"usage",Map.of("input_tokens",100,"output_tokens",200))):mapper.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",content))),"usage",Map.of("prompt_tokens",50,"completion_tokens",60)));
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var provider=new OpenAiNotebookPhotoAi(WebClient.builder().baseUrl("http://127.0.0.1:"+server.getAddress().getPort()).defaultHeader("Authorization","Bearer test-key").build(),mapper,true,List.of(png,png,png));
            var draft=provider.solve(NotebookPhotoCodec.photo(png,0));assertThat(provider.verifyMath(NotebookPhotoCodec.photo(png,0),draft)).isTrue();byte[] image=provider.render(draft);assertThat(provider.verifyImage(image,draft)).isTrue();
            assertThat(paths).containsExactly("/v1/chat/completions","/v1/chat/completions","/v1/images/edits","/v1/chat/completions");
            for(int i:List.of(0,1,3)){var json=mapper.readTree(bodies.get(i));assertThat(json.path("model").asText()).isEqualTo("gpt-6-luna");assertThat(json.path("store").asBoolean()).isFalse();assertThat(json.path("max_completion_tokens").asInt()).isLessThanOrEqualTo(6000);assertThat(bodies.get(i)).doesNotContain("studentId","sessionId","fileName");}
            assertThat(types.get(2)).startsWith("multipart/form-data");assertThat(bodies.get(2)).contains("gpt-image-2","1024x1536","medium","reference-1.jpg","reference-2.jpg","reference-3.jpg").doesNotContain("file_id","http://","https://");
            assertThat(provider.usage()).hasSize(4);assertThat(provider.usage().get(2).outputTokens()).isEqualTo(200);
        } finally {server.stop(0);}
    }
    @Test void providerErrorsDoNotRetryOrExposeResponseBody() throws Exception {
        int[] count={0};var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{count[0]++;byte[] bytes="PRIVATE upstream content".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(429,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try {byte[] png=NotebookPhotoTrialServiceTest.photo();var provider=new OpenAiNotebookPhotoAi(WebClient.create("http://127.0.0.1:"+server.getAddress().getPort()),new ObjectMapper(),true,List.of(png,png,png));assertThatThrownBy(()->provider.solve(png)).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("PRIVATE");assertThat(count[0]).isEqualTo(1);assertThat(provider.failures()).containsExactly(new OpenAiNotebookPhotoAi.Failure("solve",429,"upstream_error"));}finally{server.stop(0);}
    }
}
