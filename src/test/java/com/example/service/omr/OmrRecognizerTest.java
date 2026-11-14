package com.example.service.omr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class OmrRecognizerTest {
    private final OmrRecognizer recognizer = new OmrRecognizer();

    private BufferedImage form() throws Exception {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/omr/2026omr.pdf"));
             var pdf = Loader.loadPDF(stream.readAllBytes())) {
            return new PDFRenderer(pdf).renderImage(0, 1600f / pdf.getPage(0).getCropBox().getWidth(), ImageType.RGB);
        }
    }

    @Test void existing_blank_form_is_recognized_without_inventing_answers() throws Exception {
        var result = recognizer.read(form());
        assertThat(result.recognized()).isTrue();
        assertThat(result.marks()).hasSize(30).allSatisfy(mark -> {
            assertThat(mark.answer()).isEmpty();
            assertThat(mark.status()).isEqualTo("BLANK");
        });
    }

    @Test void filled_faint_erased_multiple_and_last_rows_are_distinguished() throws Exception {
        var image = form();
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(830, 146, 11, 22); // 1번 1
        g.fillRect(886, 187, 11, 22); // 2번 2,4
        g.fillRect(995, 187, 11, 22);
        g.fillRect(1050, 756, 11, 22); // 15번 5
        g.fillRect(1376, 152, 11, 22); // 16번 3
        g.fillRect(1485, 760, 11, 22); // 30번 5
        g.setColor(new Color(210, 210, 210));
        g.fillRect(830, 231, 11, 22); // faint / erased mark must not become blank
        g.dispose();
        var result = recognizer.read(image);
        assertThat(result.recognized()).isTrue();
        assertThat(result.marks().get(0).answer()).isEqualTo("1");
        assertThat(result.marks().get(1).answer()).isEqualTo("2,4");
        assertThat(result.marks().get(1).status()).isEqualTo("MULTIPLE");
        assertThat(result.marks().get(2).status()).isEqualTo("UNCERTAIN");
        assertThat(result.marks().get(14).answer()).isEqualTo("5");
        assertThat(result.marks().get(15).answer()).isEqualTo("3");
        assertThat(result.marks().get(29).answer()).isEqualTo("5");
    }

    @Test void skewed_scan_and_upside_down_scan_keep_question_identity() throws Exception {
        var image = form();
        Graphics2D pen = image.createGraphics(); pen.setColor(Color.BLACK); pen.fillRect(830, 146, 11, 22); pen.dispose();
        for (double angle : new double[]{-2.3, 2.3, 180}) {
            var scanned = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scanned.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0, 0, scanned.getWidth(), scanned.getHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.rotate(Math.toRadians(angle), image.getWidth() / 2.0, image.getHeight() / 2.0);
            g.drawImage(image, 0, 0, null); g.dispose();
            var result = recognizer.read(scanned);
            assertThat(result.recognized()).as("angle %s", angle).isTrue();
            assertThat(result.marks().getFirst().answer()).isEqualTo("1");
        }
    }

    @Test void unrelated_or_cropped_page_cannot_be_saved_as_blank_form() throws Exception {
        assertThat(recognizer.read(new BufferedImage(1600, 1132, BufferedImage.TYPE_INT_RGB)).recognized()).isFalse();
        var image = form();
        Graphics2D g = image.createGraphics(); g.setColor(Color.WHITE); g.fillRect(1160, 0, 440, 1132); g.dispose();
        assertThat(recognizer.read(image).recognized()).isFalse();
    }

    @Test void threshold_is_inclusive_and_separates_blank_faint_and_ambiguous_marks() {
        assertThat(OmrRecognizer.classify(new double[]{.01,.40,.03,.02,.04}))
                .isEqualTo(new OmrRecognizer.Reading("2", "MARKED"));
        assertThat(OmrRecognizer.classify(new double[]{.01,.3999,.03,.02,.04}).status()).isEqualTo("UNCERTAIN");
        assertThat(OmrRecognizer.classify(new double[]{.01,.50,.03,.02,.14}).status()).isEqualTo("MARKED");
        assertThat(OmrRecognizer.classify(new double[]{.01,.40,.03,.02,.14}).status()).isEqualTo("UNCERTAIN");
        assertThat(OmrRecognizer.classify(new double[]{.01,.50,.03,.02,.15}).status()).isEqualTo("UNCERTAIN");
        assertThat(OmrRecognizer.classify(new double[]{.01,.50,.03,.02,.39}).status()).isEqualTo("UNCERTAIN");
        assertThat(OmrRecognizer.classify(new double[]{.01,.09,.03,.02,.04}).status()).isEqualTo("BLANK");
        assertThat(OmrRecognizer.classify(new double[]{.01,.10,.03,.02,.04}).status()).isEqualTo("UNCERTAIN");
        assertThat(OmrRecognizer.classify(new double[]{.01,.50,.03,.45,.04}))
                .isEqualTo(new OmrRecognizer.Reading("2,4", "MULTIPLE"));
        assertThat(OmrRecognizer.classify(new double[]{.01,.50,.18,.45,.04}).status()).isEqualTo("UNCERTAIN");
    }

    @Test void gray_pencil_fill_is_read_without_accepting_erasure_or_double_marks() throws Exception {
        var image = form();
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(128,128,128));
        g.fillRect(830,146,11,22); // ordinary gray pencil fill, previously below .55
        g.fillRect(886,187,11,22);
        g.fillRect(995,187,11,22); // two equally strong marks must remain multiple
        g.fillRect(940,231,11,22);
        g.setColor(new Color(205,205,205)); g.fillRect(830,231,11,22); // erased alternate choice
        g.setColor(new Color(210,210,210)); g.fillRect(830,275,11,22); // faint only
        g.dispose();
        var result = recognizer.read(image);
        assertThat(result.recognized()).isTrue();
        assertThat(result.marks().get(0).status()).isEqualTo("MARKED");
        assertThat(result.marks().get(0).answer()).isEqualTo("1");
        assertThat(result.marks().get(1).status()).isEqualTo("MULTIPLE");
        assertThat(result.marks().get(1).answer()).isEqualTo("2,4");
        assertThat(result.marks().get(2).status()).isEqualTo("UNCERTAIN");
        assertThat(result.marks().get(3).status()).isEqualTo("UNCERTAIN");
        assertThat(result.marks().subList(4,30)).allSatisfy(mark -> assertThat(mark.status()).isEqualTo("BLANK"));
    }
}
