package com.example.service.notebook;

import com.example.service.notebook.NotebookTypes.*;
import jakarta.annotation.PreDestroy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** One explicitly enabled, local-only paid trial. Marker survives deletion, logout and restart. */
@Service
public class NotebookPhotoTrialService {
    public record TrialConfig(boolean ready, boolean used, String mode, String notice, List<OpenAiNotebookPhotoAi.Usage> usage, List<OpenAiNotebookPhotoAi.Failure> failures) {}
    private final NotebookPhotoAi ai;
    private final boolean enabled;
    private final String account;
    private final Path marker;
    private final Clock clock;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {var t = new Thread(r, "notebook-photo-trial"); t.setDaemon(true); return t;});
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {var t = new Thread(r, "notebook-photo-cleaner"); t.setDaemon(true); return t;});
    private Job job;
    private Future<?> task;

    @org.springframework.beans.factory.annotation.Autowired
    public NotebookPhotoTrialService(NotebookPhotoAi ai, Environment env) {
        this(ai, env.getProperty("app.solution-notebook.trial.enabled", Boolean.class, false)
                && env.acceptsProfiles(Profiles.of("local")) && !env.acceptsProfiles(Profiles.of("prod"))
                && "127.0.0.1".equals(env.getProperty("server.address", "")),
                env.getProperty("app.solution-notebook.trial.allowed-account", ""),
                env.getProperty("app.solution-notebook.trial.marker-file", ""), Clock.systemUTC());
    }
    NotebookPhotoTrialService(NotebookPhotoAi ai, boolean enabled, String account, String marker, Clock clock) {
        this.ai = ai; this.enabled = enabled; this.account = account;
        this.marker = marker.isBlank() ? null : Path.of(marker); this.clock = clock;
        cleaner.scheduleWithFixedDelay(this::purge, 5, 5, TimeUnit.SECONDS);
    }
    public synchronized TrialConfig configuration(Owner owner) {
        purge(); boolean used = marker != null && Files.exists(marker);
        return new TrialConfig(allowed(owner) && ai.ready() && !used, allowed(owner) && used, "live",
                "개인정보 없는 샘플 1건의 실제 API 시험입니다. 사진·풀이와 필체 참고 3개를 OpenAI로 전송합니다. 예상 $0.08~$0.15, 승인 시험 예산 $1이며 확정 과금 상한은 아닙니다. 실패도 사용 횟수에 포함되며 자동 재시도하지 않습니다.",
                allowed(owner) && ai instanceof OpenAiNotebookPhotoAi provider ? provider.usage() : List.of(),
                allowed(owner) && ai instanceof OpenAiNotebookPhotoAi provider ? provider.failures() : List.of());
    }
    public void requireReady(Owner owner) {
        TrialConfig c = configuration(owner);
        if (!c.ready()) throw error(c.used() ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.SERVICE_UNAVAILABLE,
                c.used() ? "승인된 샘플 시험 1회를 사용했습니다. 추가 호출은 별도 확인이 필요합니다." : "실제 API 시험 설정 또는 서버 키가 준비되지 않았습니다.");
    }
    public synchronized JobView start(Owner owner, byte[] photo, int rotation) {
        requireReady(owner);
        byte[] pixels = NotebookPhotoCodec.photo(photo, rotation);
        String id = UUID.randomUUID().toString();
        try {
            // Fail closed if directory is absent/unwritable; CREATE_NEW is atomic across server processes.
            Files.writeString(marker, "approved-sample-v1\n" + clock.instant() + "\n" + id + "\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException e) {throw error(HttpStatus.TOO_MANY_REQUESTS, "승인된 샘플 시험이 이미 시작됐습니다.");}
        catch (java.io.IOException e) {throw error(HttpStatus.SERVICE_UNAVAILABLE, "시험 사용량 기록을 저장할 수 없어 호출하지 않았습니다.");}
        job = new Job(id, owner, clock.instant().plusSeconds(600));
        Job current = job;
        task = worker.submit(() -> run(current, pixels));
        return view(current);
    }
    private void run(Job current, byte[] photo) {
        try {
            if (!stage(current, State.READING)) return;
            var draft = ai.solve(photo);
            synchronized (this) {if (!alive(current)) return; current.question = draft.question();}
            if (!stage(current, State.VERIFYING)) return;
            if (!ai.verifyMath(photo, draft)) {fail(current, "문제의 조건이나 계산을 확인하지 못해 이미지 생성 전에 중단했습니다."); return;}
            synchronized (this) {if (!alive(current)) return; current.solution = draft.solution();}
            if (!stage(current, State.RENDERING)) return;
            byte[] image = ai.render(draft);
            if (!stage(current, State.CHECKING_IMAGE)) return;
            boolean passed = ai.verifyImage(image, draft);
            synchronized (this) {
                if (!alive(current)) return;
                current.verified = passed; current.image = passed ? image : null;
                current.state = passed ? State.SUCCEEDED : State.REVIEW_REQUIRED;
                current.message = passed ? "실제 문제 사진으로 만든 시험용 AI 해설입니다. 오류가 있을 수 있으니 확인해주세요." : "검토한 수식과 이미지가 일치하지 않아 이미지 대신 풀이 텍스트만 제공합니다.";
            }
        } catch (Exception e) {
            synchronized (this) {
                if (alive(current)) {
                    current.state = current.solution == null ? State.FAILED : State.REVIEW_REQUIRED;
                    current.message = current.solution == null ? "문제 판독 또는 AI 처리에 실패해 중단했습니다. 자동으로 재시도하지 않습니다."
                            : "이미지 생성·검토에 실패해 풀이 텍스트만 제공합니다. 자동으로 재시도하지 않습니다.";
                }
            }
        }
        finally {Arrays.fill(photo, (byte)0);}
    }
    public synchronized JobView status(Owner owner, String id) {return view(find(owner, id));}
    public synchronized byte[] image(Owner owner, String id) {
        Job j = find(owner, id);
        if (j.state != State.SUCCEEDED || !j.verified || j.image == null) throw error(HttpStatus.CONFLICT, "검토된 이미지가 없습니다.");
        return j.image.clone();
    }
    public synchronized void delete(Owner owner, String id) {find(owner, id); clear();}
    private boolean allowed(Owner o) {return enabled && marker != null && marker.isAbsolute() && (o.role()+":"+o.userId()+":"+o.academyId()+":"+o.classId()).equals(account);}
    private synchronized boolean stage(Job j, State state) {if (!alive(j)) return false; j.state = state; return true;}
    private synchronized void fail(Job j, String message) {if (alive(j)) {j.state = State.FAILED; j.message = message;}}
    private boolean alive(Job j) {return job == j && clock.instant().isBefore(j.expiresAt);}
    private Job find(Owner owner, String id) {purge(); if (job == null || !job.id.equals(id) || !job.owner.equals(owner)) throw error(HttpStatus.NOT_FOUND, "작업이 없거나 접근할 수 없습니다."); return job;}
    private JobView view(Job j) {return new JobView(j.id, Kind.SOLUTION, j.state, "live", j.question, List.of(), j.solution, j.image != null && j.verified, j.verified, j.message, j.expiresAt);}
    private synchronized void purge() {if (job != null && !clock.instant().isBefore(job.expiresAt)) clear();}
    private void clear() {if (job != null && job.image != null) Arrays.fill(job.image, (byte)0); job = null; if (task != null) task.cancel(true);}
    @PreDestroy public synchronized void close() {clear(); worker.shutdownNow(); cleaner.shutdownNow();}
    private static ResponseStatusException error(HttpStatus status, String text) {return new ResponseStatusException(status, text);}
    private static class Job {
        final String id; final Owner owner; final Instant expiresAt;
        State state = State.QUEUED; String question = "", message = "실제 API 시험 처리 중"; Solution solution; byte[] image; boolean verified;
        Job(String id, Owner owner, Instant expiresAt) {this.id=id; this.owner=owner; this.expiresAt=expiresAt;}
    }
}
