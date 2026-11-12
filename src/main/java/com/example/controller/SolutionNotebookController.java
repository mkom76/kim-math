package com.example.controller;

import com.example.config.security.TenantContext;
import com.example.service.notebook.SolutionNotebookService;
import com.example.service.notebook.NotebookPhotoTrialService;
import com.example.service.notebook.NotebookPhotoCodec;
import com.example.service.notebook.NotebookTypes.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.Set;

@RestController
@RequestMapping("/api/solution-notebook")
@PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
@RequiredArgsConstructor
public class SolutionNotebookController {
    private final SolutionNotebookService service;
    private final ObjectMapper mapper;
    private final NotebookPhotoTrialService trial;
    private final com.example.service.notebook.HandwrittenSolutionService live;

    @GetMapping("/live-config")
    public ResponseEntity<com.example.service.notebook.HandwrittenSolutionService.LiveConfig> liveConfig(HttpServletRequest request, Authentication authentication) {
        return json(live.configuration(owner(request, authentication)));
    }
    @PostMapping("/live-jobs")
    public ResponseEntity<JobView> livePhoto(HttpServletRequest request, Authentication authentication,
                                            @RequestParam(defaultValue="0") int rotation) throws IOException {
        Owner owner = owner(request, authentication);
        live.requireReady(owner);
        if (!"notebook-v1".equals(request.getHeader("X-Notebook-Consent")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진과 풀이의 외부 전송 안내를 확인해주세요.");
        if (!Set.of("image/jpeg", "image/png").contains(java.util.Objects.toString(request.getContentType(), "")))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JPG 또는 PNG 사진이 필요합니다.");
        if (request.getContentLengthLong() > NotebookPhotoCodec.MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "사진 용량이 너무 큽니다.");
        byte[] bytes = request.getInputStream().readNBytes(NotebookPhotoCodec.MAX_BYTES + 1);
        try {return json(live.start(owner, bytes, rotation));}
        finally {java.util.Arrays.fill(bytes, (byte)0);}
    }
    @GetMapping("/live-jobs/{id}")
    public ResponseEntity<JobView> liveStatus(@PathVariable String id, HttpServletRequest request, Authentication authentication) {
        return json(live.status(owner(request, authentication), id));
    }
    @GetMapping("/live-jobs/{id}/image")
    public ResponseEntity<byte[]> liveImage(@PathVariable String id, HttpServletRequest request, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=kim-math-solution.png")
                .header("X-Content-Type-Options", "nosniff").body(live.image(owner(request, authentication), id));
    }
    @DeleteMapping("/live-jobs/{id}")
    public ResponseEntity<Void> liveDelete(@PathVariable String id, HttpServletRequest request, Authentication authentication) {
        live.delete(owner(request, authentication), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/trial-config")
    public ResponseEntity<NotebookPhotoTrialService.TrialConfig> trialConfig(HttpServletRequest request, Authentication authentication) {
        localTrial(request);
        return json(trial.configuration(owner(request, authentication)));
    }
    @PostMapping("/photo-jobs")
    public ResponseEntity<JobView> photo(HttpServletRequest request, Authentication authentication,
                                        @RequestParam(defaultValue="0") int rotation) throws IOException {
        localTrial(request);
        Owner owner = owner(request, authentication);
        trial.requireReady(owner);
        if (!"sample-v1".equals(request.getHeader("X-Notebook-Consent")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "개인정보 없는 샘플의 전송·비용 안내를 확인해주세요.");
        if (!Set.of("image/jpeg", "image/png").contains(java.util.Objects.toString(request.getContentType(), "")))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JPG 또는 PNG 사진이 필요합니다.");
        if (request.getContentLengthLong() > NotebookPhotoCodec.MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "사진 용량이 너무 큽니다.");
        byte[] bytes = request.getInputStream().readNBytes(NotebookPhotoCodec.MAX_BYTES + 1);
        try {return json(trial.start(owner, bytes, rotation));}
        finally {java.util.Arrays.fill(bytes, (byte)0);}
    }
    @GetMapping("/photo-jobs/{id}")
    public ResponseEntity<JobView> photoStatus(@PathVariable String id, HttpServletRequest request, Authentication authentication) {
        localTrial(request); return json(trial.status(owner(request, authentication), id));
    }
    @GetMapping("/photo-jobs/{id}/image")
    public ResponseEntity<byte[]> photoImage(@PathVariable String id, HttpServletRequest request, Authentication authentication) {
        localTrial(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=qa-solution.png")
                .header("X-Content-Type-Options", "nosniff").body(trial.image(owner(request, authentication), id));
    }
    @DeleteMapping("/photo-jobs/{id}")
    public ResponseEntity<Void> photoDelete(@PathVariable String id, HttpServletRequest request, Authentication authentication) {
        localTrial(request); trial.delete(owner(request, authentication), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private void localTrial(HttpServletRequest request) {
        if (!Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1").contains(request.getRemoteAddr()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "현재 실제 API 시험은 이 컴퓨터에서만 가능합니다.");
    }

    @GetMapping("/config")
    public ResponseEntity<Configuration> config(HttpServletRequest request, Authentication authentication) {
        owner(request,authentication);
        return json(service.configuration());
    }
    @PostMapping("/read-jobs")
    public ResponseEntity<JobView> read(HttpServletRequest request,Authentication authentication) throws IOException {
        Owner owner=owner(request,authentication);
        ReadRequest command=parse(request,Set.of("fixture","scenario"),ReadRequest.class);
        return json(service.read(owner,command));
    }
    @PostMapping("/solution-jobs")
    public ResponseEntity<JobView> solve(HttpServletRequest request,Authentication authentication) throws IOException {
        Owner owner=owner(request,authentication);
        SolveRequest command=parse(request,Set.of("readJobId","question","confirmed","grade","depth","scenario"),SolveRequest.class);
        return json(service.solve(owner,command));
    }
    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobView> status(@PathVariable String id,HttpServletRequest request,Authentication authentication) {
        return json(service.status(owner(request,authentication),id));
    }
    @GetMapping("/jobs/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable String id,HttpServletRequest request,Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=qa-mock-solution.png")
                .header("X-Content-Type-Options","nosniff")
                .body(service.image(owner(request,authentication),id));
    }
    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,HttpServletRequest request,Authentication authentication) {
        service.delete(owner(request,authentication),id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private <T> T parse(HttpServletRequest request,Set<String> allowed,Class<T> type) throws IOException {
        JsonNode node=body(request,allowed);
        if(node.has("confirmed")&&!node.get("confirmed").isBoolean())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"문제 확인 값은 참/거짓이어야 합니다.");
        try{return mapper.treeToValue(node,type);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"요청 항목과 체험 상황을 확인해주세요.");}
    }
    private JsonNode body(HttpServletRequest request,Set<String> allowed) throws IOException {
        if(request.getContentType()==null || !request.getContentType().split(";")[0].trim().equalsIgnoreCase("application/json"))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"JSON 형식이 필요합니다.");
        byte[] bytes=request.getInputStream().readNBytes(12001);
        if(bytes.length>12000)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"모의 요청이 너무 큽니다. 사진은 전송하지 않습니다.");
        JsonNode node;
        try {node=mapper.readTree(bytes);}catch(IOException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"요청 형식을 확인해주세요.");}
        if(node==null || !node.isObject())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"요청 형식을 확인해주세요.");
        var names=node.fieldNames();
        while(names.hasNext())if(!allowed.contains(names.next()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"모의 API에는 사진이나 추가 필드를 전송할 수 없습니다.");
        return node;
    }
    private Owner owner(HttpServletRequest request,Authentication authentication) {
        HttpSession session=request.getSession(false);
        if(session==null || authentication==null || !authentication.isAuthenticated())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다.");
        Object id=session.getAttribute("userId");String role=(String)session.getAttribute("userRole");
        if(!(id instanceof Long userId) || !Set.of("TEACHER","STUDENT").contains(role==null?"":role)
                || !userId.toString().equals(authentication.getName()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"인증 정보를 확인해주세요.");
        TenantContext.Context tenant=TenantContext.current();
        Long academy=(Long)session.getAttribute("STUDENT".equals(role)?"studentAcademyId":"activeAcademyId");
        Long classId="STUDENT".equals(role)?(Long)session.getAttribute("activeStudentClassId"):null;
        if(tenant==null || academy==null || !academy.equals(tenant.academyId()) || !userId.equals(tenant.teacherId())
                || "STUDENT".equals(role)&&!java.util.Objects.equals(classId,tenant.studentClassId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"현재 학원과 반 정보를 확인해주세요.");
        return new Owner(role,userId,academy,classId,session.getId(),String.valueOf(session.getAttribute("activeRole")));
    }
    private static <T> ResponseEntity<T> json(T value) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
