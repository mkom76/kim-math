package com.example.service.notebook;

import com.example.service.notebook.NotebookTypes.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static com.example.service.notebook.NotebookPhotoTrialServiceTest.photo;

class HandwrittenSolutionServiceTest {
    @TempDir Path root;
    static final Owner OWNER = new Owner("STUDENT",1L,1L,1L,"session","null");
    HandwrittenSolutionService service(NotebookPhotoTrialServiceTest.FakeAi ai) {
        return new HandwrittenSolutionService(ai,new ObjectMapper(),true,false,root.toString(),2,20,200,Clock.systemUTC());
    }
    JobView complete(HandwrittenSolutionService s, String id) throws Exception {
        for(int i=0;i<300;i++){var j=s.status(OWNER,id);if(Set.of(State.SUCCEEDED,State.FAILED,State.REVIEW_REQUIRED).contains(j.state()))return j;Thread.sleep(10);}
        throw new AssertionError("not completed");
    }
    @Test void defaultLimitAllowsThreePhotosThenRejectsFourthWithoutAnotherAiCall() throws Exception {
        var ai=new NotebookPhotoTrialServiceTest.FakeAi();
        var env=new org.springframework.mock.env.MockEnvironment()
                .withProperty("app.solution-notebook.live.enabled","true")
                .withProperty("app.solution-notebook.live.usage-dir",root.toString());
        var s=new HandwrittenSolutionService(ai,new ObjectMapper(),env);
        try {
            assertThat(s.configuration(OWNER).dailyLimit()).isEqualTo(3);
            for(int remaining=2;remaining>=0;remaining--) {
                var j=complete(s,s.start(OWNER,photo(),0).id());
                assertThat(j.state()).isEqualTo(State.SUCCEEDED);
                assertThat(s.configuration(OWNER).remainingToday()).isEqualTo(remaining);
                s.delete(OWNER,j.id());
            }
            assertThatThrownBy(()->s.start(OWNER,photo(),0)).isInstanceOfSatisfying(ResponseStatusException.class,
                    e->assertThat(e.getStatusCode().value()).isEqualTo(429));
            assertThat(Collections.frequency(ai.calls,"solve")).isEqualTo(3);
            assertThat(Collections.frequency(ai.calls,"render")).isEqualTo(3);
        }finally{s.close();}
        var restarted=new HandwrittenSolutionService(ai,new ObjectMapper(),env);
        try{assertThat(restarted.configuration(OWNER).remainingToday()).isZero();assertThat(restarted.configuration(OWNER).ready()).isFalse();}finally{restarted.close();}
    }
    @Test void verifiedImageIsBoundToExactOwnerAndDeletingDoesNotRefundQuota() throws Exception {
        var ai=new NotebookPhotoTrialServiceTest.FakeAi(); var s=service(ai);
        try {
            var j=complete(s,s.start(OWNER,photo(),90).id());
            assertThat(j.state()).isEqualTo(State.SUCCEEDED); assertThat(s.image(OWNER,j.id())).isNotEmpty();
            for(var other:List.of(new Owner("STUDENT",2L,1L,1L,"session","null"),new Owner("STUDENT",1L,2L,1L,"session","null"),new Owner("STUDENT",1L,1L,2L,"session","null"),new Owner("STUDENT",1L,1L,1L,"other","null"),new Owner("TEACHER",1L,1L,null,"session","TEACHER"))) {
                assertThatThrownBy(()->s.status(other,j.id())).isInstanceOf(ResponseStatusException.class);
                assertThatThrownBy(()->s.image(other,j.id())).isInstanceOf(ResponseStatusException.class);
                assertThatThrownBy(()->s.delete(other,j.id())).isInstanceOf(ResponseStatusException.class);
            }
            s.delete(OWNER,j.id()); assertThat(s.configuration(OWNER).remainingToday()).isEqualTo(1);
            complete(s,s.start(OWNER,photo(),0).id());
            assertThatThrownBy(()->s.start(OWNER,photo(),0)).isInstanceOf(ResponseStatusException.class);
        } finally {s.close();}
        var restarted=service(ai);try{assertThat(restarted.configuration(OWNER).ready()).isFalse();}finally{restarted.close();}
    }
    @Test void failedMathNeverRendersAndUnverifiedImageIsNeverServed() throws Exception {
        for(boolean math:List.of(false,true)) {
            var ai=new NotebookPhotoTrialServiceTest.FakeAi();ai.math=math;ai.image=false;
            var s=service(ai);try {
                var j=complete(s,s.start(OWNER,photo(),0).id());
                assertThat(j.state()).isEqualTo(math?State.REVIEW_REQUIRED:State.FAILED);
                assertThat(j.imageAvailable()).isFalse();assertThat(j.solution()!=null).isEqualTo(math);
                assertThat(ai.calls.size()).isEqualTo(math?4:2);
                assertThatThrownBy(()->s.image(OWNER,j.id())).isInstanceOf(ResponseStatusException.class);
            } finally {s.close();}
        }
    }
    @Test void invalidPhotoMissingKeyAndDisabledFlagMakeNoAiCalls() throws Exception {
        var ai=new NotebookPhotoTrialServiceTest.FakeAi();var s=service(ai);try {
            assertThatThrownBy(()->s.start(OWNER,new byte[]{1,2},0)).isInstanceOf(ResponseStatusException.class);
            assertThat(s.configuration(OWNER).remainingToday()).isEqualTo(2);
            ai.ready=false;assertThatThrownBy(()->s.start(OWNER,photo(),0)).isInstanceOf(ResponseStatusException.class);
            assertThat(ai.calls).isEmpty();
        }finally{s.close();}
        var off=new HandwrittenSolutionService(ai,new ObjectMapper(),new org.springframework.mock.env.MockEnvironment());
        try{assertThat(off.configuration(OWNER).enabled()).isFalse();assertThatThrownBy(()->off.start(OWNER,photo(),0)).isInstanceOf(ResponseStatusException.class);}finally{off.close();}
    }
    @Test void cancelStopsLaterPaidStagesAndAccountCannotStartAcrossSessions() throws Exception {
        var ai=new NotebookPhotoTrialServiceTest.FakeAi();ai.entered=new CountDownLatch(1);ai.release=new CountDownLatch(1);var s=service(ai);
        try {
            var j=s.start(OWNER,photo(),0);assertThat(ai.entered.await(2,TimeUnit.SECONDS)).isTrue();
            var otherSession=new Owner("STUDENT",1L,1L,1L,"another","null");
            assertThatThrownBy(()->s.start(otherSession,photo(),0)).isInstanceOf(ResponseStatusException.class);
            s.delete(OWNER,j.id());ai.release.countDown();
            assertThatThrownBy(()->s.status(OWNER,j.id())).isInstanceOf(ResponseStatusException.class);
            assertThat(s.configuration(OWNER).remainingToday()).isEqualTo(1);
            assertThat(ai.calls).containsExactly("solve");
        }finally{s.close();}
    }
    @Test void providerFailureIsNotRetriedAndNeverLeaksRawMessage() throws Exception {
        var ai=new NotebookPhotoTrialServiceTest.FakeAi();ai.renderFail=true;var s=service(ai);
        try{var j=complete(s,s.start(OWNER,photo(),0).id());assertThat(j.state()).isEqualTo(State.REVIEW_REQUIRED);assertThat(j.solution()).isNotNull();assertThat(j.message()).doesNotContain("private");assertThat(ai.calls).containsExactly("solve","math","render");}finally{s.close();}
    }
    @Test void productionCannotEnableLocalPreview() {
        var env=new org.springframework.mock.env.MockEnvironment().withProperty("app.solution-notebook.live.enabled","true").withProperty("app.solution-notebook.live.preview","true");env.setActiveProfiles("local","prod");
        var s=new HandwrittenSolutionService(new NotebookPhotoTrialServiceTest.FakeAi(),new ObjectMapper(),env);
        try{assertThat(s.configuration(OWNER).enabled()).isFalse();}finally{s.close();}
    }
    @Test void expiredResultCannotBeReadAndExpiryDoesNotRefundQuota() throws Exception {
        var instant=new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-11-12T00:00:00Z"));
        Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return instant.get();}};
        var ai=new NotebookPhotoTrialServiceTest.FakeAi();
        var s=new HandwrittenSolutionService(ai,new ObjectMapper(),true,false,root.toString(),2,20,200,clock);
        try {
            var j=complete(s,s.start(OWNER,photo(),0).id());
            instant.set(instant.get().plusSeconds(601));
            assertThatThrownBy(()->s.status(OWNER,j.id())).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(()->s.image(OWNER,j.id())).isInstanceOf(ResponseStatusException.class);
            assertThat(s.configuration(OWNER).remainingToday()).isEqualTo(1);
        }finally{s.close();}
    }
    @Test void siteAllowsOnlyTwoConcurrentPaidPipelines() throws Exception {
        var ai=new NotebookPhotoTrialServiceTest.FakeAi();ai.entered=new CountDownLatch(2);ai.release=new CountDownLatch(1);var s=service(ai);
        var second=new Owner("STUDENT",2L,1L,1L,"two","null");var third=new Owner("STUDENT",3L,1L,1L,"three","null");
        try {
            s.start(OWNER,photo(),0);s.start(second,photo(),0);assertThat(ai.entered.await(2,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->s.start(third,photo(),0)).isInstanceOf(ResponseStatusException.class);
            assertThat(s.configuration(third).remainingToday()).isEqualTo(2);
            assertThat(ai.calls).containsExactly("solve","solve");
        }finally{s.close();ai.release.countDown();}
    }
}
