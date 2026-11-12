package com.example.service.notebook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static com.example.service.notebook.NotebookVerification.*;
import static org.assertj.core.api.Assertions.*;

class NotebookVerificationTest {
    final ObjectMapper mapper = new ObjectMapper();
    Report parse(String json) throws Exception { return NotebookVerification.parse(Stage.IMAGE, mapper.readTree(json)); }
    @Test void onlyExplicitPassWithoutIssuesCanPass() throws Exception {
        assertThat(parse("{\"verdict\":\"PASSED\",\"issues\":[]}").passed()).isTrue();
        for (String json : new String[]{"{}", "null", "{\"passed\":true}", "{\"verdict\":true,\"issues\":[]}",
                "{\"verdict\":\"UNKNOWN\",\"issues\":[]}", "{\"verdict\":\"MISMATCH\",\"issues\":[]}",
                "{\"verdict\":\"UNAVAILABLE\",\"issues\":[]}", "{\"verdict\":\"PASSED\",\"issues\":{}}"}) {
            assertThat(parse(json).verdict()).as(json).isEqualTo(Verdict.INVALID_RESPONSE);
            assertThat(parse(json).passed()).isFalse();
        }
    }
    @Test void retainsSpecificSignFractionRootAndAnswerComparisonsWithoutInterpretingMath() throws Exception {
        String[][] examples = {{"SIGN","y=-3","y=3"},{"FRACTION","2/(x-2)","2/x-2"},
                {"EXPONENT_ROOT","sqrt(x+1)","sqrt(x)+1"},{"ANSWER","④ 제1·3·4사분면","③ 제1·2·4사분면"},
                {"OMISSION","x≠2",""},{"CONDITION","x>0","x≥0"},{"DIAGRAM","그래프 없음","좌표축 있음"}};
        for (String[] e : examples) {
            String json = mapper.writeValueAsString(java.util.Map.of("verdict","MISMATCH","issues",java.util.List.of(
                    java.util.Map.of("category",e[0],"location","풀이 두 번째 줄","expected",e[1],"observed",e[2],"reason","기준과 다릅니다."))));
            Report report = parse(json);
            assertThat(report.passed()).isFalse();
            assertThat(report.verdict()).isEqualTo(Verdict.MISMATCH);
            assertThat(report.issues().getFirst().expected()).isEqualTo(e[1]);
            assertThat(report.issues().getFirst().observed()).isEqualTo(e[2]);
        }
    }
    @Test void uncertaintyDoesNotInventAnObservedExpression() throws Exception {
        var report = parse("{\"verdict\":\"UNCERTAIN\",\"issues\":[{\"category\":\"UNREADABLE\",\"location\":\"첫 줄 지수\",\"expected\":\"x²\",\"observed\":\"\",\"reason\":\"지수가 겹쳐 읽기 어렵습니다.\"}]}");
        assertThat(report.verdict()).isEqualTo(Verdict.UNCERTAIN);
        assertThat(report.passed()).isFalse();
        assertThat(report.issues().getFirst().observed()).isEmpty();
    }
    @Test void contradictoryOversizedAndIncompleteIssuesAreRejected() throws Exception {
        var good = java.util.Map.of("category","SIGN","location","두 번째 줄","expected","-3","observed","3","reason","부호가 다릅니다.");
        assertThat(parse(mapper.writeValueAsString(java.util.Map.of("verdict","PASSED","issues",java.util.List.of(good)))).verdict()).isEqualTo(Verdict.INVALID_RESPONSE);
        assertThat(parse(mapper.writeValueAsString(java.util.Map.of("verdict","MISMATCH","issues",java.util.Collections.nCopies(6,good)))).verdict()).isEqualTo(Verdict.INVALID_RESPONSE);
        var invalid = new java.util.HashMap<>(good);
        for (String field : good.keySet()) {
            invalid.clear(); invalid.putAll(good); invalid.remove(field);
            assertThat(parse(mapper.writeValueAsString(java.util.Map.of("verdict","MISMATCH","issues",java.util.List.of(invalid)))).passed()).isFalse();
        }
        invalid.clear();invalid.putAll(good);invalid.put("reason","x".repeat(501));
        assertThat(parse(mapper.writeValueAsString(java.util.Map.of("verdict","MISMATCH","issues",java.util.List.of(invalid)))).verdict()).isEqualTo(Verdict.INVALID_RESPONSE);
    }
}
