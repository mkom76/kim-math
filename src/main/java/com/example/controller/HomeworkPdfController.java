package com.example.controller;

import com.example.service.homeworkpdf.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static com.example.service.homeworkpdf.PdfTypes.*;

@RestController @RequestMapping("/api/homeworks") @RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER') and !hasRole('ASSISTANT')")
public class HomeworkPdfController {
    private final HomeworkPdfService service;
    private final HomeworkTypeCatalog catalog;
    @GetMapping("/pdf-config") public Config config(){return service.configuration();}
    @GetMapping("/pdf-types") public Object types(){service.configuration();return catalog.document();}
    @PostMapping(value="/pdf-imports",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public View upload(@RequestParam MultipartFile file,@RequestParam Long classId,@RequestParam String subjectId,
                       @RequestParam String requestKey,@RequestParam boolean consent) throws IOException {
        if(file.getSize()>HomeworkPdfFiles.MAX_BYTES) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"PDF는 8MB 이하로 올려 주세요.");
        byte[] bytes=file.getBytes();
        try{return service.start(bytes,file.getOriginalFilename(),classId,subjectId,requestKey,consent);}
        finally{java.util.Arrays.fill(bytes,(byte)0);}
    }
    @GetMapping("/pdf-imports/{id}") public View get(@PathVariable String id){return service.read(id);}
    @GetMapping("/pdf-imports/{id}/document") public ResponseEntity<byte[]> document(@PathVariable String id){
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=homework.pdf").body(service.document(id));
    }
    @PostMapping("/pdf-imports/{id}/register") public View register(@PathVariable String id,@RequestBody Register request){return service.register(id,request);}
    @DeleteMapping("/pdf-imports/{id}") public ResponseEntity<Void> cancel(@PathVariable String id){service.cancel(id);return ResponseEntity.noContent().build();}
}
