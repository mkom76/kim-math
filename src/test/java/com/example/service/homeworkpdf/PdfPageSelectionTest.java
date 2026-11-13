package com.example.service.homeworkpdf;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.*;
class PdfPageSelectionTest {
    byte[] document() throws Exception {
        try(var pdf=new PDDocument();var out=new ByteArrayOutputStream()){
            for(int i=1;i<=4;i++){var page=new PDPage();page.setRotation(i*90);pdf.addPage(page);}pdf.save(out);return out.toByteArray();
        }
    }
    @Test void excludes_unselected_pages_and_preserves_original_order() throws Exception {
        try(var selected=Loader.loadPDF(PdfPageSelection.select(document(),"4,2-2,2"))){
            assertThat(selected.getNumberOfPages()).isEqualTo(2);
            assertThat(selected.getPage(0).getRotation()).isEqualTo(180);
            assertThat(selected.getPage(1).getRotation()).isEqualTo(0);
        }
    }
    @Test void rejects_invalid_ranges() throws Exception {
        byte[] pdf=document();
        for(String range:new String[]{"0","5","3-1","1,","1-999999999999","a","1,,2"})
            assertThatThrownBy(()->PdfPageSelection.select(pdf,range)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void retries_are_byte_identical_and_exam_pages_put_questions_before_solutions() throws Exception {
        byte[] pdf=document();
        assertThat(PdfPageSelection.select(pdf,"2,4")).isEqualTo(PdfPageSelection.select(pdf,"4,2"));
        var exam=PdfPageSelection.exam(pdf,"3-4","1");
        assertThat(exam.pdf()).isEqualTo(PdfPageSelection.exam(pdf,"3-4","1").pdf());
        assertThat(exam.questionPageCount()).isEqualTo(2);
        try(var selected=Loader.loadPDF(exam.pdf())) {
            assertThat(selected.getNumberOfPages()).isEqualTo(3);
            assertThat(selected.getPage(0).getRotation()).isEqualTo(270);
            assertThat(selected.getPage(2).getRotation()).isEqualTo(90);
        }
        assertThatThrownBy(()->PdfPageSelection.exam(pdf,"1-3","3-4")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void inherited_fonts_survive_and_excluded_page_text_is_absent() throws Exception {
        byte[] source;
        try(var pdf=new PDDocument();var out=new ByteArrayOutputStream()) {
            var resources=new PDResources();
            var font=new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
            var fontName=resources.add(font);
            pdf.getPages().getCOSObject().setItem(org.apache.pdfbox.cos.COSName.RESOURCES,resources);
            for(String text:new String[]{"KEEP PROBLEM","EXCLUDED SECRET"}) {
                var page=new PDPage();pdf.addPage(page);
                var stream=new org.apache.pdfbox.pdmodel.common.PDStream(pdf);
                try(var content=stream.createOutputStream()){content.write(("BT /"+fontName.getName()+" 12 Tf 10 20 Td ("+text+") Tj ET").getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
                page.setContents(stream);
            }
            var link=new org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink();
            var destination=new org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination();
            destination.setPage(pdf.getPage(1));link.setDestination(destination);
            pdf.getPage(0).setAnnotations(java.util.List.of(link));
            pdf.save(out);source=out.toByteArray();
        }
        try(var selected=Loader.loadPDF(PdfPageSelection.select(source,"1"))) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(selected)).contains("KEEP PROBLEM").doesNotContain("EXCLUDED SECRET");
            for(var key:selected.getDocument().getXrefTable().keySet()) {
                var object=selected.getDocument().getObjectFromPool(key).getObject();
                if(object instanceof org.apache.pdfbox.cos.COSStream stream) {
                    try(var decoded=stream.createInputStream()) {
                        assertThat(new String(decoded.readAllBytes(),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("EXCLUDED SECRET");
                    }
                }
            }
        }
    }
}
