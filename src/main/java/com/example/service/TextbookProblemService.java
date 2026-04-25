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

    public List<TextbookProblemDto> bulkCreate(Long textbookId, List<TextbookProblemDto> items) {
        Textbook tb = loadOwnedTextbook(textbookId);
        List<TextbookProblem> toSave = items.stream()
                .map(dto -> TextbookProblem.builder()
                        .textbook(tb)
                        .number(dto.getNumber())
                        .answer(dto.getAnswer())
                        .questionType(dto.getQuestionType())
                        .topic(dto.getTopic())
                        .videoLink(dto.getVideoLink())
                        .build())
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
                .topic(dto.getTopic())
                .videoLink(dto.getVideoLink())
                .build();
        return TextbookProblemDto.from(textbookProblemRepository.save(p));
    }

    public TextbookProblemDto update(Long problemId, TextbookProblemDto dto) {
        TextbookProblem p = loadOwnedProblem(problemId);
        p.setNumber(dto.getNumber());
        p.setAnswer(dto.getAnswer());
        p.setQuestionType(dto.getQuestionType());
        p.setTopic(dto.getTopic());
        p.setVideoLink(dto.getVideoLink());
        return TextbookProblemDto.from(textbookProblemRepository.save(p));
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
