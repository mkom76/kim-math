package com.example.service;

import com.example.service.homeworkpdf.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class HomeworkPdfValidationTest {
    @Test void rejects_out_of_range_and_non_finite_regions(){
        for(var region:List.of(new PdfTypes.Region(0,0,0,1,1),new PdfTypes.Region(2,0,0,1,1),new PdfTypes.Region(1,-0.1,0,1,1),
                new PdfTypes.Region(1,0,0,Double.NaN,1),new PdfTypes.Region(1,0.5,0,1,1)))
            assertThatThrownBy(()->HomeworkPdfService.validateRegions(List.of(region),1)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void page_limit_is_checked_on_the_actual_document()throws Exception{
        var files=new HomeworkPdfFiles(new MockEnvironment());
        try(var document=new PDDocument();var bytes=new ByteArrayOutputStream()){
            for(int i=0;i<21;i++)document.addPage(new PDPage());document.save(bytes);
            assertThatThrownBy(()->files.validate(bytes.toByteArray())).hasMessageContaining("20");
        }
    }
    @Test void server_and_frontend_share_the_same_catalog_and_fraction_paths()throws Exception{
        var resource=new org.springframework.core.io.ClassPathResource("homework/question-types.json");
        var mapper=new ObjectMapper();
        // Windows checkout can use different line endings for frontend and Java resources.
        try(var in=resource.getInputStream()) {assertThat(mapper.readTree(in)).isEqualTo(mapper.readTree(Files.readString(Path.of("frontend/src/data/homeworkQuestionTypes.json"))));}
        var catalog=new HomeworkTypeCatalog(new ObjectMapper());
        var candidate=catalog.candidates("CALC1").stream().filter(c->((List<?>)c.get("path")).contains("0/0 꼴 극한값의 계산")).findFirst().orElseThrow();
        assertThat(catalog.get((String)candidate.get("id"),"CALC1").path()).hasSize(4);
        assertThat(catalog.get((String)candidate.get("id"),"ALG")).isNull();
    }
}
