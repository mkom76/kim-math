package com.example.service.notebook;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static com.example.service.notebook.NotebookTypes.*;

/** Mock-only workflow. Deliberately has no HTTP client, API key, repository, or disk upload path. */
@Service
public class SolutionNotebookService {
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_JOBS = 128, MAX_USAGE_ENTRIES = 4096;
    private final boolean enabled;
    private final String mode;
    private final Clock clock;
    private final Consumer<Runnable> execute;
    private final long delayMillis;
    private final byte[] image;
    private final Map<String, Job> jobs = new HashMap<>();
    private final Map<Account, Usage> usage = new HashMap<>();
    private ExecutorService workers;
    private ScheduledExecutorService cleaner;

    @Autowired
    public SolutionNotebookService(@Value("${app.solution-notebook.enabled:false}") boolean enabled,
            @Value("${app.solution-notebook.mode:mock}") String mode,
            @Value("${app.solution-notebook.mock-step-delay-ms:250}") long delay,
            ResourceLoader resources) throws IOException {
        this.enabled = enabled;
        this.mode = mode;
        this.clock = Clock.systemUTC();
        this.delayMillis = Math.min(Math.max(delay, 0), 2000);
        try (var stream = resources.getResource("classpath:notebook/mock-solution.png").getInputStream()) {
            this.image = stream.readAllBytes();
        }
        workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8), runnable -> {
                    Thread t = new Thread(runnable, "notebook-mock-worker"); t.setDaemon(true); return t;
                }, new ThreadPoolExecutor.AbortPolicy());
        execute = workers::execute;
        cleaner = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "notebook-mock-cleaner"); t.setDaemon(true); return t;
        });
        cleaner.scheduleWithFixedDelay(this::purge, 5, 5, TimeUnit.SECONDS);
    }

    // Deterministic clock/executor for isolation, timeout, quota and cancellation tests.
    SolutionNotebookService(boolean enabled, String mode, byte[] image, Clock clock, Consumer<Runnable> execute) {
        this.enabled=enabled; this.mode=mode; this.image=image.clone(); this.clock=clock;
        this.execute=execute; this.delayMillis=0;
    }

    public Configuration configuration() {
        return new Configuration(enabled && "mock".equals(mode), "mock", FIXTURE, QUESTION, 3, 10, 10,
                "모의 API입니다. 선택한 사진은 서버에 보내지 않으며, 고정된 예제와 사전 제작 이미지를 반환합니다. 외부 AI 호출·과금은 없습니다.");
    }

    public JobView read(Owner owner, ReadRequest request) {
        requireEnabled();
        if (request == null || !FIXTURE.equals(request.fixture())) throw error(HttpStatus.BAD_REQUEST, "지원하는 모의 예제를 선택해주세요.");
        Scenario scenario = scenario(request.scenario());
        Job job = create(owner, Kind.READ, scenario);
        submit(job, () -> {
            if (!stage(job, State.READING)) return;
            if (scenario == Scenario.UNREADABLE) { fail(job, "모의 판독 실패: 수식이 선명한 사진으로 다시 시도해주세요."); return; }
            if (scenario == Scenario.TIMEOUT) { fail(job, "모의 시간 초과: 요청을 다시 시도해주세요."); return; }
            synchronized (this) {
                if (!alive(job)) return;
                job.question=QUESTION;
                job.message="모의 판독 완료입니다. 선택한 사진을 읽은 결과가 아니에요. 예제의 수식과 조건을 확인해주세요.";
                job.state=State.SUCCEEDED;
            }
        });
        return view(job);
    }

    public JobView solve(Owner owner, SolveRequest request) {
        requireEnabled();
        if (request == null || !request.confirmed()) throw error(HttpStatus.BAD_REQUEST, "문제 내용 확인에 체크해주세요.");
        if (request.question() == null || request.question().length()>6000 || !normalize(QUESTION).equals(normalize(request.question())))
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "모의 API는 준비된 예제 문제만 지원합니다. 새 문제를 실제로 푼 결과를 만들지는 않습니다.");
        if (!Set.of("중학생","고1","고2","고3").contains(Objects.toString(request.grade(),""))
                || !Set.of("standard","detailed").contains(Objects.toString(request.depth(),"")))
            throw error(HttpStatus.BAD_REQUEST, "학년과 설명 깊이를 확인해주세요.");
        synchronized (this) {
            Job read = find(owner, request.readJobId());
            if (read.kind != Kind.READ || read.state != State.SUCCEEDED) throw error(HttpStatus.CONFLICT, "문제 읽기를 먼저 완료해주세요.");
        }
        Scenario scenario=scenario(request.scenario());
        Job job=create(owner,Kind.SOLUTION,scenario);
        job.question=request.question();
        submit(job, () -> {
            if (!stage(job,State.SOLVING) || !stage(job,State.VERIFYING)) return;
            if (scenario==Scenario.TIMEOUT) { fail(job,"모의 시간 초과: 잠시 뒤 다시 시도해주세요."); return; }
            if (scenario==Scenario.MATH_FAILED) { fail(job,"모의 풀이 검토 실패: 조건을 확인한 뒤 다시 시도해주세요."); return; }
            synchronized (this) {
                if (!alive(job)) return;
                job.solution=new Solution("유리함수의 점근선", request.depth().equals("detailed")
                        ? List.of("분자를 2(x−1)+3으로 바꾸면 분모 x−1과 나누어지는 부분이 보여요.","f(x)=2+3/(x−1)이고 정의역은 x≠1이에요.","점근선은 x=1, y=2예요. 그래프의 두 절편은 (−1/2,0), (0,−1)이에요.","두 점근선의 교점은 (1,2)예요.")
                        : List.of("f(x)=(2x+1)/(x−1)=2+3/(x−1), x≠1", "점근선: x=1, y=2", "두 점근선의 교점: (1,2)"), "(1, 2)", "점근선 x=1, y=2인 유리함수 그래프");
            }
            if (!stage(job,State.RENDERING) || !stage(job,State.CHECKING_IMAGE)) return;
            synchronized (this) {
                if (!alive(job)) return;
                job.verified=scenario!=Scenario.IMAGE_FAILED;
                job.state=job.verified?State.SUCCEEDED:State.REVIEW_REQUIRED;
                job.message=job.verified?"모의 처리 완료: 예제용 사전 제작 이미지입니다. 새 사진으로 생성한 결과가 아닙니다."
                        :"모의 이미지 검토 실패: 부호를 확인해야 하므로 이미지 제공을 중단했습니다. 풀이 텍스트를 확인해주세요.";
            }
        });
        return view(job);
    }

    public synchronized JobView status(Owner owner,String id) { requireEnabled(); return view(find(owner,id)); }
    public synchronized byte[] image(Owner owner,String id) {
        requireEnabled(); Job job=find(owner,id);
        if (job.kind!=Kind.SOLUTION || job.state!=State.SUCCEEDED || !job.verified)
            throw error(HttpStatus.CONFLICT,"검토를 마친 해설 이미지가 아직 없습니다.");
        return image.clone();
    }
    public synchronized void delete(Owner owner,String id) { requireEnabled(); Job job=find(owner,id); jobs.remove(job.id); }

    private synchronized Job create(Owner owner,Kind kind,Scenario scenario) {
        purge();
        if (scenario==Scenario.RATE_LIMIT) throw error(HttpStatus.TOO_MANY_REQUESTS,"모의 사용량 초과: 나중에 다시 시도해주세요.");
        if (jobs.size()>=MAX_JOBS) throw error(HttpStatus.SERVICE_UNAVAILABLE,"보관 가능한 작업 수를 초과했습니다. 잠시 뒤 다시 시도해주세요.");
        // One in-flight request across sessions/classes for the same account.
        Account account=account(owner);
        if (jobs.values().stream().anyMatch(j->account(j.owner).equals(account)&&!terminal(j.state)))
            throw error(HttpStatus.CONFLICT,"진행 중인 작업이 있습니다. 완료하거나 취소한 뒤 시도해주세요.");
        LocalDate day=LocalDate.ofInstant(clock.instant(),ZONE);
        if (!usage.containsKey(account) && usage.size()>=MAX_USAGE_ENTRIES) throw error(HttpStatus.SERVICE_UNAVAILABLE,"잠시 뒤 다시 시도해주세요.");
        Usage u=usage.computeIfAbsent(account,k->new Usage(day));
        if (kind==Kind.SOLUTION && u.solutions>=3 || kind==Kind.READ && u.reads>=10)
            throw error(HttpStatus.TOO_MANY_REQUESTS,"오늘의 모의 체험 횟수를 모두 사용했습니다. 모의 풀이는 하루 3회입니다.");
        if (kind==Kind.READ) u.reads++; else u.solutions++;
        Job job=new Job(UUID.randomUUID().toString(),owner,kind,clock.instant().plus(TTL)); jobs.put(job.id,job);return job;
    }
    private void submit(Job job,Runnable action) {
        try { execute.accept(()->{try { action.run(); } catch (Exception e) { fail(job,"모의 처리 중 문제가 발생했습니다. 다시 시도해주세요."); }}); }
        catch (RejectedExecutionException e) { synchronized(this){jobs.remove(job.id);} throw error(HttpStatus.SERVICE_UNAVAILABLE,"처리 대기열이 가득 찼습니다. 잠시 뒤 다시 시도해주세요."); }
    }
    private boolean stage(Job job,State state) {
        synchronized(this) { if(!alive(job)) return false; job.state=state; }
        if (delayMillis>0) {try {Thread.sleep(delayMillis);}catch(InterruptedException e){Thread.currentThread().interrupt(); fail(job,"작업이 중단되었습니다.");return false;}}
        synchronized(this) {return alive(job);}
    }
    private synchronized void fail(Job job,String message) {if(alive(job)){job.state=State.FAILED;job.message=message;}}
    private boolean alive(Job job) {return jobs.get(job.id)==job && clock.instant().isBefore(job.expiresAt);}
    private Job find(Owner owner,String id) {
        purge();Job job=jobs.get(id);
        if(job==null || !job.owner.equals(owner)) throw error(HttpStatus.NOT_FOUND,"작업이 없거나 접근할 수 없습니다. 만료된 경우 다시 시작해주세요.");
        return job;
    }
    private synchronized JobView view(Job j) {return new JobView(j.id,j.kind,j.state,"mock",j.question,List.of(),j.solution,
            j.kind==Kind.SOLUTION&&j.state==State.SUCCEEDED&&j.verified,j.verified,j.message,j.expiresAt);}
    private synchronized void purge() {
        Instant now=clock.instant();jobs.values().removeIf(j->!now.isBefore(j.expiresAt));
        LocalDate day=LocalDate.ofInstant(now,ZONE);usage.values().removeIf(u->!u.day.equals(day));
    }
    private void requireEnabled() {if(!enabled || !"mock".equals(mode))throw error(HttpStatus.SERVICE_UNAVAILABLE,"Q&A 모의 API가 비활성 상태입니다. 실제 AI 연결은 아직 지원하지 않습니다.");}
    private static Scenario scenario(Scenario value) {return value==null?Scenario.SUCCESS:value;}
    private static String normalize(String s) {return s.replaceAll("\\s+","").replace('−','-');}
    private static boolean terminal(State s) {return s==State.SUCCEEDED||s==State.FAILED||s==State.REVIEW_REQUIRED;}
    private static Account account(Owner o) {return new Account(o.role(),o.userId());}
    private static ResponseStatusException error(HttpStatus s,String m) {return new ResponseStatusException(s,m);}
    @PreDestroy public void close() {if(workers!=null)workers.shutdownNow();if(cleaner!=null)cleaner.shutdownNow();synchronized(this){jobs.clear();usage.clear();}}
    private record Account(String role,Long userId) {}
    private static class Usage {final LocalDate day;int reads,solutions;Usage(LocalDate day){this.day=day;}}
    private static class Job {
        final String id;final Owner owner;final Kind kind;final Instant expiresAt;
        State state=State.QUEUED;String question="",message="모의 작업 대기 중";Solution solution;boolean verified;
        Job(String id,Owner owner,Kind kind,Instant expiresAt){this.id=id;this.owner=owner;this.kind=kind;this.expiresAt=expiresAt;}
    }
}
