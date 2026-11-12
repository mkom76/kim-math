package com.example.service.notebook;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

/** Decode only bounded raster images; re-encode pixels without file names or EXIF/GPS. */
public final class NotebookPhotoCodec {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    private NotebookPhotoCodec() {}

    public static byte[] photo(byte[] bytes, int rotation) {
        if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270)
            throw bad("사진 회전 값을 확인해주세요.");
        BufferedImage source = read(bytes, MAX_BYTES);
        int w = rotation % 180 == 0 ? source.getWidth() : source.getHeight();
        int h = rotation % 180 == 0 ? source.getHeight() : source.getWidth();
        double scale = Math.min(1.0, 1600.0 / Math.max(w, h));
        BufferedImage result = new BufferedImage(Math.max(1, (int)(w * scale)), Math.max(1, (int)(h * scale)), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setColor(Color.WHITE); g.fillRect(0, 0, result.getWidth(), result.getHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.scale(scale, scale);
            switch (rotation) {
                case 90 -> {g.translate(w, 0); g.rotate(Math.PI / 2);}
                case 180 -> {g.translate(w, h); g.rotate(Math.PI);}
                case 270 -> {g.translate(0, h); g.rotate(-Math.PI / 2);}
                default -> {}
            }
            g.drawImage(source, 0, 0, null);
        } finally {g.dispose(); source.flush();}
        return encode(result, "jpeg");
    }

    public static byte[] png(byte[] bytes) {return encode(read(bytes, 8 * 1024 * 1024), "png");}

    private static BufferedImage read(byte[] bytes, int limit) {
        if (bytes == null || bytes.length == 0) throw bad("사진 파일이 필요합니다.");
        if (bytes.length > limit) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "사진 용량이 너무 큽니다.");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw bad("사진을 읽지 못했어요. JPG 또는 PNG로 다시 선택해주세요.");
            var reader = readers.next();
            try {
                if (!java.util.Set.of("JPEG", "PNG").contains(reader.getFormatName().toUpperCase())) throw bad("JPG 또는 PNG 사진이 필요합니다.");
                reader.setInput(input, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || (long)w * h > 16_000_000L) throw bad("사진 해상도가 너무 큽니다. 한 문제만 담아 다시 선택해주세요.");
                return reader.read(0);
            } finally {reader.dispose();}
        } catch (IOException | IllegalArgumentException e) {throw bad("손상된 사진을 읽지 못했어요. 다시 선택해주세요.");}
    }
    private static byte[] encode(BufferedImage image, String format) {
        try (var output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, output)) throw bad("사진을 변환하지 못했어요.");
            return output.toByteArray();
        } catch (IOException e) {throw bad("사진을 변환하지 못했어요.");}
        finally {image.flush();}
    }
    private static ResponseStatusException bad(String text) {return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);}
}
