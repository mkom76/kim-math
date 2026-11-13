package com.example.service.homeworkpdf;

import com.fasterxml.jackson.databind.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component
public class HomeworkTypeCatalog {
    public record Type(String id, String subjectId, List<String> path, boolean proposed, boolean corrected) {}
    private final JsonNode document;
    private final Map<String, Type> types = new LinkedHashMap<>();
    private final Set<String> subjects = new HashSet<>();
    public HomeworkTypeCatalog(ObjectMapper mapper) throws IOException {
        try (var in = new ClassPathResource("homework/question-types.json").getInputStream()) {document = mapper.readTree(in);}
        for (var subject : document.path("subjects")) {subjects.add(subject.path("id").asText()); walk(subject, subject.path("id").asText(), List.of());}
        if (types.isEmpty()) throw new IllegalStateException("Empty homework type catalog");
    }
    private void walk(JsonNode node, String subject, List<String> parents) {
        var path = new ArrayList<>(parents); path.add(node.path("name").asText());
        if (node.has("children")) {for (var child : node.path("children")) walk(child, subject, path); return;}
        String id = node.path("id").asText();
        if (path.size()!=4 || types.containsKey(id)) throw new IllegalStateException("Invalid homework type catalog");
        types.put(id, new Type(id, subject, List.copyOf(path), node.path("proposed").asBoolean(), node.path("corrected").asBoolean()));
    }
    public JsonNode document() {return document.deepCopy();}
    public String version() {return document.path("version").asText();}
    public boolean hasSubject(String id) {return subjects.contains(id);}
    public Type get(String id, String subject) {var type = types.get(id); return type!=null && type.subjectId().equals(subject) ? type : null;}
    public List<Map<String,Object>> candidates(String subject) {return types.values().stream().filter(t->t.subjectId().equals(subject))
            .map(t->Map.<String,Object>of("id",t.id(),"path",t.path())).toList();}
}
