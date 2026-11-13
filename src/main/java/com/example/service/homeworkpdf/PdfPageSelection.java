package com.example.service.homeworkpdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.TreeSet;
import org.apache.pdfbox.cos.*;

public final class PdfPageSelection {
    private PdfPageSelection() {}
    public record ExamSelection(byte[] pdf, int questionPageCount) {}
    public static ExamSelection exam(byte[] source,String questionPages,String solutionPages) {
        if(source.length>HomeworkPdfFiles.MAX_BYTES)throw bad();
        try(var original=Loader.loadPDF(source);var selected=new PDDocument();var out=new ByteArrayOutputStream()) {
            if(original.isEncrypted())throw bad();
            var questions=parse(questionPages,original.getNumberOfPages());
            var solutions=parse(solutionPages,original.getNumberOfPages());
            if(questions.stream().anyMatch(solutions::contains)||questions.size()+solutions.size()>HomeworkPdfFiles.MAX_PAGES)throw bad();
            var combined=new java.util.ArrayList<Integer>(questions);combined.addAll(solutions);
            for(int number:combined) copyPage(selected,original.getPage(number-1));
            stableId(selected,source,combined.toString());
            selected.save(out);return new ExamSelection(out.toByteArray(),questions.size());
        }catch(IOException e){throw bad();}
    }
    private static TreeSet<Integer> parse(String expression,int total) {
        if(expression==null||expression.isBlank()||expression.length()>200)throw bad();
        var result=new TreeSet<Integer>();
        try {
            for(String token:expression.split(",",-1)) {
                if(!token.trim().matches("[0-9]+(?:\\s*-\\s*[0-9]+)?"))throw bad();
                var range=token.trim().split("\\s*-\\s*");int first=Integer.parseInt(range[0]);int last=range.length==1?first:Integer.parseInt(range[1]);
                if(first<1||last<first||last>total||last-first>=HomeworkPdfFiles.MAX_PAGES)throw bad();
                for(int page=first;page<=last;page++){result.add(page);if(result.size()>HomeworkPdfFiles.MAX_PAGES)throw bad();}
            }
        }catch(NumberFormatException e){throw bad();}
        return result;
    }
    public static byte[] select(byte[] source, String expression) {
        if (expression == null || expression.isBlank()) return source;
        if (source.length > HomeworkPdfFiles.MAX_BYTES || expression.length() > 200) throw bad();
        try (var original = Loader.loadPDF(source); var selected = new PDDocument(); var output = new ByteArrayOutputStream()) {
            if (original.isEncrypted()) throw bad();
            var pages = parse(expression,original.getNumberOfPages());
            for (int page : pages) {
                copyPage(selected,original.getPage(page-1));
            }
            stableId(selected,source,pages.toString());
            selected.save(output);
            return output.toByteArray();
        } catch (IOException | NumberFormatException exception) { throw bad(); }
    }
    private static void copyPage(PDDocument selected,org.apache.pdfbox.pdmodel.PDPage source) throws IOException {
        // Copy visible page content, not links/actions or page-tree references
        // that could pull excluded pages into the uploaded document.
        var page=new org.apache.pdfbox.pdmodel.PDPage(source.getMediaBox());
        page.setCropBox(source.getCropBox());page.setRotation(source.getRotation());page.setUserUnit(source.getUserUnit());
        page.setResources(source.getResources());
        try(var content=source.getContents()) {
            page.setContents(new org.apache.pdfbox.pdmodel.common.PDStream(selected,content,COSName.FLATE_DECODE));
        }
        selected.addPage(page);
    }
    private static void stableId(PDDocument document,byte[] source,String pages) {
        // Stable output keeps upload retries with the same request key idempotent.
        String value=HomeworkPdfFiles.hash(source)+pages;
        var id=new COSString(HomeworkPdfFiles.hash(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var ids=new COSArray();ids.add(id);ids.add(id);
        document.getDocument().getTrailer().setItem(COSName.ID,ids);
    }
    private static ResponseStatusException bad() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF의 실제 페이지 번호를 1-3,5 형식으로 입력해 주세요. 선택은 20쪽 이내여야 합니다.");
    }
}
