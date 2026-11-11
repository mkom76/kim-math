package com.example.service.notebook;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static com.example.service.notebook.NotebookTypes.*;
import static org.assertj.core.api.Assertions.*;

class SolutionNotebookServiceTest {
    private static Owner owner(long id) {return new Owner("STUDENT",id,1L,2L,"session-"+id,"null");}
    private static ReadRequest readRequest(Scenario s){return new ReadRequest(FIXTURE,s);}
    private static SolveRequest solveRequest(String id,Scenario s){return new SolveRequest(id,QUESTION,true,"고1","standard",s);}
    private static void statusCode(Runnable action,int code){assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(code));}
    private static class MutableClock extends Clock {
        Instant now=Instant.parse("2026-11-11T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    private SolutionNotebookService service(MutableClock c){return new SolutionNotebookService(true,"mock",new byte[]{1,2,3},c,Runnable::run);}

    @Test void disabledAndUnknownLiveModeCannotExecuteEvenWhenEnabled() {
        var c=new MutableClock();
        for(var service:List.of(new SolutionNotebookService(false,"mock",new byte[0],c,Runnable::run),new SolutionNotebookService(true,"live",new byte[0],c,Runnable::run))) {
            assertThat(service.configuration().enabled()).isFalse();
            statusCode(()->service.read(owner(1),readRequest(Scenario.SUCCESS)),503);
        }
    }
    @Test void mockWorkflowReturnsExplicitFixtureAndVerifiedImage() {
        var s=service(new MutableClock());var o=owner(1);
        var read=s.read(o,readRequest(Scenario.SUCCESS));
        assertThat(read.state()).isEqualTo(State.SUCCEEDED);assertThat(read.mode()).isEqualTo("mock");assertThat(read.question()).isEqualTo(QUESTION);
        var job=s.solve(o,solveRequest(read.id(),Scenario.SUCCESS));
        assertThat(job.state()).isEqualTo(State.SUCCEEDED);assertThat(job.imageAvailable()).isTrue();assertThat(job.solution().answer()).isEqualTo("(1, 2)");
        byte[] first=s.image(o,job.id());first[0]=9;assertThat(s.image(o,job.id())).containsExactly(1,2,3);
    }
    @Test void otherUsersRolesAcademiesClassesAndSessionsCannotReadDeleteOrDownload() {
        var s=service(new MutableClock());var o=owner(1);var read=s.read(o,readRequest(Scenario.SUCCESS));var solved=s.solve(o,solveRequest(read.id(),Scenario.SUCCESS));
        for(var other:List.of(owner(2),new Owner("TEACHER",1L,1L,null,"session-1","TEACHER"),
                new Owner("STUDENT",1L,9L,2L,"session-1","null"),new Owner("STUDENT",1L,1L,3L,"session-1","null"),
                new Owner("STUDENT",1L,1L,2L,"another-session","null"))) {
            statusCode(()->s.status(other,solved.id()),404);statusCode(()->s.image(other,solved.id()),404);statusCode(()->s.delete(other,solved.id()),404);
            statusCode(()->s.solve(other,solveRequest(read.id(),Scenario.SUCCESS)),404);
        }
    }
    @Test void missingConfirmationAndChangedProblemCannotReceiveTheFixtureAnswer() {
        var s=service(new MutableClock());var o=owner(1);var read=s.read(o,readRequest(Scenario.SUCCESS));
        statusCode(()->s.solve(o,new SolveRequest(read.id(),QUESTION,false,"고1","standard",Scenario.SUCCESS)),400);
        statusCode(()->s.solve(o,new SolveRequest(read.id(),"1+1은 무엇인가요?",true,"고1","standard",Scenario.SUCCESS)),422);
        statusCode(()->s.solve(o,new SolveRequest("missing",QUESTION,true,"고1","standard",Scenario.SUCCESS)),404);
    }
    @Test void failuresDoNotExposeUnverifiedImages() {
        for(var scenario:List.of(Scenario.MATH_FAILED,Scenario.IMAGE_FAILED,Scenario.TIMEOUT)) {
            var s=service(new MutableClock());var o=owner(1);var read=s.read(o,readRequest(Scenario.SUCCESS));var job=s.solve(o,solveRequest(read.id(),scenario));
            assertThat(job.state()).isIn(State.FAILED,State.REVIEW_REQUIRED);assertThat(job.verified()).isFalse();assertThat(job.imageAvailable()).isFalse();statusCode(()->s.image(o,job.id()),409);
        }
        for(var scenario:List.of(Scenario.UNREADABLE,Scenario.TIMEOUT)) {
            var s=service(new MutableClock());assertThat(s.read(owner(1),readRequest(scenario)).state()).isEqualTo(State.FAILED);
        }
        statusCode(()->service(new MutableClock()).read(owner(1),readRequest(Scenario.RATE_LIMIT)),429);
    }
    @Test void quotaCountsAttemptsAndResetsOnTheNextKoreanDay() {
        var c=new MutableClock();var s=service(c);var o=owner(1);var read=s.read(o,readRequest(Scenario.SUCCESS));
        for(int i=0;i<3;i++)s.solve(o,solveRequest(read.id(),Scenario.MATH_FAILED));
        statusCode(()->s.solve(o,solveRequest(read.id(),Scenario.SUCCESS)),429);
        c.now=c.now.plus(Duration.ofDays(1));var next=s.read(o,readRequest(Scenario.SUCCESS));assertThat(s.solve(o,solveRequest(next.id(),Scenario.SUCCESS)).state()).isEqualTo(State.SUCCEEDED);
    }
    @Test void expiryAndRestartRemoveResults() {
        var c=new MutableClock();var s=service(c);var o=owner(1);var read=s.read(o,readRequest(Scenario.SUCCESS));var job=s.solve(o,solveRequest(read.id(),Scenario.SUCCESS));
        c.now=c.now.plus(Duration.ofMinutes(10));statusCode(()->s.status(o,job.id()),404);statusCode(()->s.image(o,job.id()),404);
        statusCode(()->service(c).status(o,job.id()),404);
    }
    @Test void cancellingQueuedWorkPreventsResurrectionAndDuplicateSubmitIsRejected() {
        var queue=new ArrayList<Runnable>();var s=new SolutionNotebookService(true,"mock",new byte[]{1},new MutableClock(),queue::add);var o=owner(1);
        var read=s.read(o,readRequest(Scenario.SUCCESS));assertThat(read.state()).isEqualTo(State.QUEUED);
        statusCode(()->s.read(o,readRequest(Scenario.SUCCESS)),409);
        s.delete(o,read.id());queue.getFirst().run();statusCode(()->s.status(o,read.id()),404);
        assertThat(s.read(o,readRequest(Scenario.SUCCESS)).state()).isEqualTo(State.QUEUED);
    }
    @Test void fullWorkerQueueFailsClosed() {
        var s=new SolutionNotebookService(true,"mock",new byte[]{1},new MutableClock(),r->{throw new java.util.concurrent.RejectedExecutionException();});
        statusCode(()->s.read(owner(1),readRequest(Scenario.SUCCESS)),503);
    }
}
