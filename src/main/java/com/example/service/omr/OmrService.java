package com.example.service.omr;

import com.example.dto.QuestionSnapshot;
import com.example.entity.QuestionType;
import com.example.entity.TestQuestion;
import com.example.repository.TestQuestionRepository;
import com.example.repository.TestRepository;
import com.example.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Semaphore;

import static com.example.service.omr.OmrTypes.*;

@Service
@RequiredArgsConstructor
public class OmrService {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_PAGES = 30;
    private final TestRepository testRepository;
    private final TestQuestionRepository questionRepository;
    private final AuthorizationService authorizationService;
    private final OmrRecognizer recognizer;
    private final Semaphore scans = new Semaphore(1);

    @Transactional(readOnly = true)
    public Scan scan(Long testId, byte[] bytes) {
        var test = testRepository.findById(testId).orElseThrow(() -> bad("시험이 없습니다."));
        authorizationService.assertCanAccessTest(test);
        var questions = questionRepository.findByTestIdOrderByNumber(testId);
        validateQuestions(questions);
        if (bytes.length < 5 || bytes.length > MAX_BYTES || bytes[0] != '%' || bytes[1] != 'P'
                || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-') throw bad("8MB 이하의 스캔 PDF를 선택해 주세요.");
        if (!scans.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "다른 OMR을 인식 중입니다. 잠시 후 다시 시도해 주세요.");
        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) throw bad("암호가 없는 PDF를 올려 주세요.");
            if (document.getNumberOfPages() < 1 || document.getNumberOfPages() > MAX_PAGES) throw bad("PDF는 1~30쪽까지 올릴 수 있습니다.");
            var renderer = new PDFRenderer(document);
            renderer.setSubsamplingAllowed(true);
            List<Page> pages = new ArrayList<>();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                var size = document.getPage(i).getCropBox();
                float longest = Math.max(size.getWidth(), size.getHeight());
                if (!Float.isFinite(longest) || longest < 10 || longest > 15000) throw bad("PDF 용지 크기를 확인해 주세요.");
                var result = recognizer.read(renderer.renderImage(i, 1600f / longest, ImageType.RGB));
                var output = new ByteArrayOutputStream();
                ImageIO.write(result.image(), "jpg", output);
                pages.add(new Page(i + 1, result.recognized(), result.recognized() ? "" : "기존 OMR 양식을 인식하지 못했습니다. 방향·잘림·스캔 품질을 확인하고 다시 올려 주세요.",
                        "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(output.toByteArray()), result.marks()));
                result.image().flush();
            }
            return new Scan(questions.stream().map(QuestionSnapshot::from).toList(),
                    questions.stream().map(q -> new Question(q.getNumber(), Boolean.TRUE.equals(q.getMultipleAnswers()))).toList(), pages);
        } catch (IOException | IllegalArgumentException e) {
            throw bad("PDF를 읽을 수 없습니다. 암호·손상 여부와 스캔 설정을 확인해 주세요.");
        } finally { scans.release(); }
    }

    public static void validateQuestions(List<TestQuestion> questions) {
        if (questions.isEmpty() || questions.size() > 30 || questions.stream().anyMatch(q ->
                q.getNumber() == null || q.getNumber() < 1 || q.getNumber() > 30 || q.getQuestionType() != QuestionType.OBJECTIVE)) {
            throw bad("OMR은 문항 번호가 1~30인 객관식 시험에서 사용할 수 있습니다. 주관식·서술형은 기존 답안 입력을 이용해 주세요.");
        }
    }

    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
