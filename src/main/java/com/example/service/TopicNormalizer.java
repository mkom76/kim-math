package com.example.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits free-form topic input ("함수 / 일차함수 / 그래프", "함수 > 일차함수",
 * "함수 › 일차함수") into up to {@link #MAX_LEVELS} cleaned segments and rejoins
 * them with the canonical {@link #SEPARATOR} (U+203A "›") used everywhere in
 * storage and display.
 */
public final class TopicNormalizer {

    /** Canonical separator stored in DB and shown in UI. */
    public static final String SEPARATOR = "›"; // ›
    public static final String JOIN_DELIM = " " + SEPARATOR + " ";
    public static final int MAX_LEVELS = 5;
    public static final int MAX_LEVEL_CHARS = 100;

    /** Any of `/`, `>`, `›` with optional surrounding whitespace splits levels. */
    private static final Pattern SPLIT = Pattern.compile("\\s*[/>›]\\s*");

    private TopicNormalizer() {}

    /**
     * Split raw input into level segments. Returns an empty array when input
     * is null, blank, or contains only delimiters. Segments exceeding
     * {@link #MAX_LEVEL_CHARS} are truncated. Levels past {@link #MAX_LEVELS}
     * are dropped.
     */
    public static String[] parse(String raw) {
        if (raw == null) return new String[0];
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return new String[0];

        String[] parts = SPLIT.split(trimmed, -1);
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            String t = p.trim();
            if (t.isEmpty()) continue; // skip blank slots from leading/trailing/double delimiters
            if (t.length() > MAX_LEVEL_CHARS) t = t.substring(0, MAX_LEVEL_CHARS);
            out.add(t);
            if (out.size() >= MAX_LEVELS) break;
        }
        return out.toArray(new String[0]);
    }

    /** Join levels with the canonical separator. Returns null when empty. */
    public static String format(String[] levels) {
        if (levels == null || levels.length == 0) return null;
        return String.join(JOIN_DELIM, levels);
    }

    /** Convenience for {@code format(parse(raw))}. Returns null when input is empty. */
    public static String normalize(String raw) {
        return format(parse(raw));
    }

    /** Return level at 1-based index, or null if not set. */
    public static String levelAt(String[] levels, int oneBased) {
        if (levels == null || oneBased < 1 || oneBased > levels.length) return null;
        return levels[oneBased - 1];
    }

    /** Convenience for {@link #levelAt(String[], int)} from raw input. */
    public static String levelAt(String raw, int oneBased) {
        return levelAt(parse(raw), oneBased);
    }

    /** Levels as a fixed-size list of size {@link #MAX_LEVELS}, padding with null. */
    public static List<String> toPaddedList(String[] levels) {
        List<String> padded = new ArrayList<>(MAX_LEVELS);
        padded.addAll(Arrays.asList(levels));
        while (padded.size() < MAX_LEVELS) padded.add(null);
        return padded;
    }
}
