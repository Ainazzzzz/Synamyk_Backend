package synamyk.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import synamyk.dto.AnswerOptionResponse;
import synamyk.dto.attempt.QuestionView;
import synamyk.entities.AnswerOption;
import synamyk.entities.Question;
import synamyk.enums.QuestionType;
import synamyk.util.FigureValidator;
import synamyk.util.L10n;

import java.util.List;

/** Builds the client view of a test question (localized, without correct flags). */
@Component
@RequiredArgsConstructor
public class QuestionPresenter {

    private final MinioService minioService;
    private final FigureValidator figureValidator;

    public QuestionView toView(Question q, int index, List<Long> selectedOptionIds, String lang) {
        return QuestionView.builder()
                .questionId(q.getId())
                .index(index)
                .number(index + 1)
                .passageId(q.getPassage() != null ? q.getPassage().getId() : null)
                .sectionName(L10n.pick(q.getSectionName(), q.getSectionNameKy(), lang))
                .questionType(typeOf(q).name())
                .text(L10n.pick(q.getText(), q.getTextKy(), lang))
                .columnA(L10n.pick(q.getColumnA(), q.getColumnAKy(), lang))
                .columnB(L10n.pick(q.getColumnB(), q.getColumnBKy(), lang))
                .imageUrl(minioService.presign(q.getImageUrl()))
                .figure(figure(q))
                .pointValue(q.getPointValue())
                .options(options(q, lang))
                .selectedOptionIds(selectedOptionIds)
                .build();
    }

    public java.util.Map<String, Object> figure(Question q) {
        return figureValidator.fromJson(q.getFigure());
    }

    public List<AnswerOptionResponse> options(Question q, String lang) {
        return q.getOptions().stream()
                .map(o -> AnswerOptionResponse.builder()
                        .id(o.getId())
                        .label(o.getLabel())
                        .text(L10n.pick(o.getText(), o.getTextKy(), lang))
                        .orderIndex(o.getOrderIndex())
                        .build())
                .toList();
    }

    public static QuestionType typeOf(Question q) {
        return q.getQuestionType() != null ? q.getQuestionType() : QuestionType.STANDARD;
    }

    /** Exact-set match: correct only if the selected options are exactly the correct ones. */
    public static boolean isCorrect(Question q, List<AnswerOption> selected) {
        if (selected == null || selected.isEmpty()) return false;
        List<Long> correctIds = q.getOptions().stream()
                .filter(AnswerOption::getIsCorrect).map(AnswerOption::getId).sorted().toList();
        List<Long> selectedIds = selected.stream().map(AnswerOption::getId).sorted().toList();
        return !correctIds.isEmpty() && correctIds.equals(selectedIds);
    }
}
