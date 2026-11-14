package com.example.service.omr;

import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static com.example.service.omr.OmrTypes.*;

/** Fixed-layout optical mark reading for the existing 2026omr form, without OCR or AI calls. */
@Component
public class OmrRecognizer {
    static final double MARK_THRESHOLD = .40;
    private static final double FAINT_THRESHOLD = .10;
    private static final double SECONDARY_LIMIT = .15;
    private static final double MIN_SINGLE_MARK_GAP = .30;
    record Reading(String answer, String status) {}
    // Coordinates measured from the supplied blank form at 1600 x 1132. The two tables
    // have slightly different row spacing, so an evenly divided 15-row grid is incorrect.
    private static final double[][] ROWS = {
            {156.5,196.5,240.5,284.5,331.5,375.5,417.5,461.5,505.5,548.5,591.5,635.5,679,723.5,765.5},
            {162.5,208,250,295,338,381,424,467,508,552,596,640,683,726,769.5}};
    private static final double[] COLUMNS = {103,158,213,268,322};
    public record Result(BufferedImage image, boolean recognized, List<Mark> marks) {}
    private record Table(int left, int right, int top, int bottom) {}
    private record Layout(BufferedImage image, List<Table> tables) {}

    public Result read(BufferedImage input) {
        BufferedImage landscape = input.getWidth() < input.getHeight() ? rotate(input, Math.PI / 2) : input;
        Layout layout = locate(landscape);
        if (layout == null) layout = locate(rotate(landscape, Math.PI));
        if (layout == null) return new Result(landscape, false, List.of());

        BufferedImage image = layout.image();
        List<Mark> marks = new ArrayList<>();
        int outlined = 0;
        for (int tableIndex = 0; tableIndex < 2; tableIndex++) {
            Table table = layout.tables().get(tableIndex);
            double sx = (table.right() - table.left()) / 353.0;
            double sy = (table.bottom() - table.top()) / 696.0;
            for (int row = 0; row < 15; row++) {
                double cy = table.top() + (ROWS[tableIndex][row] - 101) * sy;
                double[] scores = new double[5];
                for (int col = 0; col < 5; col++) {
                    double cx = table.left() + COLUMNS[col] * sx;
                    if (borderSupport(image, cx, cy, sx, sy) > .12) outlined++;
                    scores[col] = ink(image, cx - 4 * sx, cy - 9 * sy, cx + 4 * sx, cy + 9 * sy);
                }
                Reading reading = classify(scores);
                Box box = new Box((table.left() + 86 * sx) / image.getWidth(), (cy - 17 * sy) / image.getHeight(),
                        252 * sx / image.getWidth(), 34 * sy / image.getHeight());
                marks.add(new Mark(tableIndex * 15 + row + 1, reading.answer(), reading.status(), box));
            }
        }
        // Similar-looking unrelated tables must not silently produce thirty blank answers.
        if (outlined < 120) return new Result(image, false, List.of());
        return new Result(image, true, marks);
    }

    /** Require a clear gap for a single mark; never resolve two dark choices to a winner. */
    static Reading classify(double[] scores) {
        List<String> selected = new ArrayList<>();
        double peak = 0, secondary = 0;
        for (int i = 0; i < scores.length; i++) {
            peak = Math.max(peak, scores[i]);
            if (scores[i] >= MARK_THRESHOLD) selected.add(Integer.toString(i + 1));
            else secondary = Math.max(secondary, scores[i]);
        }
        String status;
        if (selected.isEmpty()) status = peak >= FAINT_THRESHOLD ? "UNCERTAIN" : "BLANK";
        else if (selected.size() > 1) status = secondary >= FAINT_THRESHOLD ? "UNCERTAIN" : "MULTIPLE";
        else status = secondary >= SECONDARY_LIMIT || peak - secondary < MIN_SINGLE_MARK_GAP ? "UNCERTAIN" : "MARKED";
        return new Reading(String.join(",", selected), status);
    }

    private Layout locate(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        if (w / (double) h < 1.25 || w / (double) h > 1.6) return null;
        int y0 = (int) (h * .18), y1 = (int) (h * .62);
        List<int[]> pixels = new ArrayList<>();
        for (int y = y0; y < y1; y++) {
            for (int x = (int) (w * .39); x < w * .99; x++) {
                if (dark(image, x, y)) pixels.add(new int[]{x, y});
            }
        }
        double bestSlope = 0;
        int bestScore = 0;
        // Projection detects the six long vertical rules, allowing up to four degrees of skew.
        for (int step = -40; step <= 40; step++) {
            double slope = Math.tan(Math.toRadians(step / 10.0));
            int[] histogram = new int[w];
            for (int[] pixel : pixels) {
                int x = (int) Math.round(pixel[0] - slope * (pixel[1] - h / 2.0));
                if (x >= 1 && x < w - 1) histogram[x]++;
            }
            int[] peaks = peaks(histogram, 6, (int) (w * .025));
            int score = Arrays.stream(peaks).map(x -> smoothed(histogram, x)).sum();
            if (score > bestScore) { bestScore = score; bestSlope = slope; }
        }
        BufferedImage aligned = rotate(image, Math.atan(bestSlope));
        int[] histogram = new int[w];
        for (int x = (int) (w * .39); x < w * .99; x++) {
            for (int y = y0; y < y1; y++) if (dark(aligned, x, y)) histogram[x]++;
        }
        int[] lines = peaks(histogram, 6, (int) (w * .025));
        Arrays.sort(lines);
        for (int x : lines) if (smoothed(histogram, x) < (y1 - y0) * .85) return null;
        List<Table> tables = new ArrayList<>();
        for (int t = 0; t < 2; t++) {
            int left = lines[t * 3], divider = lines[t * 3 + 1], right = lines[t * 3 + 2];
            int width = right - left;
            double ratio = (divider - left) / (double) width;
            if (width < w * .17 || width > w * .27 || ratio < .18 || ratio > .24) return null;
            int top = horizontal(aligned, left, right, .035, .17, .089);
            int bottom = horizontal(aligned, left, right, .59, .82, .705);
            if (top < 0 || bottom < 0 || (bottom - top) / (double) width < 1.8
                    || (bottom - top) / (double) width > 2.15) return null;
            tables.add(new Table(left, right, top, bottom));
        }
        if (Math.abs(tables.get(0).top() - tables.get(1).top()) > h * .015
                || Math.abs(tables.get(0).bottom() - tables.get(1).bottom()) > h * .015
                || lines[3] - lines[2] < w * .025 || lines[3] - lines[2] > w * .085) return null;
        return new Layout(aligned, tables);
    }

    private static int horizontal(BufferedImage image, int left, int right, double from, double to, double expected) {
        int found = -1;
        double distance = Double.MAX_VALUE;
        for (int y = (int) (image.getHeight() * from); y < image.getHeight() * to; y++) {
            int count = 0;
            for (int x = left; x <= right; x++) {
                if (dark(image, x, y) || dark(image, x, y - 1) || dark(image, x, y + 1)) count++;
            }
            if (count < (right - left) * .9) continue;
            double d = Math.abs(y - expected * image.getHeight());
            if (d < distance) { found = y; distance = d; }
        }
        return found;
    }

    private static int[] peaks(int[] histogram, int count, int gap) {
        Integer[] indices = new Integer[histogram.length];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, Comparator.comparingInt((Integer x) -> smoothed(histogram, x)).reversed());
        List<Integer> result = new ArrayList<>();
        for (int x : indices) {
            if (result.stream().allMatch(previous -> Math.abs(previous - x) > gap)) result.add(x);
            if (result.size() == count) break;
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int smoothed(int[] histogram, int x) {
        int sum = 0;
        for (int dx = -1; dx <= 1; dx++) if (x + dx >= 0 && x + dx < histogram.length) sum += histogram[x + dx];
        return sum;
    }

    private static double borderSupport(BufferedImage image, double x, double y, double sx, double sy) {
        return (density(image, x - 8 * sx, y - 13 * sy, x + 8 * sx, y - 10 * sy)
                + density(image, x - 8 * sx, y + 10 * sy, x + 8 * sx, y + 13 * sy)
                + density(image, x - 8 * sx, y - 13 * sy, x - 5 * sx, y + 13 * sy)
                + density(image, x + 5 * sx, y - 13 * sy, x + 8 * sx, y + 13 * sy)) / 4;
    }

    private static double density(BufferedImage image, double x0, double y0, double x1, double y1) {
        int count = 0, total = 0;
        for (int y = (int) Math.round(y0); y <= Math.round(y1); y++) {
            for (int x = (int) Math.round(x0); x <= Math.round(x1); x++) {
                total++;
                if (dark(image, x, y)) count++;
            }
        }
        return total == 0 ? 0 : count / (double) total;
    }

    private static boolean dark(BufferedImage image, int x, int y) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) return false;
        int rgb = image.getRGB(x, y);
        return (((rgb >> 16) & 255) * 299 + ((rgb >> 8) & 255) * 587 + (rgb & 255) * 114) < 230000;
    }

    private static double ink(BufferedImage image, double x0, double y0, double x1, double y1) {
        double sum = 0;
        int total = 0;
        for (int y = (int) Math.round(y0); y <= Math.round(y1); y++) {
            for (int x = (int) Math.round(x0); x <= Math.round(x1); x++) {
                if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) return 1;
                int rgb = image.getRGB(x, y);
                sum += 1 - ((((rgb >> 16) & 255) * 299 + ((rgb >> 8) & 255) * 587 + (rgb & 255) * 114) / 255000.0);
                total++;
            }
        }
        return total == 0 ? 0 : sum / total;
    }

    private static BufferedImage rotate(BufferedImage source, double radians) {
        if (Math.abs(radians) < .0001) return source;
        boolean quarter = Math.abs(Math.abs(radians) - Math.PI / 2) < .001;
        int w = quarter ? source.getHeight() : source.getWidth(), h = quarter ? source.getWidth() : source.getHeight();
        BufferedImage target = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.translate(w / 2.0, h / 2.0); g.rotate(radians); g.translate(-source.getWidth() / 2.0, -source.getHeight() / 2.0);
        g.drawImage(source, 0, 0, null); g.dispose();
        return target;
    }
}
