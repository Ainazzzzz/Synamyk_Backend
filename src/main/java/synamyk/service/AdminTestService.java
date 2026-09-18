package synamyk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import synamyk.dto.admin.*;
import synamyk.entities.*;
import synamyk.enums.PushCategory;
import synamyk.enums.PushDataType;
import synamyk.exception.AppException;
import synamyk.repo.*;
import synamyk.util.FigureValidator;
import synamyk.util.PushMessages;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTestService {

    private final TestRepository testRepository;
    private final SubTestRepository subTestRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository optionRepository;
    private final MinioService minioService;
    private final UserTestAccessRepository userTestAccessRepository;
    private final PushNotificationService pushNotificationService;
    private final ReadingPassageRepository passageRepository;
    private final FigureValidator figureValidator;

    // ===== TESTS =====

    public List<AdminTestResponse> getAllTests() {
        return testRepository.findAll().stream()
                .map(this::toAdminTestResponse)
                .toList();
    }

    public Page<AdminTestListResponse> listTests(int page, int size, String search, String subject, Boolean active) {
        String s = (search != null && !search.isBlank()) ? search.trim() : null;
        String sub = (subject != null && !subject.isBlank()) ? subject.trim() : null;
        return testRepository.findAllByFilters(s, sub, active, PageRequest.of(page, size))
                .map(t -> AdminTestListResponse.builder()
                        .id(t.getId())
                        .title(t.getTitle())
                        .iconUrl(minioService.presign(t.getIconUrl()))
                        .subject(t.getSubject())
                        .price(t.getPrice())
                        .questionCount(testRepository.countQuestionsByTestId(t.getId()))
                        .attemptsCount(testRepository.countAttemptsByTestId(t.getId()))
                        .createdAt(t.getCreatedAt())
                        .active(t.getActive())
                        .build());
    }

    public List<String> getSubjects() {
        return testRepository.findAllSubjects();
    }

    public AdminTestResponse getTest(Long testId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new AppException("Тест не найден.", "Тест табылган жок."));
        return toAdminTestResponse(test);
    }

    @Transactional
    public AdminTestResponse createTest(CreateTestRequest request) {
        Test test = Test.builder()
                .title(request.getTitle())
                .titleKy(request.getTitleKy())
                .description(request.getDescription())
                .descriptionKy(request.getDescriptionKy())
                .iconUrl(request.getIconUrl())
                .subject(request.getSubject())
                .price(request.getPrice())
                .maxScore(request.getMaxScore() != null ? request.getMaxScore() : 245)
                .active(true)
                .build();
        return toAdminTestResponse(testRepository.save(test));
    }

    @Transactional
    public AdminTestResponse updateTest(Long testId, CreateTestRequest request) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new AppException("Тест не найден.", "Тест табылган жок."));
        test.setTitle(request.getTitle());
        test.setTitleKy(request.getTitleKy());
        test.setDescription(request.getDescription());
        test.setDescriptionKy(request.getDescriptionKy());
        test.setIconUrl(request.getIconUrl());
        test.setSubject(request.getSubject());
        test.setPrice(request.getPrice());
        if (request.getMaxScore() != null) test.setMaxScore(request.getMaxScore());
        return toAdminTestResponse(testRepository.save(test));
    }

    @Transactional
    public void deleteTest(Long testId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new AppException("Тест не найден.", "Тест табылган жок."));
        test.setActive(false);
        testRepository.save(test);
    }

    /** Sets the price of the whole test — sections are not sold separately. */
    @Transactional
    public AdminTestResponse updateTestPricing(Long testId, UpdateTestPricingRequest request) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new AppException("Тест не найден.", "Тест табылган жок."));

        BigDecimal price = request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO;
        if (price.signum() < 0) {
            throw new AppException("Цена не может быть отрицательной.", "Баа терс болушу мүмкүн эмес.");
        }
        test.setPrice(price);
        testRepository.save(test);
        log.info("Updated pricing for testId={}: price={}", testId, price);

        return toAdminTestResponse(test);
    }

    // ===== SCHEDULE (free windows) =====

    @Transactional
    public AdminTestResponse updateTestSchedule(Long testId, ScheduleRequest request) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new AppException("Тест не найден.", "Тест табылган жок."));
        validateWindow(request);
        test.setFreeFrom(request.getFreeFrom());
        test.setFreeUntil(request.getFreeUntil());
        testRepository.save(test);
        log.info("Updated schedule for testId={}: freeFrom={}, freeUntil={}",
                testId, request.getFreeFrom(), request.getFreeUntil());
        return toAdminTestResponse(test);
    }

    private void validateWindow(ScheduleRequest r) {
        if (r.getFreeFrom() != null && r.getFreeUntil() != null && !r.getFreeUntil().isAfter(r.getFreeFrom())) {
            throw new AppException(
                    "Дата окончания бесплатности должна быть позже даты начала.",
                    "Бекер мөөнөттүн аякталышы башталышынан кийин болушу керек.");
        }
    }

    // ===== SUB-TESTS =====

    @Transactional
    public AdminTestResponse.AdminSubTestResponse createSubTest(Long testId, CreateSubTestRequest request) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new AppException("Тест не найден.", "Тест табылган жок."));

        SubTest subTest = SubTest.builder()
                .test(test)
                .title(request.getTitle())
                .titleKy(request.getTitleKy())
                .levelName(request.getLevelName())
                .levelNameKy(request.getLevelNameKy())
                .levelOrder(request.getLevelOrder())
                .durationMinutes(request.getDurationMinutes())
                .maxScore(request.getMaxScore())
                .iconUrl(request.getIconUrl())
                .active(true)
                .build();

        subTest = subTestRepository.save(subTest);

        // Notify everyone who owns the parent test that new content is available.
        List<Long> owners = userTestAccessRepository.findActiveUserIdsByTestId(testId, java.time.LocalDateTime.now());
        if (!owners.isEmpty()) {
            pushNotificationService.notifyUsersAsync(owners, PushCategory.MARKETING,
                    PushMessages.newSubTest(test.getTitle(), test.getTitleKy()),
                    PushDataType.SUB_TEST, subTest.getId());
        }

        return toAdminSubTestResponse(subTest);
    }

    @Transactional
    public AdminTestResponse.AdminSubTestResponse updateSubTest(Long subTestId, CreateSubTestRequest request) {
        SubTest subTest = subTestRepository.findById(subTestId)
                .orElseThrow(() -> new AppException("Подтест не найден.", "Подтест табылган жок."));

        subTest.setTitle(request.getTitle());
        subTest.setTitleKy(request.getTitleKy());
        subTest.setLevelName(request.getLevelName());
        subTest.setLevelNameKy(request.getLevelNameKy());
        subTest.setLevelOrder(request.getLevelOrder());
        subTest.setDurationMinutes(request.getDurationMinutes());
        subTest.setMaxScore(request.getMaxScore());
        subTest.setIconUrl(request.getIconUrl());

        return toAdminSubTestResponse(subTestRepository.save(subTest));
    }

    @Transactional
    public void deleteSubTest(Long subTestId) {
        SubTest subTest = subTestRepository.findById(subTestId)
                .orElseThrow(() -> new AppException("Подтест не найден.", "Подтест табылган жок."));
        subTest.setActive(false);
        subTestRepository.save(subTest);
    }

    // ===== QUESTIONS =====

    public List<AdminQuestionResponse> getQuestions(Long subTestId) {
        return questionRepository.findBySubTestIdOrderByOrderIndexAsc(subTestId).stream()
                .map(this::toAdminQuestionResponse)
                .toList();
    }

    @Transactional
    public AdminQuestionResponse createQuestion(Long subTestId, CreateQuestionRequest request) {
        SubTest subTest = subTestRepository.findById(subTestId)
                .orElseThrow(() -> new AppException("Подтест не найден.", "Подтест табылган жок."));

        List<CreateQuestionRequest.AnswerOptionRequest> options = resolveOptions(request);
        validateQuestionContent(request);

        Question question = Question.builder()
                .subTest(subTest)
                .text(request.getText())
                .textKy(request.getTextKy())
                .sectionName(request.getSectionName())
                .sectionNameKy(request.getSectionNameKy())
                .imageUrl(request.getImageUrl())
                .explanation(request.getExplanation())
                .explanationKy(request.getExplanationKy())
                .orderIndex(request.getOrderIndex())
                .pointValue(request.getPointValue())
                .questionType(typeOf(request))
                .columnA(request.getColumnA())
                .columnAKy(request.getColumnAKy())
                .columnB(request.getColumnB())
                .columnBKy(request.getColumnBKy())
                .figure(figureValidator.toJson(request.getFigure()))
                .passage(resolvePassage(request.getPassageId(), subTestId))
                .active(true)
                .build();

        question = questionRepository.save(question);

        int optIndex = 0;
        for (CreateQuestionRequest.AnswerOptionRequest optReq : options) {
            AnswerOption option = AnswerOption.builder()
                    .question(question)
                    .label(optReq.getLabel())
                    .text(optReq.getText())
                    .textKy(optReq.getTextKy())
                    .isCorrect(Boolean.TRUE.equals(optReq.getIsCorrect()))
                    .orderIndex(optIndex++)
                    .build();
            optionRepository.save(option);
        }

        return toAdminQuestionResponse(questionRepository.findById(question.getId()).orElseThrow());
    }

    @Transactional
    public AdminQuestionResponse updateQuestion(Long questionId, CreateQuestionRequest request) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new AppException("Вопрос не найден.", "Суроо табылган жок."));

        question.setText(request.getText());
        question.setTextKy(request.getTextKy());
        question.setSectionName(request.getSectionName());
        question.setSectionNameKy(request.getSectionNameKy());
        question.setImageUrl(request.getImageUrl());
        question.setExplanation(request.getExplanation());
        question.setExplanationKy(request.getExplanationKy());
        question.setOrderIndex(request.getOrderIndex());
        question.setPointValue(request.getPointValue());
        List<CreateQuestionRequest.AnswerOptionRequest> resolvedOptions = resolveOptions(request);
        validateQuestionContent(request);
        question.setQuestionType(typeOf(request));
        question.setColumnA(request.getColumnA());
        question.setColumnAKy(request.getColumnAKy());
        question.setColumnB(request.getColumnB());
        question.setColumnBKy(request.getColumnBKy());
        question.setFigure(figureValidator.toJson(request.getFigure()));
        question.setPassage(resolvePassage(request.getPassageId(), question.getSubTest().getId()));

        // Replace options — merge in place so options already referenced by user
        // answers (user_answer_selected_options) are not hard-deleted.
        List<AnswerOption> existing =
                optionRepository.findByQuestionIdOrderByOrderIndexAsc(questionId);
        List<CreateQuestionRequest.AnswerOptionRequest> incoming = resolvedOptions;

        for (int i = 0; i < Math.max(existing.size(), incoming.size()); i++) {
            if (i < existing.size() && i < incoming.size()) {
                AnswerOption option = existing.get(i);
                CreateQuestionRequest.AnswerOptionRequest optReq = incoming.get(i);
                option.setLabel(optReq.getLabel());
                option.setText(optReq.getText());
                option.setTextKy(optReq.getTextKy());
                option.setIsCorrect(Boolean.TRUE.equals(optReq.getIsCorrect()));
                option.setOrderIndex(i);
                optionRepository.save(option);
            } else if (i < incoming.size()) {
                CreateQuestionRequest.AnswerOptionRequest optReq = incoming.get(i);
                optionRepository.save(AnswerOption.builder()
                        .question(question)
                        .label(optReq.getLabel())
                        .text(optReq.getText())
                        .textKy(optReq.getTextKy())
                        .isCorrect(Boolean.TRUE.equals(optReq.getIsCorrect()))
                        .orderIndex(i)
                        .build());
            } else {
                AnswerOption option = existing.get(i);
                if (optionRepository.countUserAnswerReferences(option.getId()) > 0) {
                    throw new AppException(
                            "Нельзя удалить вариант ответа, который уже выбирали пользователи. Отредактируйте его текст вместо удаления.",
                            "Колдонуучулар мурда тандаган жооп вариантын өчүрүүгө болбойт. Өчүрүүнүн ордуна текстин оңдоңуз.");
                }
                optionRepository.delete(option);
            }
        }

        return toAdminQuestionResponse(questionRepository.findById(questionId).orElseThrow());
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new AppException("Вопрос не найден.", "Суроо табылган жок."));
        question.setActive(false);
        questionRepository.save(question);
    }

    // ----- question content helpers -----

    private static synamyk.enums.QuestionType typeOf(CreateQuestionRequest r) {
        return r.getQuestionType() != null ? r.getQuestionType() : synamyk.enums.QuestionType.STANDARD;
    }

    /** Explicit options, or the 4 standard ОРТ comparison options generated from comparisonAnswer. */
    private List<CreateQuestionRequest.AnswerOptionRequest> resolveOptions(CreateQuestionRequest r) {
        List<CreateQuestionRequest.AnswerOptionRequest> options = r.getOptions();
        if ((options == null || options.isEmpty())
                && typeOf(r) == synamyk.enums.QuestionType.COMPARISON && r.getComparisonAnswer() != null) {
            options = comparisonOptions(r.getComparisonAnswer());
        }
        if (options == null || options.size() < 2 || options.size() > 6) {
            throw new AppException("Нужно от 2 до 6 вариантов ответа.", "2ден 6га чейин жооп варианты керек.");
        }
        if (options.stream().noneMatch(o -> Boolean.TRUE.equals(o.getIsCorrect()))) {
            throw new AppException("Хотя бы один вариант должен быть отмечен как правильный.", "Жок дегенде бир туура жооп белгиленүү керек.");
        }
        return options;
    }

    static List<CreateQuestionRequest.AnswerOptionRequest> comparisonOptions(CreateQuestionRequest.ComparisonAnswer answer) {
        String[][] rows = {
                {"А", "Колонка А больше", "Колонка А чоң"},
                {"Б", "Колонка Б больше", "Колонка Б чоң"},
                {"В", "Равны", "Тең"},
                {"Г", "Невозможно определить", "Аныктоо мүмкүн эмес"}
        };
        List<CreateQuestionRequest.AnswerOptionRequest> result = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) {
            CreateQuestionRequest.AnswerOptionRequest o = new CreateQuestionRequest.AnswerOptionRequest();
            o.setLabel(rows[i][0]);
            o.setText(rows[i][1]);
            o.setTextKy(rows[i][2]);
            o.setIsCorrect(i == answer.ordinal());
            o.setOrderIndex(i);
            result.add(o);
        }
        return result;
    }

    private void validateQuestionContent(CreateQuestionRequest r) {
        if (typeOf(r) == synamyk.enums.QuestionType.COMPARISON
                && (isBlank(r.getColumnA()) || isBlank(r.getColumnB()))) {
            throw new AppException(
                    "Для вопроса-сравнения заполните «Колонка А» и «Колонка Б».",
                    "Салыштыруу суроосу үчүн «Колонка А» жана «Колонка Б» толтуруңуз.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private ReadingPassage resolvePassage(Long passageId, Long subTestId) {
        if (passageId == null) return null;
        ReadingPassage passage = passageRepository.findById(passageId)
                .orElseThrow(() -> new AppException("Текст не найден.", "Текст табылган жок."));
        if (!passage.getSubTest().getId().equals(subTestId)) {
            throw new AppException("Текст относится к другому подтесту.", "Текст башка подтестке тиешелүү.");
        }
        return passage;
    }

    // ===== PASSAGES =====

    public List<PassageResponse> getPassages(Long subTestId) {
        return passageRepository.findBySubTestIdOrderByOrderIndexAsc(subTestId).stream()
                .map(this::toPassageResponse)
                .toList();
    }

    @Transactional
    public PassageResponse createPassage(Long subTestId, PassageRequest request) {
        SubTest subTest = subTestRepository.findById(subTestId)
                .orElseThrow(() -> new AppException("Подтест не найден.", "Подтест табылган жок."));
        ReadingPassage passage = ReadingPassage.builder()
                .subTest(subTest)
                .title(request.getTitle())
                .titleKy(request.getTitleKy())
                .text(request.getText())
                .textKy(request.getTextKy())
                .imageUrl(request.getImageUrl())
                .orderIndex(request.getOrderIndex() != null ? request.getOrderIndex() : 0)
                .active(true)
                .build();
        return toPassageResponse(passageRepository.save(passage));
    }

    @Transactional
    public PassageResponse updatePassage(Long passageId, PassageRequest request) {
        ReadingPassage passage = passageRepository.findById(passageId)
                .orElseThrow(() -> new AppException("Текст не найден.", "Текст табылган жок."));
        passage.setTitle(request.getTitle());
        passage.setTitleKy(request.getTitleKy());
        passage.setText(request.getText());
        passage.setTextKy(request.getTextKy());
        passage.setImageUrl(request.getImageUrl());
        if (request.getOrderIndex() != null) passage.setOrderIndex(request.getOrderIndex());
        return toPassageResponse(passageRepository.save(passage));
    }

    /** Soft delete; questions keep their link (history) but the passage is hidden from new runs. */
    @Transactional
    public void deletePassage(Long passageId) {
        ReadingPassage passage = passageRepository.findById(passageId)
                .orElseThrow(() -> new AppException("Текст не найден.", "Текст табылган жок."));
        passage.setActive(false);
        passageRepository.save(passage);
    }

    private PassageResponse toPassageResponse(ReadingPassage p) {
        long questionCount = questionRepository.findBySubTestIdAndActiveTrueOrderByOrderIndexAsc(p.getSubTest().getId())
                .stream().filter(q -> q.getPassage() != null && q.getPassage().getId().equals(p.getId())).count();
        return PassageResponse.builder()
                .id(p.getId())
                .subTestId(p.getSubTest().getId())
                .title(p.getTitle())
                .titleKy(p.getTitleKy())
                .text(p.getText())
                .textKy(p.getTextKy())
                .imageUrl(minioService.presign(p.getImageUrl()))
                .orderIndex(p.getOrderIndex())
                .active(p.getActive())
                .questionCount(questionCount)
                .build();
    }

    // ===== MAPPERS =====

    private AdminTestResponse toAdminTestResponse(Test test) {
        List<SubTest> subTests = subTestRepository.findByTestIdOrderByLevelOrderAsc(test.getId());
        return AdminTestResponse.builder()
                .id(test.getId())
                .title(test.getTitle())
                .titleKy(test.getTitleKy())
                .description(test.getDescription())
                .descriptionKy(test.getDescriptionKy())
                .iconUrl(minioService.presign(test.getIconUrl()))
                .price(test.getPrice())
                .maxScore(test.getMaxScore())
                .freeFrom(test.getFreeFrom())
                .freeUntil(test.getFreeUntil())
                .active(test.getActive())
                .subTests(subTests.stream().map(this::toAdminSubTestResponse).toList())
                .build();
    }

    private AdminTestResponse.AdminSubTestResponse toAdminSubTestResponse(SubTest st) {
        return AdminTestResponse.AdminSubTestResponse.builder()
                .id(st.getId())
                .title(st.getTitle())
                .titleKy(st.getTitleKy())
                .levelName(st.getLevelName())
                .levelNameKy(st.getLevelNameKy())
                .levelOrder(st.getLevelOrder())
                .durationMinutes(st.getDurationMinutes())
                .maxScore(st.getMaxScore())
                .iconUrl(minioService.presign(st.getIconUrl()))
                .questionCount(questionRepository.countBySubTestIdAndActiveTrue(st.getId()))
                .active(st.getActive())
                .build();
    }

    private AdminQuestionResponse toAdminQuestionResponse(Question q) {
        List<AdminQuestionResponse.OptionResponse> options = optionRepository
                .findByQuestionIdOrderByOrderIndexAsc(q.getId()).stream()
                .map(o -> AdminQuestionResponse.OptionResponse.builder()
                        .id(o.getId())
                        .label(o.getLabel())
                        .text(o.getText())
                        .textKy(o.getTextKy())
                        .isCorrect(o.getIsCorrect())
                        .orderIndex(o.getOrderIndex())
                        .build())
                .toList();

        return AdminQuestionResponse.builder()
                .id(q.getId())
                .sectionName(q.getSectionName())
                .sectionNameKy(q.getSectionNameKy())
                .text(q.getText())
                .textKy(q.getTextKy())
                .imageUrl(minioService.presign(q.getImageUrl()))
                .questionType(q.getQuestionType() != null ? q.getQuestionType().name() : "STANDARD")
                .columnA(q.getColumnA())
                .columnAKy(q.getColumnAKy())
                .columnB(q.getColumnB())
                .columnBKy(q.getColumnBKy())
                .figure(figureValidator.fromJson(q.getFigure()))
                .passageId(q.getPassage() != null ? q.getPassage().getId() : null)
                .explanation(q.getExplanation())
                .explanationKy(q.getExplanationKy())
                .orderIndex(q.getOrderIndex())
                .pointValue(q.getPointValue())
                .active(q.getActive())
                .options(options)
                .build();
    }
}