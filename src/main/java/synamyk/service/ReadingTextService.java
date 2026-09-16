package synamyk.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import synamyk.dto.text.*;
import synamyk.entities.ReadingText;
import synamyk.enums.ProductCode;
import synamyk.exception.AppException;
import synamyk.repo.ReadingTextRepository;
import synamyk.util.L10n;

import java.time.LocalDateTime;
import java.util.List;

/** «Тексттер» tab: practice reading texts unlocked by the ALL_TEXTS product. */
@Service
@RequiredArgsConstructor
public class ReadingTextService {

    private final ReadingTextRepository repository;
    private final AccessResolver accessResolver;
    private final MinioService minioService;

    public List<ReadingTextListItem> list(Long userId, String lang) {
        boolean all = accessResolver.hasAllAccess(userId, ProductCode.ALL_TEXTS, LocalDateTime.now());
        return repository.findByActiveTrueOrderByOrderIndexAscIdAsc().stream()
                .map(t -> ReadingTextListItem.builder()
                        .id(t.getId())
                        .title(L10n.pick(t.getTitle(), t.getTitleKy(), lang))
                        .orderIndex(t.getOrderIndex())
                        .free(t.getFree())
                        .hasAccess(all || Boolean.TRUE.equals(t.getFree()))
                        .hasPdf(t.getPdfUrl() != null && !t.getPdfUrl().isBlank())
                        .build())
                .toList();
    }

    public ReadingTextDetail get(Long id, Long userId, String lang) {
        ReadingText t = repository.findById(id)
                .filter(ReadingText::getActive)
                .orElseThrow(() -> new AppException("Текст не найден.", "Текст табылган жок."));
        if (!Boolean.TRUE.equals(t.getFree())
                && !accessResolver.hasAllAccess(userId, ProductCode.ALL_TEXTS, LocalDateTime.now())) {
            throw new AppException(
                    "Нет доступа. Откройте все тексты одной оплатой.",
                    "Мүмкүнчүлүк жок. Бардык тексттерди бир төлөм менен ачыңыз.");
        }
        return ReadingTextDetail.builder()
                .id(t.getId())
                .title(L10n.pick(t.getTitle(), t.getTitleKy(), lang))
                .content(L10n.pick(t.getContent(), t.getContentKy(), lang))
                .pdfUrl(minioService.presign(t.getPdfUrl()))
                .build();
    }

    // ===== ADMIN =====

    public List<AdminReadingTextResponse> adminList() {
        return repository.findAllByOrderByOrderIndexAscIdAsc().stream().map(this::toAdmin).toList();
    }

    @Transactional
    public AdminReadingTextResponse create(ReadingTextRequest r) {
        return toAdmin(repository.save(apply(new ReadingText(), r)));
    }

    @Transactional
    public AdminReadingTextResponse update(Long id, ReadingTextRequest r) {
        ReadingText t = repository.findById(id)
                .orElseThrow(() -> new AppException("Текст не найден.", "Текст табылган жок."));
        return toAdmin(repository.save(apply(t, r)));
    }

    @Transactional
    public void delete(Long id) {
        ReadingText t = repository.findById(id)
                .orElseThrow(() -> new AppException("Текст не найден.", "Текст табылган жок."));
        t.setActive(false);
        repository.save(t);
    }

    public String uploadPdf(MultipartFile file) {
        try {
            return minioService.uploadPdf(file);
        } catch (IllegalArgumentException e) {
            throw new AppException(e.getMessage(), e.getMessage());
        }
    }

    private ReadingText apply(ReadingText t, ReadingTextRequest r) {
        t.setTitle(r.getTitle());
        t.setTitleKy(r.getTitleKy());
        t.setContent(r.getContent());
        t.setContentKy(r.getContentKy());
        t.setPdfUrl(r.getPdfUrl() != null && !r.getPdfUrl().isBlank() ? minioService.extractKey(r.getPdfUrl()) : null);
        t.setFree(Boolean.TRUE.equals(r.getFree()));
        t.setOrderIndex(r.getOrderIndex() != null ? r.getOrderIndex() : 0);
        t.setActive(r.getActive() == null || r.getActive());
        if ((t.getContent() == null || t.getContent().isBlank()) && t.getPdfUrl() == null) {
            throw new AppException("Укажите текст или PDF.", "Текст же PDF көрсөтүңүз.");
        }
        return t;
    }

    private AdminReadingTextResponse toAdmin(ReadingText t) {
        return AdminReadingTextResponse.builder()
                .id(t.getId())
                .title(t.getTitle())
                .titleKy(t.getTitleKy())
                .content(t.getContent())
                .contentKy(t.getContentKy())
                .pdfKey(t.getPdfUrl())
                .pdfUrl(minioService.presign(t.getPdfUrl()))
                .free(t.getFree())
                .orderIndex(t.getOrderIndex())
                .active(t.getActive())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
