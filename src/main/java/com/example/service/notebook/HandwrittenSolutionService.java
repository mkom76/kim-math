package com.example.service.notebook;

import com.example.service.notebook.NotebookTypes.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.core.env.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Authenticated site workflow. Disabled by default; no persistent student photos or solutions. */
@Service
public class HandwrittenSolutionService {
    public record LiveConfig(boolean enabled, boolean ready, boolean preview, String mode,
                             int dailyLimit, int remainingToday, int retentionMinutes, String notice) {}
    private final NotebookPhotoAi ai;
    private final boolean enabled, preview;
    private final int dailyLimit;
    private final Clock clock;
    private final NotebookUsageLedger ledger;
    private final Map<String, Job> jobs = new HashMap<>();
    private final ExecutorService workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2), r -> {var t = new Thread(r, "notebook-live-worker"); t.setDaemon(true); return t;}, new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {var t = new Thread(r, "notebook-live-cleaner"); t.setDaemon(true); return t;});

    @org.springframework.beans.factory.annotation.Autowired
    public HandwrittenSolutionService(NotebookPhotoAi ai, ObjectMapper mapper, Environment env) {
        this(ai, mapper, env.getProperty("app.solution-notebook.live.enabled", Boolean.class, false)
                        && (!env.getProperty("app.solution-notebook.live.preview", Boolean.class, false)
                            || env.acceptsProfiles(Profiles.of("local")) && !env.acceptsProfiles(Profiles.of("prod"))),
                env.getProperty("app.solution-notebook.live.preview", Boolean.class, false),
                env.getProperty("app.solution-notebook.live.usage-dir", ""),
                env.getProperty("app.solution-notebook.live.daily-limit", Integer.class, 3),
                env.getProperty("app.solution-notebook.live.global-daily-limit", Integer.class, 20),
                env.getProperty("app.solution-notebook.live.global-monthly-limit", Integer.class, 200), Clock.systemUTC());
    }
    HandwrittenSolutionService(NotebookPhotoAi ai, ObjectMapper mapper, boolean enabled, boolean preview,
                               String usageDir, int dailyLimit, int globalDaily, int globalMonthly, Clock clock) {
        if (dailyLimit < 1 || dailyLimit > 10 || globalDaily < 1 || globalDaily > 100 || globalMonthly < 1 || globalMonthly > 2000)
            throw new IllegalArgumentException("Invalid notebook usage limits");
        this.ai = ai; this.enabled = enabled; this.preview = preview; this.dailyLimit = dailyLimit; this.clock = clock;
        this.ledger = new NotebookUsageLedger(usageDir.isBlank() ? null : Path.of(usageDir), mapper, clock, dailyLimit, globalDaily, globalMonthly);
        cleaner.scheduleWithFixedDelay(this::purge, 5, 5, TimeUnit.SECONDS);
    }
    public synchronized LiveConfig configuration(Owner owner) {
        purge();
        String notice = preview ? "로컬 화면 검증용 가짜 응답입니다. 실제 사진 판독·유료 AI 호출은 하지 않습니다."
                : "사진·문제·풀이와 필체 참고 2개를 OpenAI로 전송해 손글씨 해설을 만듭니다. 이름·얼굴·연락처 없이 한 문제만 담아주세요. AI 해설에는 오류가 있을 수 있어요.";
        if (!enabled) return new LiveConfig(false, false, false, "live", dailyLimit, 0, 10, "손글씨 풀이를 준비하고 있어요. 준비된 샘플을 먼저 볼 수 있습니다.");
        if (!validOwner(owner) || !ai.ready()) return new LiveConfig(true, false, preview, "live", dailyLimit, 0, 10, "지금은 풀이를 준비 중입니다. 잠시 후 다시 이용해주세요.");
        try {
            var q = ledger.quota(account(owner));
            return new LiveConfig(true, q.available(), preview, "live", dailyLimit, q.remainingToday(), 10,
                    q.available() ? notice : q.remainingToday() == 0 ? "오늘의 풀이 횟수를 모두 사용했어요. 내일 다시 이용해주세요." : "현재 서비스 이용 한도에 도달했어요. 나중에 다시 이용해주세요.");
        } catch (ResponseStatusException e) {return new LiveConfig(true, false, preview, "live", dailyLimit, 0, 10, "지금은 이용 횟수를 확인할 수 없어 요청을 중단했어요. 잠시 후 다시 이용해주세요.");}
    }
    public synchronized void requireReady(Owner owner) {
        LiveConfig config = configuration(owner);
        if (!config.enabled() || !validOwner(owner) || !ai.ready()) throw error(HttpStatus.SERVICE_UNAVAILABLE, config.notice());
        // Re-read persistent counters; capacity and quota are checked again immediately before reserving.
        var q = ledger.quota(account(owner));
        if (!q.available()) throw error(HttpStatus.TOO_MANY_REQUESTS, config.notice());
    }
    public synchronized JobView start(Owner owner, byte[] bytes, int rotation) {
        requireReady(owner); purge();
        if (jobs.size() >= 32) throw error(HttpStatus.SERVICE_UNAVAILABLE, "풀이 결과를 정리 중입니다. 잠시 후 다시 시도해주세요.");
        if (jobs.values().stream().anyMatch(j -> account(j.owner).equals(account(owner)) && !terminal(j.state)))
            throw error(HttpStatus.CONFLICT, "이미 진행 중인 풀이가 있어요. 완료하거나 취소한 뒤 다시 시도해주세요.");
        if (jobs.values().stream().filter(j -> !terminal(j.state)).count() >= 2)
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "다른 풀이를 처리 중입니다. 잠시 후 다시 시도해주세요.");
        byte[] photo = NotebookPhotoCodec.photo(bytes, rotation);
        try {ledger.reserve(account(owner));} catch (RuntimeException e) {Arrays.fill(photo, (byte)0); throw e;}
        var job = new Job(UUID.randomUUID().toString(), owner, clock.instant().plusSeconds(600), photo);
        jobs.put(job.id, job);
        try {job.task = workers.submit(() -> run(job));}
        catch (RejectedExecutionException e) {jobs.remove(job.id); clear(job); throw error(HttpStatus.SERVICE_UNAVAILABLE, "처리 대기열이 가득 차 요청하지 못했어요. 잠시 후 다시 시도해주세요.");}
        return view(job);
    }
    private void run(Job job) {
        try {
            if (!stage(job, State.READING)) return;
            var draft = ai.solve(job.photo);
            synchronized (this) {if (!alive(job)) return; job.question = draft.question();}
            if (!stage(job, State.VERIFYING)) return;
            if (!ai.verifyMath(job.photo, draft)) {
                synchronized (this) {if (alive(job)) {job.state = State.FAILED; job.message = "수식이나 계산을 확실히 확인하지 못했어요. 문제를 선명하게 다시 촬영해주세요.";}}
                return;
            }
            synchronized (this) {if (!alive(job)) return; job.solution = draft.solution();}
            if (!stage(job, State.RENDERING)) return;
            byte[] image = ai.render(draft);
            if (!stage(job, State.CHECKING_IMAGE)) {Arrays.fill(image, (byte)0); return;}
            boolean passed = ai.verifyImage(image, draft);
            synchronized (this) {
                if (!alive(job)) {Arrays.fill(image, (byte)0); return;}
                job.verified = passed; job.image = passed ? image : null;
                if (!passed) Arrays.fill(image, (byte)0);
                job.state = passed ? State.SUCCEEDED : State.REVIEW_REQUIRED;
                job.message = preview ? "로컬 검증용 가짜 결과입니다. 실제 사진을 분석한 해설이 아닙니다."
                        : passed ? "손글씨 해설이 준비됐어요. AI 해설에는 오류가 있을 수 있으니 풀이와 답을 확인해주세요."
                        : "이미지의 수식을 확인하지 못해 풀이 텍스트만 제공합니다.";
            }
        } catch (Exception e) {
            synchronized (this) {if (alive(job)) {
                job.state = job.solution == null ? State.FAILED : State.REVIEW_REQUIRED;
                job.message = job.solution == null ? "풀이를 완성하지 못했어요. 잠시 후 다시 이용해주세요."
                        : "손글씨 이미지를 완성하지 못해 풀이 텍스트를 먼저 제공합니다.";
            }}
        } finally {Arrays.fill(job.photo, (byte)0);}
    }
    public synchronized JobView status(Owner owner, String id) {return view(find(owner, id));}
    public synchronized byte[] image(Owner owner, String id) {
        var job = find(owner, id);
        if (job.state != State.SUCCEEDED || !job.verified || job.image == null) throw error(HttpStatus.CONFLICT, "검토된 이미지가 없습니다.");
        return job.image.clone();
    }
    public synchronized void delete(Owner owner, String id) {var job = find(owner, id); jobs.remove(id); clear(job);}
    private boolean validOwner(Owner o) {return o != null && o.userId() != null && o.academyId() != null
            && ("TEACHER".equals(o.role()) || "STUDENT".equals(o.role()) && o.classId() != null);}
    private static String account(Owner o) {return o.role() + ":" + o.userId();}
    private static boolean terminal(State s) {return s == State.SUCCEEDED || s == State.FAILED || s == State.REVIEW_REQUIRED;}
    private synchronized boolean stage(Job j, State s) {if (!alive(j)) return false; j.state = s; return true;}
    private boolean alive(Job j) {return jobs.get(j.id) == j && clock.instant().isBefore(j.expiresAt);}
    private Job find(Owner o, String id) {purge(); var j = jobs.get(id); if (j == null || !j.owner.equals(o)) throw error(HttpStatus.NOT_FOUND, "작업이 없거나 접근할 수 없습니다. 만료됐다면 다시 시작해주세요."); return j;}
    private JobView view(Job j) {return new JobView(j.id, Kind.SOLUTION, j.state, "live", j.question, List.of(), j.solution, j.verified && j.image != null, j.verified, j.message, j.expiresAt);}
    private synchronized void purge() {var expired = jobs.values().stream().filter(j -> !clock.instant().isBefore(j.expiresAt)).toList(); for (var j : expired) {jobs.remove(j.id); clear(j);}}
    private void clear(Job j) {if (j.task != null) j.task.cancel(true); Arrays.fill(j.photo, (byte)0); if (j.image != null) Arrays.fill(j.image, (byte)0);}
    @PreDestroy public synchronized void close() {for (var j : jobs.values()) clear(j); jobs.clear(); workers.shutdownNow(); cleaner.shutdownNow();}
    private static ResponseStatusException error(HttpStatus status, String message) {return new ResponseStatusException(status, message);}
    private static final class Job {
        final String id; final Owner owner; final Instant expiresAt; final byte[] photo;
        Future<?> task; State state = State.QUEUED; String question = "", message = "풀이를 준비하고 있어요."; Solution solution; byte[] image; boolean verified;
        Job(String id, Owner owner, Instant expiresAt, byte[] photo) {this.id=id; this.owner=owner; this.expiresAt=expiresAt; this.photo=photo;}
    }
}
