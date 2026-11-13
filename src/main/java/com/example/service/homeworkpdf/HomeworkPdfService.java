package com.example.service.homeworkpdf;

import com.example.config.security.TenantContext;
import com.example.entity.*;
import com.example.repository.*;
import com.example.service.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.core.env.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.example.service.homeworkpdf.PdfTypes.*;

@Service
public class HomeworkPdfService {
    private final HomeworkPdfImportRepository imports;
    private final HomeworkPdfQuestionRepository questions;
    private final HomeworkRepository homeworks;
    private final HomeworkProblemRepository problems;
    private final AcademyClassRepository classes;
    private final AuthorizationService auth;
    private final HomeworkPdfFiles files;
    private final HomeworkPdfAi ai;
    private final HomeworkTypeCatalog catalog;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final PdfImportQuota quota;
    private final boolean enabled, mock;
    private final int dailyLimit, globalDailyLimit;
    private final ExecutorService worker = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),
            r->{var t=new Thread(r,"homework-pdf-analysis");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService cleaner=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"homework-pdf-cleanup");t.setDaemon(true);return t;});
    private static final ZoneId ZONE=ZoneId.of("Asia/Seoul");
    public HomeworkPdfService(HomeworkPdfImportRepository imports, HomeworkPdfQuestionRepository questions,
            HomeworkRepository homeworks, HomeworkProblemRepository problems, AcademyClassRepository classes,
            AuthorizationService auth, HomeworkPdfFiles files, HomeworkPdfAi ai, HomeworkTypeCatalog catalog,
            ObjectMapper mapper, PlatformTransactionManager manager, Environment env, PdfImportQuota quota) {
        this.imports=imports;this.questions=questions;this.homeworks=homeworks;this.problems=problems;this.classes=classes;
        this.auth=auth;this.files=files;this.ai=ai;this.catalog=catalog;this.mapper=mapper;tx=new TransactionTemplate(manager);this.quota=quota;
        enabled=env.getProperty("app.homework-pdf.enabled",Boolean.class,false);
        mock=env.getProperty("app.homework-pdf.mock",Boolean.class,false)
                && env.acceptsProfiles(Profiles.of("local","test")) && !env.acceptsProfiles(Profiles.of("prod"));
        dailyLimit=bounded(env.getProperty("app.homework-pdf.daily-limit",Integer.class,3),1,20);
        globalDailyLimit=bounded(env.getProperty("app.homework-pdf.global-daily-limit",Integer.class,20),1,100);
        if(enabled) cleaner.scheduleWithFixedDelay(()->{try{purgeDrafts();}catch(RuntimeException ignored){}},60,60,TimeUnit.SECONDS);
    }
    public Config configuration() {
        teacher();
        return new Config(enabled,enabled&&files.configured()&&(mock||ai.ready()),mock,HomeworkPdfFiles.MAX_BYTES,
                HomeworkPdfFiles.MAX_PAGES,HomeworkPdfFiles.MAX_QUESTIONS,catalog.version(),
                mock?"로컬 검증용 가상 분석입니다. 실제 PDF 내용으로 분류한 결과가 아닙니다.":
                "PDF 전체의 문제 내용과 페이지 이미지가 OpenAI로 전송됩니다. 이름·연락처가 포함된 파일은 제거 후 올려 주세요. 등록 전에 문항 누락과 유형을 확인해 주세요.");
    }
    public synchronized View start(byte[] pdf,String fileName,Long classId,String subjectId,String requestKey,boolean consent) {
        return quota.start(()->{
        var ctx=teacher();
        if(!configuration().ready()) throw error(HttpStatus.SERVICE_UNAVAILABLE,"PDF 등록 기능의 연결 설정을 준비 중입니다.");
        if(!consent) throw bad("PDF 분석 전송 안내를 확인해 주세요.");
        if(!catalog.hasSubject(subjectId)) throw bad("과목을 선택해 주세요.");
        uuid(requestKey);
        int pages=files.validate(pdf);
        String hash=HomeworkPdfFiles.hash(pdf);
        var clazz=classes.findById(classId).orElseThrow(()->bad("반을 찾을 수 없습니다."));
        auth.assertCanModifyClass(clazz); AcademyClassPolicy.assertActive(clazz);
        View existing=tx.execute(status->{
            var prior=imports.findByTeacherIdAndRequestKey(ctx.teacherId(),requestKey);
            if(prior.isEmpty()) return null;
            var job=owned(prior.get().getId(),false);
            if(!job.getFileSha256().equals(hash)||!job.getClassId().equals(classId)||!job.getSubjectId().equals(subjectId))
                throw conflict("같은 요청에 다른 PDF가 포함되어 있습니다. 새 분석을 시작해 주세요.");
            return view(job);
        });
        if(existing!=null) return existing;
        LocalDateTime now=now();
        quota.check(ctx.teacherId(),dailyLimit,globalDailyLimit,now);
        String id=UUID.randomUUID().toString();
        try{files.write(id,pdf);}catch(IOException e){throw error(HttpStatus.SERVICE_UNAVAILABLE,"PDF 원본을 보관하지 못했습니다.");}
        try {
            tx.executeWithoutResult(status->{
                var job=new HomeworkPdfImport();job.setId(id);job.setAcademyId(ctx.academyId());job.setTeacherId(ctx.teacherId());job.setClassId(classId);
                job.setSubjectId(subjectId);job.setState("PROCESSING");job.setStorageKey(id);job.setOriginalFileName(safeName(fileName));job.setFileSha256(hash);
                job.setFileSize((long)pdf.length);job.setPageCount(pages);job.setCatalogVersion(catalog.version());job.setAiModel(mock?"mock":ai.model());
                job.setRequestKey(requestKey);job.setCreatedAt(now);job.setUpdatedAt(now);job.setExpiresAt(now.plusMinutes(20));imports.saveAndFlush(job);
            });
        } catch(RuntimeException e) {try{files.delete(id);}catch(IOException ignored){} throw e;}
        try{worker.submit(()->analyze(id));}catch(RejectedExecutionException e){failed(id,"BUSY");}
        return read(id);
        });
    }
    private void analyze(String id) {
        byte[] pdf=null;
        try {
            var job=tx.execute(status->imports.findById(id).orElseThrow());
            if(!"PROCESSING".equals(job.getState())) return;
            pdf=files.read(job.getStorageKey());
            var candidates=catalog.candidates(job.getSubjectId());
            Analysis result=mock?new Analysis(true,List.of(new DetectedQuestion("1","로컬 검증용 가상 문항입니다. 실제 PDF를 판독하지 않았습니다.",
                    List.of(new Region(1,0.05,0.1,0.9,0.3)),(String)candidates.getFirst().get("id"),"가상 유형 제안",true))):ai.analyze(pdf,candidates);
            if(result==null||result.questions()==null||result.questions().isEmpty()||result.questions().size()>HomeworkPdfFiles.MAX_QUESTIONS)
                throw new IllegalArgumentException("Invalid question count");
            var rows=new ArrayList<HomeworkPdfQuestion>();var numbers=new HashSet<String>();
            for(var detected:result.questions()) {
                String number=required(detected.number(),40,"문항 번호");
                if(!numbers.add(number)) throw new IllegalArgumentException("Duplicate question numbers");
                validateRegions(detected.regions(),job.getPageCount());
                var type=catalog.get(detected.typeId(),job.getSubjectId());
                var row=new HomeworkPdfQuestion();row.setId(UUID.randomUUID().toString());row.setImportId(id);row.setPosition(rows.size()+1);
                row.setSourceNumber(number);row.setExtractedText(required(detected.text(),6000,"문항 내용"));row.setRegionsJson(json(detected.regions()));
                row.setSuggestedTypeId(type==null?null:type.id());row.setTypePathSnapshot(type==null?"[]":json(type.path()));
                row.setReviewState(detected.needsReview()||type==null||type.proposed()||type.corrected()?"REVIEW_REQUIRED":"SUGGESTED");
                row.setClassificationReason(type==null?"유형을 확정하지 못했습니다. 직접 선택해 주세요.":limited(detected.reason(),500));rows.add(row);
            }
            tx.executeWithoutResult(status->{
                var current=imports.findLocked(id).orElseThrow();
                if(!"PROCESSING".equals(current.getState())||current.getExpiresAt().isBefore(now())) return;
                questions.saveAll(rows);current.setReviewRequired(!result.complete());current.setState("REVIEW");current.setUpdatedAt(now());
                current.setExpiresAt(now().plusHours(24));imports.save(current);
            });
        } catch(Exception e){failed(id,"ANALYSIS_FAILED");}
        finally{if(pdf!=null) Arrays.fill(pdf,(byte)0);}
    }
    private void failed(String id,String code) {
        tx.executeWithoutResult(status->{var job=imports.findLocked(id).orElse(null);
            if(job!=null&&"PROCESSING".equals(job.getState())){job.setState("FAILED");job.setErrorCode(code);job.setUpdatedAt(now());imports.save(job);}});
    }
    public View read(String id) {return tx.execute(status->view(owned(id,true)));}
    public byte[] document(String id) {
        String key=tx.execute(status->{var job=owned(id,true);if(!Set.of("REVIEW","REGISTERED").contains(job.getState())) throw conflict("PDF를 준비 중이거나 만료됐습니다.");return job.getStorageKey();});
        try{return files.read(key);}catch(IOException e){throw error(HttpStatus.NOT_FOUND,"PDF 원본을 찾을 수 없습니다.");}
    }
    public void cancel(String id) {tx.executeWithoutResult(status->{var job=owned(id,false);
        if("REGISTERED".equals(job.getState())) throw conflict("등록된 숙제 원본은 취소할 수 없습니다.");
        job.setState("CANCELLED");job.setExpiresAt(now());job.setUpdatedAt(now());imports.save(job);
    });}
    public View register(String id,Register request) {
        return tx.execute(status->{
            var job=owned(id,false);String fingerprint=HomeworkPdfFiles.hash(json(request).getBytes(StandardCharsets.UTF_8));
            if("REGISTERED".equals(job.getState())) {
                if(!fingerprint.equals(job.getRegistrationHash())) throw conflict("이미 다른 내용으로 등록된 숙제입니다.");
                return view(job);
            }
            if(!"REVIEW".equals(job.getState())) throw conflict("분석 완료 후 등록해 주세요.");
            if(request==null||!Objects.equals(request.expectedVersion(),job.getVersion())||!Objects.equals(request.catalogVersion(),job.getCatalogVersion())
                    ||!catalog.version().equals(job.getCatalogVersion())) throw conflict("분석 결과가 바뀌었습니다. 다시 열어 확인해 주세요.");
            if(!request.contentsReviewed()) throw bad("문항 누락·번호·유형을 확인해 주세요.");
            String title=required(request.title(),255,"숙제명");
            if(request.memo()!=null&&request.memo().length()>2000) throw bad("메모는 2000자까지 입력해 주세요.");
            if(request.questions()==null||request.questions().isEmpty()||request.questions().size()>HomeworkPdfFiles.MAX_QUESTIONS) throw bad("문항은 1~60개까지 등록해 주세요.");
            var clazz=classes.findById(job.getClassId()).orElseThrow(()->bad("반을 찾을 수 없습니다."));
            auth.assertCanModifyClass(clazz);AcademyClassPolicy.assertActive(clazz);
            var original=questions.findByImportIdOrderByPositionAsc(id);var byId=new HashMap<String,HomeworkPdfQuestion>();original.forEach(q->byId.put(q.getId(),q));
            var rows=new ArrayList<HomeworkPdfQuestion>();var usedIds=new HashSet<String>();var numbers=new HashSet<String>();
            for(var input:request.questions()) {
                if(input==null||input.id()==null||!usedIds.add(input.id())) throw bad("문항이 중복되었거나 올바르지 않습니다.");
                var previous=byId.get(input.id());
                boolean newQuestion=input.id().startsWith("new-");
                if(previous==null&&!newQuestion) throw bad("이 PDF의 문항만 등록할 수 있습니다.");
                String number=required(input.sourceNumber(),40,"문항 번호");
                if(!numbers.add(number)) throw bad("같은 문항 번호가 있습니다. 단원명 등을 붙여 구분해 주세요.");
                validateRegions(input.regions(),job.getPageCount());
                var type=input.typeId()==null?null:catalog.get(input.typeId(),job.getSubjectId());
                if(input.typeId()!=null&&type==null) throw bad("선택한 과목의 유형을 선택해 주세요.");
                var row=new HomeworkPdfQuestion();row.setId(previous==null?UUID.randomUUID().toString():previous.getId());row.setImportId(id);
                row.setPosition(rows.size()+1);row.setSourceNumber(number);row.setExtractedText(required(input.text(),6000,"문항 내용"));row.setRegionsJson(json(input.regions()));
                row.setSuggestedTypeId(previous==null?null:previous.getSuggestedTypeId());row.setConfirmedTypeId(type==null?null:type.id());
                row.setTypePathSnapshot(type==null?"[]":json(type.path()));row.setReviewState(type==null?"UNCLASSIFIED":"CONFIRMED");
                row.setClassificationReason(previous==null?"직접 추가한 문항":previous.getClassificationReason());rows.add(row);
            }
            // Validate everything before creating any homework or replacing draft rows.
            var homework=Homework.builder().title(title).questionCount(rows.size()).memo(request.memo()).dueDate(request.dueDate())
                    .academy(clazz.getAcademy()).academyClass(clazz).build();homeworks.saveAndFlush(homework);
            questions.deleteDraftRows(id);
            for(var row:rows) {
                var slot=HomeworkProblem.builder().homework(homework).position(row.getPosition()).build();problems.saveAndFlush(slot);row.setHomeworkProblemId(slot.getId());
            }
            questions.saveAll(rows);job.setHomeworkId(homework.getId());job.setState("REGISTERED");job.setRegistrationHash(fingerprint);job.setUpdatedAt(now());
            job=imports.saveAndFlush(job);return view(job);
        });
    }
    private HomeworkPdfImport owned(String id,boolean allowShared) {
        var ctx=teacher();uuid(id);var job=imports.findLocked(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"PDF 작업을 찾을 수 없습니다."));
        if(!ctx.academyId().equals(job.getAcademyId())) throw error(HttpStatus.NOT_FOUND,"PDF 작업을 찾을 수 없습니다.");
        if(allowShared&&"REGISTERED".equals(job.getState())&&job.getHomeworkId()!=null) {
            auth.assertCanAccessHomework(homeworks.findById(job.getHomeworkId()).orElseThrow());
        } else if(!ctx.teacherId().equals(job.getTeacherId())) throw error(HttpStatus.NOT_FOUND,"PDF 작업을 찾을 수 없습니다.");
        else auth.assertCanModifyClass(classes.findById(job.getClassId()).orElseThrow());
        if(!"REGISTERED".equals(job.getState())&&job.getExpiresAt().isBefore(now())) throw error(HttpStatus.GONE,"PDF 분석 초안이 만료됐습니다. 다시 올려 주세요.");
        return job;
    }
    private View view(HomeworkPdfImport job) {
        var rows=questions.findByImportIdOrderByPositionAsc(job.getId()).stream().map(row->new Question(row.getId(),row.getPosition(),row.getSourceNumber(),row.getExtractedText(),
                decode(row.getRegionsJson(),new TypeReference<List<Region>>(){}),row.getSuggestedTypeId(),row.getConfirmedTypeId(),decode(row.getTypePathSnapshot(),new TypeReference<List<String>>(){}),
                row.getClassificationReason(),"REVIEW_REQUIRED".equals(row.getReviewState()))).toList();
        String message=switch(job.getState()) {case "PROCESSING"->"PDF 문항과 유형을 분석하고 있습니다.";case "REVIEW"->job.getReviewRequired()?"문항 누락 가능성이 있습니다. PDF와 문항 수를 꼭 확인해 주세요.":"문항과 유형을 확인한 뒤 등록해 주세요.";
            case "REGISTERED"->"숙제 등록이 완료되었습니다.";case "FAILED"->"PDF 분석을 완료하지 못했습니다. 파일을 확인한 뒤 새 분석을 시작해 주세요.";default->"PDF 분석이 취소되었거나 만료됐습니다.";};
        return new View(job.getId(),job.getState(),job.getVersion(),job.getHomeworkId(),job.getClassId(),job.getSubjectId(),job.getOriginalFileName(),job.getPageCount(),
                job.getCatalogVersion(),job.getReviewRequired(),job.getExpiresAt(),mock?"[가상 분석] "+message:message,rows);
    }
    public void purgeDrafts() {
        if(!enabled||!files.configured()) return;
        for(var candidate:imports.findTop20ByStateInAndExpiresAtBefore(List.of("PROCESSING","REVIEW","FAILED","CANCELLED"),now())) {
            try{tx.executeWithoutResult(status->{var job=imports.findLocked(candidate.getId()).orElseThrow();
                if("REGISTERED".equals(job.getState())||!job.getExpiresAt().isBefore(now())) return;
                try{files.delete(job.getStorageKey());}catch(IOException e){throw new IllegalStateException("PDF cleanup failed");}
                questions.deleteByImportId(job.getId());job.setState("PURGED");job.setOriginalFileName("expired.pdf");job.setUpdatedAt(now());imports.save(job);
            });}catch(RuntimeException ignored){/* Keep the row for the next bounded cleanup pass. */}
        }
    }
    private TenantContext.Context teacher() {
        var ctx=TenantContext.current();
        if(ctx==null||ctx.role()==null||ctx.role()==TeacherAcademyRole.ASSISTANT) throw error(HttpStatus.FORBIDDEN,"숙제 PDF 등록은 담당 교사만 이용할 수 있습니다.");
        return ctx;
    }
    public static void validateRegions(List<Region> regions,int pages) {
        if(regions==null||regions.isEmpty()||regions.size()>20) throw bad("문항이 있는 PDF 페이지를 확인해 주세요.");
        for(var r:regions) if(r==null||r.page()<1||r.page()>pages||!Double.isFinite(r.x())||!Double.isFinite(r.y())||!Double.isFinite(r.width())||!Double.isFinite(r.height())
                ||r.x()<0||r.y()<0||r.width()<=0||r.height()<=0||r.x()+r.width()>1.00001||r.y()+r.height()>1.00001) throw bad("문항의 PDF 위치가 올바르지 않습니다.");
    }
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(IOException e){throw new IllegalArgumentException("Invalid PDF data");}}
    private <T>T decode(String value,TypeReference<T> type){try{return mapper.readValue(value,type);}catch(IOException e){throw new IllegalStateException("Invalid stored PDF data");}}
    private static LocalDateTime now(){return LocalDateTime.now(ZONE);}
    private static int bounded(int value,int min,int max){if(value<min||value>max)throw new IllegalArgumentException("Invalid PDF limit");return value;}
    private static String required(String value,int max,String field){if(value==null||value.isBlank()||value.length()>max)throw bad(field+"을(를) 확인해 주세요.");return value.trim();}
    private static String limited(String value,int max){return value==null?"":value.substring(0,Math.min(value.length(),max));}
    private static String safeName(String value){return limited(value==null?"homework.pdf":value.replaceAll("[\\\\/\\p{Cntrl}]","_"),255);}
    private static void uuid(String value){try{if(value==null||!UUID.fromString(value).toString().equals(value))throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw bad("요청 식별자가 올바르지 않습니다.");}}
    private static ResponseStatusException bad(String message){return error(HttpStatus.BAD_REQUEST,message);}
    private static ResponseStatusException conflict(String message){return error(HttpStatus.CONFLICT,message);}
    private static ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
    @PreDestroy public void close(){cleaner.shutdownNow();worker.shutdownNow();}
}
