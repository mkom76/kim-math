package com.example.service.notebook;

import com.example.service.notebook.NotebookTypes.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class NotebookPhotoTrialServiceTest {
    @TempDir Path temp;
    private static final Owner OWNER = new Owner("STUDENT",1L,1L,1L,"session","null");
    static byte[] photo() throws IOException {var out = new ByteArrayOutputStream(); javax.imageio.ImageIO.write(new BufferedImage(40,20,BufferedImage.TYPE_INT_RGB),"png",out); return out.toByteArray();}
    static class FakeAi implements NotebookPhotoAi {
        boolean ready=true, math=true, image=true, fail=false, renderFail=false; final List<String> calls=new CopyOnWriteArrayList<>(); CountDownLatch entered, release;
        public boolean ready(){return ready;}
        public Draft solve(byte[] p){calls.add("solve"); if(entered!=null){entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException();}}if(fail)throw new IllegalStateException("private upstream body");return new Draft("1+1?",new Solution("더하기",List.of("1+1=2"),"2",""));}
        public boolean verifyMath(byte[] p,Draft d){calls.add("math");return math;}
        public byte[] render(Draft d){calls.add("render");if(renderFail)throw new IllegalStateException("private body");try{return photo();}catch(IOException e){throw new IllegalStateException();}}
        public boolean verifyImage(byte[] p,Draft d){calls.add("image");return image;}
    }
    private NotebookPhotoTrialService service(FakeAi ai){return new NotebookPhotoTrialService(ai,true,"STUDENT:1:1:1",temp.resolve("paid.used").toString(),Clock.systemUTC());}
    private static JobView complete(NotebookPhotoTrialService s,String id) throws Exception {
        for(int i=0;i<300;i++){var j=s.status(OWNER,id);if(Set.of(State.SUCCEEDED,State.FAILED,State.REVIEW_REQUIRED).contains(j.state()))return j;Thread.sleep(10);}throw new AssertionError("trial did not finish");
    }
    @Test void successIsOwnerBoundAndTheOneAttemptPersistsAcrossRestartAndDelete() throws Exception {
        var ai=new FakeAi();String id;
        try(var closeable=new Trial(service(ai))){var s=closeable.s;id=s.start(OWNER,photo(),90).id();var result=complete(s,id);
            assertThat(result.mode()).isEqualTo("live");assertThat(result.state()).isEqualTo(State.SUCCEEDED);
            assertThat(ai.calls).containsExactly("solve","math","render","image");assertThat(s.image(OWNER,id)).isNotEmpty();
            for(var other:List.of(new Owner("STUDENT",2L,1L,1L,"session","null"),new Owner("STUDENT",1L,2L,1L,"session","null"),new Owner("STUDENT",1L,1L,2L,"session","null"),new Owner("STUDENT",1L,1L,1L,"other","null"),new Owner("TEACHER",1L,1L,null,"session","TEACHER"))){
                assertThatThrownBy(()->s.status(other,id)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->s.image(other,id)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->s.delete(other,id)).isInstanceOf(ResponseStatusException.class);
            }
            s.delete(OWNER,id);assertThat(s.configuration(OWNER).used()).isTrue();assertThatThrownBy(()->s.start(OWNER,photo(),0)).isInstanceOf(ResponseStatusException.class);
        }
        try(var t=new Trial(service(new FakeAi()))){assertThat(t.s.configuration(OWNER).ready()).isFalse();assertThat(t.s.configuration(OWNER).used()).isTrue();}
    }
    @Test void invalidPhotoAndUnconfiguredKeyNeverConsumeTrial() throws Exception {
        var ai=new FakeAi();try(var t=new Trial(service(ai))){
            assertThatThrownBy(()->t.s.start(OWNER,new byte[]{1,2,3},0)).isInstanceOf(ResponseStatusException.class);assertThat(t.s.configuration(OWNER).ready()).isTrue();
            ai.ready=false;assertThatThrownBy(()->t.s.start(OWNER,photo(),0)).isInstanceOf(ResponseStatusException.class);assertThat(ai.calls).isEmpty();assertThat(Files.exists(temp.resolve("paid.used"))).isFalse();
        }
    }
    @Test void mathFailureStopsBeforePaidImageAndImageFailureHidesImage() throws Exception {
        for(boolean math:List.of(false,true)){
            Files.deleteIfExists(temp.resolve("paid.used"));var ai=new FakeAi();ai.math=math;ai.image=false;
            try(var t=new Trial(service(ai))){var j=complete(t.s,t.s.start(OWNER,photo(),0).id());assertThat(j.state()).isEqualTo(math?State.REVIEW_REQUIRED:State.FAILED);assertThat(j.imageAvailable()).isFalse();assertThatThrownBy(()->t.s.image(OWNER,j.id())).isInstanceOf(ResponseStatusException.class);
                assertThat(ai.calls.size()).isEqualTo(math?4:2);if(math)assertThat(j.solution()).isNotNull();assertThat(t.s.configuration(OWNER).used()).isTrue();}
        }
    }
    @Test void upstreamFailureDoesNotRetryOrExposeProviderBody() throws Exception {
        var ai=new FakeAi();ai.fail=true;try(var t=new Trial(service(ai))){var j=complete(t.s,t.s.start(OWNER,photo(),0).id());assertThat(j.state()).isEqualTo(State.FAILED);assertThat(j.message()).doesNotContain("private");assertThat(ai.calls).containsExactly("solve");}
    }
    @Test void cancellationStopsSubsequentPaidStages() throws Exception {
        var ai=new FakeAi();ai.entered=new CountDownLatch(1);ai.release=new CountDownLatch(1);
        try(var t=new Trial(service(ai))){var j=t.s.start(OWNER,photo(),0);assertThat(ai.entered.await(2,TimeUnit.SECONDS)).isTrue();t.s.delete(OWNER,j.id());ai.release.countDown();assertThatThrownBy(()->t.s.status(OWNER,j.id())).isInstanceOf(ResponseStatusException.class);assertThat(ai.calls).containsExactly("solve");assertThat(t.s.configuration(OWNER).used()).isTrue();}
    }
    @Test void codecBoundsInputAndNormalizesPixels() throws Exception {
        byte[] normalized=NotebookPhotoCodec.photo(photo(),90);var image=javax.imageio.ImageIO.read(new ByteArrayInputStream(normalized));assertThat(image.getWidth()).isEqualTo(20);assertThat(image.getHeight()).isEqualTo(40);
        assertThatThrownBy(()->NotebookPhotoCodec.photo(new byte[NotebookPhotoCodec.MAX_BYTES+1],0)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->NotebookPhotoCodec.photo(photo(),45)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void imageApiFailurePreservesReviewedTextAndDoesNotRetry() throws Exception {
        var ai=new FakeAi();ai.renderFail=true;try(var t=new Trial(service(ai))){var j=complete(t.s,t.s.start(OWNER,photo(),0).id());assertThat(j.state()).isEqualTo(State.REVIEW_REQUIRED);assertThat(j.solution().answer()).isEqualTo("2");assertThat(j.imageAvailable()).isFalse();assertThat(j.message()).doesNotContain("private");assertThat(ai.calls).containsExactly("solve","math","render");}
    }
    @Test void productionProfileOrNonLoopbackBindCannotEnableTrial() {
        for(String profile:List.of("prod","test")){
            var env=new org.springframework.mock.env.MockEnvironment().withProperty("app.solution-notebook.trial.enabled","true").withProperty("server.address","127.0.0.1").withProperty("app.solution-notebook.trial.allowed-account","STUDENT:1:1:1").withProperty("app.solution-notebook.trial.marker-file",temp.resolve("profile.used").toString());env.setActiveProfiles(profile);
            try(var t=new Trial(new NotebookPhotoTrialService(new FakeAi(),env))){assertThat(t.s.configuration(OWNER).ready()).isFalse();}
        }
        var env=new org.springframework.mock.env.MockEnvironment().withProperty("app.solution-notebook.trial.enabled","true").withProperty("server.address","0.0.0.0");env.setActiveProfiles("local");try(var t=new Trial(new NotebookPhotoTrialService(new FakeAi(),env))){assertThat(t.s.configuration(OWNER).ready()).isFalse();}
    }
    private record Trial(NotebookPhotoTrialService s) implements AutoCloseable {public void close(){s.close();}}
}
