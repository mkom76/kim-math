package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.TextbookDto;
import com.example.entity.Textbook;
import com.example.exception.ForbiddenException;
import com.example.repository.TextbookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TextbookService {
    private final TextbookRepository textbookRepository;

    @Transactional(readOnly = true)
    public List<TextbookDto> listMine() {
        Long teacherId = requireTeacherId();
        return textbookRepository.findByOwnerTeacherIdOrderByCreatedAtDesc(teacherId).stream()
                .map(TextbookDto::summary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TextbookDto get(Long id) {
        Textbook tb = loadOwnedById(id);
        return TextbookDto.withProblems(tb);
    }

    public TextbookDto create(String title) {
        Long teacherId = requireTeacherId();
        Textbook tb = textbookRepository.save(Textbook.builder()
                .ownerTeacherId(teacherId)
                .title(title)
                .build());
        return TextbookDto.summary(tb);
    }

    public TextbookDto update(Long id, String title) {
        Textbook tb = loadOwnedById(id);
        tb.setTitle(title);
        return TextbookDto.summary(textbookRepository.save(tb));
    }

    public void delete(Long id) {
        Textbook tb = loadOwnedById(id);
        textbookRepository.delete(tb);
    }

    private Textbook loadOwnedById(Long id) {
        Long teacherId = requireTeacherId();
        Textbook tb = textbookRepository.findById(id)
                .orElseThrow(() -> new ForbiddenException("교재를 찾을 수 없습니다"));
        if (!teacherId.equals(tb.getOwnerTeacherId())) {
            throw new ForbiddenException("본인의 교재만 접근할 수 있습니다");
        }
        return tb;
    }

    private Long requireTeacherId() {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.role() == null) {
            throw new ForbiddenException("선생님 권한이 필요합니다");
        }
        return ctx.teacherId();
    }
}
