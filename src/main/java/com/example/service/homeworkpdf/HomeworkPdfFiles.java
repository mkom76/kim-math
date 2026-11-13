package com.example.service.homeworkpdf;

import org.apache.pdfbox.Loader;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class HomeworkPdfFiles {
    public static final int MAX_BYTES=8*1024*1024, MAX_PAGES=20, MAX_QUESTIONS=60;
    private final Path root;
    public HomeworkPdfFiles(Environment env) {
        String directory=env.getProperty("app.homework-pdf.storage-dir","");
        root=directory.isBlank()?null:Path.of(directory).toAbsolutePath().normalize();
    }
    public boolean configured() {return root!=null;}
    public int validate(byte[] bytes) {
        if (bytes.length<5 || bytes.length>MAX_BYTES || bytes[0]!='%' || bytes[1]!='P' || bytes[2]!='D' || bytes[3]!='F' || bytes[4]!='-')
            throw bad("8MB 이하 PDF 파일을 선택해 주세요.");
        try(var document=Loader.loadPDF(bytes)) {
            if(document.isEncrypted()) throw bad("암호가 없는 PDF를 올려 주세요.");
            int count=document.getNumberOfPages();
            if(count<1 || count>MAX_PAGES) throw bad("PDF는 1~20쪽까지 등록할 수 있습니다.");
            return count;
        } catch(IOException e) {throw bad("PDF를 읽을 수 없습니다. 파일과 암호 설정을 확인해 주세요.");}
    }
    private Path file(String key) {
        if(root==null) throw new IllegalStateException("PDF storage is not configured");
        if(key==null || !key.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid PDF key");
        Path target=root.resolve(key+".pdf").normalize();
        if(!target.getParent().equals(root)) throw new IllegalArgumentException("Invalid PDF path");
        return target;
    }
    public void write(String key, byte[] bytes) throws IOException {
        Files.createDirectories(root);
        if(Files.isSymbolicLink(root)) throw new IOException("PDF directory cannot be a symbolic link");
        Files.write(file(key),bytes,StandardOpenOption.CREATE_NEW);
    }
    public byte[] read(String key) throws IOException {
        var target=file(key);
        if(Files.isSymbolicLink(target) || Files.size(target)>MAX_BYTES) throw new IOException("Invalid PDF file");
        return Files.readAllBytes(target);
    }
    public void delete(String key) throws IOException {Files.deleteIfExists(file(key));}
    public static String hash(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
