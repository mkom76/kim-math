package com.example.service.notebook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class NotebookUsageLedgerTest {
    @TempDir Path root;
    final ObjectMapper mapper = new ObjectMapper();
    NotebookUsageLedger ledger(String time, int personal, int daily, int monthly) {
        return new NotebookUsageLedger(root, mapper, Clock.fixed(Instant.parse(time), ZoneOffset.UTC), personal, daily, monthly);
    }
    @Test void restartCannotResetPersonalQuotaAndNoPlainAccountIsWritten() throws Exception {
        var first = ledger("2026-11-12T00:00:00Z", 2, 20, 200);
        first.reserve("STUDENT:12345"); first.reserve("STUDENT:12345");
        var restarted = ledger("2026-11-12T01:00:00Z", 2, 20, 200);
        assertThat(restarted.quota("STUDENT:12345").remainingToday()).isZero();
        assertThatThrownBy(() -> restarted.reserve("STUDENT:12345")).isInstanceOf(ResponseStatusException.class);
        assertThat(Files.readString(root.resolve("usage.json"))).doesNotContain("STUDENT", "12345", "question", "photo");
        assertThat(restarted.quota("STUDENT:999").available()).isTrue();
    }
    @Test void globalDayAndMonthLimitsApplyAcrossUsersAndKoreaMidnight() {
        var today = ledger("2026-11-12T14:59:00Z", 2, 2, 3);
        today.reserve("a"); today.reserve("b");
        assertThat(today.quota("c").available()).isFalse();
        var tomorrow = ledger("2026-11-12T15:00:00Z", 2, 2, 3);
        tomorrow.reserve("a");
        assertThat(tomorrow.quota("b").available()).isFalse();
        assertThat(ledger("2026-11-30T15:00:00Z", 2, 2, 3).quota("a").available()).isTrue();
    }
    @Test void concurrentReservationsAcrossInstancesNeverExceedSiteLimit() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            var attempts = new ArrayList<Callable<Boolean>>();
            for (int i=0;i<20;i++) {String account = "user"+i; attempts.add(() -> {
                try {ledger("2026-11-12T00:00:00Z",2,5,200).reserve(account); return true;}
                catch (ResponseStatusException e) {assertThat(e.getStatusCode().value()).isEqualTo(429); return false;}
            });}
            int success=0; for (var f:pool.invokeAll(attempts)) if(f.get()) success++;
            assertThat(success).isEqualTo(5);
        } finally {pool.shutdownNow();}
    }
    @Test void missingSaltMalformedOrMissingTotalsFailClosed() throws Exception {
        var ledger = ledger("2026-11-12T00:00:00Z",2,20,200); ledger.reserve("a");
        String original = Files.readString(root.resolve("usage.json"));
        for (String invalid : List.of("{", "{}", original.replace("\"total\":1", "\"invalid\":1"))) {
            Files.writeString(root.resolve("usage.json"),invalid);
            assertThatThrownBy(() -> ledger.reserve("a")).isInstanceOf(ResponseStatusException.class);
            assertThat(Files.readString(root.resolve("usage.json"))).isEqualTo(invalid);
        }
        Files.writeString(root.resolve("usage.json"),original); Files.delete(root.resolve("usage.salt"));
        assertThatThrownBy(() -> ledger.quota("a")).isInstanceOf(ResponseStatusException.class);
        assertThat(Files.exists(root.resolve("usage.salt"))).isFalse();
        Files.write(root.resolve("usage.salt"),new byte[32]); Files.delete(root.resolve("usage.json"));
        assertThatThrownBy(() -> ledger.reserve("a")).isInstanceOf(ResponseStatusException.class);
        assertThat(Files.exists(root.resolve("usage.json"))).isFalse();
    }
    @Test void nextReservationPrunesOldCounters() throws Exception {
        ledger("2026-08-01T00:00:00Z",2,20,200).reserve("a");
        ledger("2026-11-12T00:00:00Z",2,20,200).reserve("b");
        var json = mapper.readTree(Files.readString(root.resolve("usage.json")));
        assertThat(json.path("days").size()).isEqualTo(1);
        assertThat(json.path("months").size()).isEqualTo(1);
    }
}
