package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.TextbookProblemDto;
import com.example.entity.Textbook;
import com.example.entity.TextbookProblem;
import com.example.exception.ForbiddenException;
import com.example.repository.TextbookProblemRepository;
import com.example.repository.TextbookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TextbookProblemService {
    private final TextbookProblemRepository textbookProblemRepository;
    private final TextbookRepository textbookRepository;

    @Transactional(readOnly = true)
    public List<TextbookProblemDto> listByTextbook(Long textbookId) {
        Textbook tb = loadOwnedTextbook(textbookId);
        return textbookProblemRepository.findByTextbookIdOrderByNumberAsc(tb.getId()).stream()
                .map(TextbookProblemDto::from)
                .collect(Collectors.toList());
    }

    /**
     * Distinct non-blank values at the given {@code level} (1..5) across the
     * textbook's problems, optionally filtered by selected parent levels.
     * Returned list is sorted lexicographically for stable UI ordering.
     */
    @Transactional(readOnly = true)
    public List<String> suggestTopics(Long textbookId, int level,
                                      String l1, String l2, String l3, String l4) {
        if (level < 1 || level > TopicNormalizer.MAX_LEVELS) return List.of();
        Textbook tb = loadOwnedTextbook(textbookId);
        return textbookProblemRepository.findByTextbookIdOrderByNumberAsc(tb.getId()).stream()
                .filter(p -> level <= 1 || java.util.Objects.equals(p.getTopicL1(), nullIfBlank(l1)))
                .filter(p -> level <= 2 || java.util.Objects.equals(p.getTopicL2(), nullIfBlank(l2)))
                .filter(p -> level <= 3 || java.util.Objects.equals(p.getTopicL3(), nullIfBlank(l3)))
                .filter(p -> level <= 4 || java.util.Objects.equals(p.getTopicL4(), nullIfBlank(l4)))
                .map(p -> levelOf(p, level))
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String levelOf(TextbookProblem p, int level) {
        return switch (level) {
            case 1 -> p.getTopicL1();
            case 2 -> p.getTopicL2();
            case 3 -> p.getTopicL3();
            case 4 -> p.getTopicL4();
            case 5 -> p.getTopicL5();
            default -> null;
        };
    }

    public List<TextbookProblemDto> bulkCreate(Long textbookId, List<TextbookProblemDto> items) {
        Textbook tb = loadOwnedTextbook(textbookId);
        List<TextbookProblem> toSave = items.stream()
                .map(dto -> {
                    TextbookProblem p = TextbookProblem.builder()
                            .textbook(tb)
                            .number(dto.getNumber())
                            .answer(dto.getAnswer())
                            .questionType(dto.getQuestionType())
                            .videoLink(dto.getVideoLink())
                            .build();
                    applyTopic(p, dto.getTopic());
                    return p;
                })
                .collect(Collectors.toList());
        return textbookProblemRepository.saveAll(toSave).stream()
                .map(TextbookProblemDto::from)
                .collect(Collectors.toList());
    }

    public TextbookProblemDto create(Long textbookId, TextbookProblemDto dto) {
        Textbook tb = loadOwnedTextbook(textbookId);
        TextbookProblem p = TextbookProblem.builder()
                .textbook(tb)
                .number(dto.getNumber())
                .answer(dto.getAnswer())
                .questionType(dto.getQuestionType())
                .videoLink(dto.getVideoLink())
                .build();
        applyTopic(p, dto.getTopic());
        return TextbookProblemDto.from(textbookProblemRepository.save(p));
    }

    public TextbookProblemDto update(Long problemId, TextbookProblemDto dto) {
        TextbookProblem p = loadOwnedProblem(problemId);
        p.setNumber(dto.getNumber());
        p.setAnswer(dto.getAnswer());
        p.setQuestionType(dto.getQuestionType());
        applyTopic(p, dto.getTopic());
        p.setVideoLink(dto.getVideoLink());
        return TextbookProblemDto.from(textbookProblemRepository.save(p));
    }

    /**
     * Set both the canonical {@code topic} path and the {@code topic_l1..l5}
     * denormalized columns from whatever raw input the caller provided.
     */
    static void applyTopic(TextbookProblem p, String rawTopic) {
        String[] levels = TopicNormalizer.parse(rawTopic);
        p.setTopic(TopicNormalizer.format(levels));
        p.setTopicL1(TopicNormalizer.levelAt(levels, 1));
        p.setTopicL2(TopicNormalizer.levelAt(levels, 2));
        p.setTopicL3(TopicNormalizer.levelAt(levels, 3));
        p.setTopicL4(TopicNormalizer.levelAt(levels, 4));
        p.setTopicL5(TopicNormalizer.levelAt(levels, 5));
    }

    public void delete(Long problemId) {
        TextbookProblem p = loadOwnedProblem(problemId);
        textbookProblemRepository.delete(p);
    }

    private Textbook loadOwnedTextbook(Long textbookId) {
        Long teacherId = requireTeacherId();
        Textbook tb = textbookRepository.findById(textbookId)
                .orElseThrow(() -> new ForbiddenException("교재를 찾을 수 없습니다"));
        if (!teacherId.equals(tb.getOwnerTeacherId())) {
            throw new ForbiddenException("본인의 교재만 접근할 수 있습니다");
        }
        return tb;
    }

    private TextbookProblem loadOwnedProblem(Long problemId) {
        Long teacherId = requireTeacherId();
        TextbookProblem p = textbookProblemRepository.findById(problemId)
                .orElseThrow(() -> new ForbiddenException("문제를 찾을 수 없습니다"));
        Textbook parent = p.getTextbook();
        if (parent == null || !teacherId.equals(parent.getOwnerTeacherId())) {
            throw new ForbiddenException("본인의 교재만 접근할 수 있습니다");
        }
        return p;
    }

    private Long requireTeacherId() {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.role() == null) {
            throw new ForbiddenException("선생님 권한이 필요합니다");
        }
        return ctx.teacherId();
    }
}
