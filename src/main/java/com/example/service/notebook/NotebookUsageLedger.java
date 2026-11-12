package com.example.service.notebook;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static java.nio.file.StandardOpenOption.*;

/** Persistent counters only. No photos, questions, solutions, names, or plain account IDs. */
final class NotebookUsageLedger {
    private static final Object JVM_LOCK = new Object();
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private final Path root;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final int personal, daily, monthly;
    record Quota(int remainingToday, boolean available) {}

    NotebookUsageLedger(Path root, ObjectMapper mapper, Clock clock, int personal, int daily, int monthly) {
        this.root = root; this.mapper = mapper; this.clock = clock;
        this.personal = personal; this.daily = daily; this.monthly = monthly;
    }
    Quota quota(String account) {return transaction(account, false);}
    void reserve(String account) {transaction(account, true);}

    private Quota transaction(String account, boolean reserve) {
        if (root == null || !root.isAbsolute()) throw unavailable();
        synchronized (JVM_LOCK) {
            try {
                Files.createDirectories(root);
                try (var channel = FileChannel.open(root.resolve("usage.lock"), CREATE, WRITE); var lock = channel.lock()) {
                    boolean existingSalt = Files.exists(root.resolve("usage.salt"));
                    byte[] salt = salt();
                    String key = key(account, salt);
                    Path file = root.resolve("usage.json");
                    ObjectNode state;
                    if (Files.exists(file)) {
                        if (Files.size(file) < 2 || Files.size(file) > 2_000_000) throw unavailable();
                        JsonNode parsed = mapper.readTree(Files.readAllBytes(file));
                        if (!(parsed instanceof ObjectNode object) || parsed.path("version").asInt(-1) != 1
                                || !parsed.path("days").isObject() || !parsed.path("months").isObject()) throw unavailable();
                        state = object;
                    } else {
                        if (existingSalt) throw unavailable();
                        state = mapper.createObjectNode(); state.put("version", 1); state.putObject("days"); state.putObject("months");
                        // Initialize both files together on first access. Losing one later must fail closed.
                        atomicWrite(file, mapper.writeValueAsBytes(state));
                    }
                    LocalDate day = LocalDate.ofInstant(clock.instant(), ZONE);
                    String date = day.toString(), month = YearMonth.from(day).toString();
                    var days = (ObjectNode)state.get("days"); var months = (ObjectNode)state.get("months");
                    JsonNode today = days.path(date), thisMonth = months.path(month);
                    if (!today.isMissingNode() && (!today.isObject() || !today.path("accounts").isObject() || !today.has("total"))) throw unavailable();
                    if (!thisMonth.isMissingNode() && (!thisMonth.isObject() || !thisMonth.has("total"))) throw unavailable();
                    int used = count(today.path("accounts").path(key));
                    int dayUsed = count(today.path("total")), monthUsed = count(thisMonth.path("total"));
                    if (!today.isMissingNode()) {
                        long sum = 0; for (JsonNode value : today.path("accounts")) sum += count(value);
                        if (sum != dayUsed || monthUsed < dayUsed) throw unavailable();
                    }
                    int remaining = Math.max(0, personal - used);
                    boolean available = remaining > 0 && dayUsed < daily && monthUsed < monthly;
                    if (!reserve) return new Quota(remaining, available);
                    if (!available) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            remaining == 0 ? "오늘 질문 " + personal + "번을 모두 사용했어요. 밤 12시(한국 시간)에 횟수가 초기화되면 다시 질문할 수 있어요." : "현재 풀이 서비스의 이용 한도에 도달했어요. 잠시 후 또는 다음 이용 기간에 다시 시도해주세요.");
                    ObjectNode d = today.isMissingNode() ? days.putObject(date) : (ObjectNode)today;
                    ObjectNode accounts = d.has("accounts") ? (ObjectNode)d.get("accounts") : d.putObject("accounts");
                    accounts.put(key, used + 1); d.put("total", dayUsed + 1);
                    ObjectNode m = thisMonth.isMissingNode() ? months.putObject(month) : (ObjectNode)thisMonth;
                    m.put("total", monthUsed + 1);
                    // Counters roll over in Korea time. Keep at most 35 days and two calendar months.
                    var dayNames = new ArrayList<String>(); days.fieldNames().forEachRemaining(dayNames::add);
                    for (String name : dayNames) if (LocalDate.parse(name).isBefore(day.minusDays(35))) days.remove(name);
                    var monthNames = new ArrayList<String>(); months.fieldNames().forEachRemaining(monthNames::add);
                    for (String name : monthNames) if (YearMonth.parse(name).isBefore(YearMonth.from(day).minusMonths(1))) months.remove(name);
                    atomicWrite(file, mapper.writeValueAsBytes(state));
                    return new Quota(remaining - 1, remaining > 1 && dayUsed + 1 < daily && monthUsed + 1 < monthly);
                }
            } catch (ResponseStatusException e) {throw e;}
            catch (Exception e) {throw unavailable();}
        }
    }
    private byte[] salt() throws Exception {
        Path file = root.resolve("usage.salt");
        if (!Files.exists(file)) {
            // An existing ledger without its salt is not a fresh ledger: never silently reset users' quotas.
            if (Files.exists(root.resolve("usage.json"))) throw unavailable();
            byte[] value = new byte[32]; new SecureRandom().nextBytes(value);
            Files.write(file, value, CREATE_NEW, WRITE);
            try {Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));} catch (UnsupportedOperationException ignored) {}
        }
        byte[] value = Files.readAllBytes(file); if (value.length != 32) throw unavailable(); return value;
    }
    private static int count(JsonNode value) {
        if (value.isMissingNode()) return 0;
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0) throw unavailable();
        return value.asInt();
    }
    private static String key(String account, byte[] salt) throws Exception {
        var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(account.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private void atomicWrite(Path file, byte[] data) throws Exception {
        Path temp = root.resolve("usage-" + UUID.randomUUID() + ".tmp");
        try {
            try (var channel = FileChannel.open(temp, CREATE_NEW, WRITE)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {Files.deleteIfExists(temp);}
    }
    private static ResponseStatusException unavailable() {return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "이용 횟수를 안전하게 확인하지 못해 요청하지 않았어요. 잠시 후 다시 시도해주세요.");}
}
