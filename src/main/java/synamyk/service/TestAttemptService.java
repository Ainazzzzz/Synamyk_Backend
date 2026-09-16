package synamyk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import synamyk.dto.attempt.*;
import synamyk.entities.*;
import synamyk.exception.AppException;
import synamyk.repo.*;
import synamyk.util.L10n;
import synamyk.util.OrtScoring;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A test taken as one whole: sections (sub-tests) go one after another, each with its
 * own timer, and the result is a single ОРТ score with a per-section and per-topic breakdown.
 *
 * <p>Section sessions are created lazily — the timer of a section starts when its questions
 * are first requested. Leaving the app keeps the attempt resumable; «начать заново»
 * abandons it and creates a new one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestAttemptService {

    public static final int ORT_THRESHOLD = 110;

    private final TestRepository testRepository;
    private final SubTestRepository subTestRepository;
    private final QuestionRepository questionRepository;
    private final ReadingPassageRepository passageRepository;
    private final TestAttemptRepository attemptRepository;
    private final TestSessionRepository sessionRepository;
    private final UserAnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final AccessResolver accessResolver;
    private final QuestionPresenter questionPresenter;
    private final MinioService minioService;

    // ===== START / RESUME =====

    @Transactional
    public AttemptStateResponse start(Long testId, Long userId, boolean restart, String lang) {
        Test test = testRepository.findById(testId)
                .filter(Test::getActive)
                .orElseThrow(() -> new AppException("Тест не найден.", "Тест табылган жок."));
        List<SubTest> sections = sections(testId);
        if (sections.isEmpty()) {
            throw new AppException("В тесте нет разделов.", "Тестте бөлүмдөр жок.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!accessResolver.hasTestAccess(userId, test, sections, now)) {
            throw new AppException(
                    "Нет доступа. Пожалуйста, приобретите тест.",
                    "Мүмкүнчүлүк жок. Тестти сатып алыңыз.");
        }

        List<TestAttempt> resumable = attemptRepository.findResumable(userId, testId);
        if (!resumable.isEmpty() && !restart) {
            TestAttempt attempt = resumable.get(0);
            resume(attempt, now);
            log.info("Attempt resumed: attemptId={}, userId={}, section={}", attempt.getId(), userId,
                    attempt.getCurrentSectionIndex());
            return buildState(attempt, sections, true, lang);
        }
        for (TestAttempt old : resumable) {
            abandon(old);
        }

        TestAttempt attempt = attemptRepository.save(TestAttempt.builder()
                .user(userRepository.getReferenceById(userId))
                .test(test)
                .status(TestAttempt.AttemptStatus.IN_PROGRESS)
                .currentSectionIndex(0)
                .startedAt(now)
                .build());
        log.info("Attempt started: attemptId={}, userId={}, testId={}, restart={}", attempt.getId(), userId, testId, restart);
        return buildState(attempt, sections, false, lang);
    }

    @Transactional
    public AttemptStateResponse getState(Long attemptId, Long userId, String lang) {
        TestAttempt attempt = owned(attemptId, userId);
        List<SubTest> sections = sections(attempt.getTest().getId());
        if (isOpen(attempt)) advancePastExpired(attempt, sections);
        return buildState(attempt, sections, false, lang);
    }

    /** Resumable attempt of the user on the test, if any («Продолжить» / «Начать заново» dialog). */
    @Transactional(readOnly = true)
    public Optional<AttemptStateResponse> findResumable(Long testId, Long userId, String lang) {
        List<TestAttempt> resumable = attemptRepository.findResumable(userId, testId);
        if (resumable.isEmpty()) return Optional.empty();
        TestAttempt attempt = resumable.get(0);
        return Optional.of(buildState(attempt, sections(testId), false, lang));
    }

    // ===== SECTION =====

    /** Questions of the current section; starts its timer on first call. */
    @Transactional
    public SectionQuestionsResponse getCurrentSection(Long attemptId, Long userId, String lang) {
        TestAttempt attempt = openAttempt(attemptId, userId);
        List<SubTest> sections = sections(attempt.getTest().getId());
        resume(attempt, LocalDateTime.now());
        advancePastExpired(attempt, sections);
        if (!isOpen(attempt)) {
            throw new AppException("Тест уже завершён.", "Тест аяктады.");
        }

        int index = attempt.getCurrentSectionIndex();
        SubTest subTest = sections.get(index);
        TestSession session = currentOrNewSession(attempt, subTest);

        List<Question> questions = questionRepository.findBySubTestIdAndActiveTrueOrderByOrderIndexAsc(subTest.getId());
        Map<Long, List<Long>> selected = selectedByQuestion(session.getId());

        List<QuestionView> views = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            views.add(questionPresenter.toView(q, i, selected.getOrDefault(q.getId(), List.of()), lang));
        }

        List<PassageView> passages = buildPassages(subTest.getId(), questions, lang);

        return SectionQuestionsResponse.builder()
                .attemptId(attempt.getId())
                .sessionId(session.getId())
                .sectionIndex(index)
                .totalSections(sections.size())
                .isLastSection(index == sections.size() - 1)
                .subTestId(subTest.getId())
                .title(L10n.pick(subTest.getTitle(), subTest.getTitleKy(), lang))
                .durationMinutes(subTest.getDurationMinutes())
                .expiresAt(session.getExpiresAt())
                .remainingSeconds(session.getRemainingSeconds())
                .totalRemainingSeconds(totalRemaining(attempt, sections, session))
                .totalQuestions(questions.size())
                .answeredCount((int) selected.values().stream().filter(l -> !l.isEmpty()).count())
                .passages(passages)
                .questions(views)
                .build();
    }

    /** Save / change / clear the answer to any question of the current section (free navigation). */
    @Transactional
    public SaveAnswerResponse saveAnswer(Long attemptId, Long userId, SaveAnswerRequest request) {
        TestAttempt attempt = openAttempt(attemptId, userId);
        if (attempt.getStatus() == TestAttempt.AttemptStatus.PAUSED) {
            resume(attempt, LocalDateTime.now());
        }
        List<SubTest> sections = sections(attempt.getTest().getId());
        SubTest subTest = sections.get(Math.min(attempt.getCurrentSectionIndex(), sections.size() - 1));
        TestSession session = existingSession(attempt, subTest)
                .filter(s -> s.getStatus() == TestSession.SessionStatus.IN_PROGRESS)
                .orElseThrow(() -> new AppException(
                        "Раздел не начат. Откройте текущий раздел.",
                        "Бөлүм башталган жок. Учурдагы бөлүмдү ачыңыз."));

        if (session.isExpired()) {
            advancePastExpired(attempt, sections);
            throw new AppException(
                    "Время раздела истекло. Ответ не сохранён.",
                    "Бөлүмдүн убактысы бүттү. Жооп сакталган жок.");
        }

        Question question = questionRepository.findById(request.getQuestionId())
                .filter(q -> q.getSubTest().getId().equals(subTest.getId()) && Boolean.TRUE.equals(q.getActive()))
                .orElseThrow(() -> new AppException(
                        "Вопрос не относится к текущему разделу.",
                        "Суроо учурдагы бөлүмгө тиешелүү эмес."));

        List<Long> requested = request.getSelectedOptionIds() == null ? List.of()
                : request.getSelectedOptionIds().stream().distinct().toList();
        List<AnswerOption> selectedOptions = question.getOptions().stream()
                .filter(o -> requested.contains(o.getId()))
                .toList();
        if (selectedOptions.size() != requested.size()) {
            throw new AppException(
                    "Один или несколько выбранных вариантов не найдены.",
                    "Тандалган жооптордун бири же бирнечеси табылган жок.");
        }

        UserAnswer answer = answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId())
                .orElseGet(() -> UserAnswer.builder().session(session).question(question).build());
        answer.setSelectedOptions(new ArrayList<>(selectedOptions));
        answer.setIsSkipped(selectedOptions.isEmpty());
        answer.setIsCorrect(QuestionPresenter.isCorrect(question, selectedOptions));
        answerRepository.save(answer);

        int answered = (int) answerRepository.findBySessionIdOrderByQuestionOrderIndex(session.getId()).stream()
                .filter(a -> !a.getIsSkipped()).count();

        return SaveAnswerResponse.builder()
                .questionId(question.getId())
                .selectedOptionIds(selectedOptions.stream().map(AnswerOption::getId).toList())
                .answeredCount(answered)
                .totalQuestions((int) questionRepository.countBySubTestIdAndActiveTrue(subTest.getId()))
                .remainingSeconds(session.getRemainingSeconds())
                .build();
    }

    /** Finish the current section and move to the next one (or finish the test after the last). */
    @Transactional
    public AttemptStateResponse finishSection(Long attemptId, Long userId, String lang) {
        TestAttempt attempt = openAttempt(attemptId, userId);
        List<SubTest> sections = sections(attempt.getTest().getId());
        SubTest subTest = sections.get(Math.min(attempt.getCurrentSectionIndex(), sections.size() - 1));
        existingSession(attempt, subTest).ifPresent(s -> closeSession(s, TestSession.SessionStatus.COMPLETED));
        moveToNextSection(attempt, sections);
        return buildState(attempt, sections, false, lang);
    }

    @Transactional
    public AttemptStateResponse pause(Long attemptId, Long userId, String lang) {
        TestAttempt attempt = openAttempt(attemptId, userId);
        List<SubTest> sections = sections(attempt.getTest().getId());
        LocalDateTime now = LocalDateTime.now();
        if (attempt.getStatus() == TestAttempt.AttemptStatus.IN_PROGRESS) {
            attempt.setStatus(TestAttempt.AttemptStatus.PAUSED);
            attempt.setPausedAt(now);
            attemptRepository.save(attempt);
            sessionRepository.findByAttemptIdOrderByCreatedAtAsc(attempt.getId()).stream()
                    .filter(s -> s.getStatus() == TestSession.SessionStatus.IN_PROGRESS)
                    .forEach(s -> {
                        s.setStatus(TestSession.SessionStatus.PAUSED);
                        s.setPausedAt(now);
                        sessionRepository.save(s);
                    });
            log.info("Attempt paused: attemptId={}", attemptId);
        }
        return buildState(attempt, sections, false, lang);
    }

    /** Finish the whole test now; unanswered / unstarted sections count as zero. */
    @Transactional
    public AttemptResultResponse finish(Long attemptId, Long userId, String lang) {
        TestAttempt attempt = owned(attemptId, userId);
        if (isOpen(attempt)) {
            sessionRepository.findByAttemptIdOrderByCreatedAtAsc(attempt.getId()).stream()
                    .filter(s -> s.getStatus() == TestSession.SessionStatus.IN_PROGRESS
                            || s.getStatus() == TestSession.SessionStatus.PAUSED)
                    .forEach(s -> closeSession(s, s.isExpired()
                            ? TestSession.SessionStatus.EXPIRED : TestSession.SessionStatus.COMPLETED));
            complete(attempt, sections(attempt.getTest().getId()));
        }
        return buildResult(attempt, lang);
    }

    // ===== RESULT / HISTORY =====

    @Transactional(readOnly = true)
    public AttemptResultResponse getResult(Long attemptId, Long userId, String lang) {
        TestAttempt attempt = owned(attemptId, userId);
        if (attempt.getStatus() != TestAttempt.AttemptStatus.COMPLETED) {
            throw new AppException("Тест ещё не завершён.", "Тест азырынча аяктай элек.");
        }
        return buildResult(attempt, lang);
    }

    @Transactional(readOnly = true)
    public Page<AttemptHistoryEntry> history(Long userId, Pageable pageable, String lang) {
        return attemptRepository.findCompletedByUser(userId, pageable).map(a -> AttemptHistoryEntry.builder()
                .attemptId(a.getId())
                .testId(a.getTest().getId())
                .testTitle(L10n.pick(a.getTest().getTitle(), a.getTest().getTitleKy(), lang))
                .ortScore(a.getOrtScore())
                .maxScore(a.getMaxScore())
                .correctAnswers(a.getCorrectAnswers())
                .totalQuestions(a.getTotalQuestions())
                .percentage(OrtScoring.percentOneDecimal(nz(a.getEarnedPoints()), nz(a.getTotalPoints())))
                .startedAt(a.getStartedAt())
                .completedAt(a.getCompletedAt())
                .build());
    }

    // ===== INTERNALS =====

    private List<SubTest> sections(Long testId) {
        return subTestRepository.findByTestIdAndActiveTrueOrderByLevelOrderAsc(testId);
    }

    private TestAttempt owned(Long attemptId, Long userId) {
        TestAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new AppException("Попытка не найдена.", "Аракет табылган жок."));
        if (!attempt.getUser().getId().equals(userId)) {
            throw new AppException("Нет доступа.", "Мүмкүнчүлүк жок.");
        }
        return attempt;
    }

    private TestAttempt openAttempt(Long attemptId, Long userId) {
        TestAttempt attempt = owned(attemptId, userId);
        if (!isOpen(attempt)) {
            throw new AppException(
                    "Попытка неактивна. Статус: " + attempt.getStatus(),
                    "Аракет активдүү эмес. Статус: " + attempt.getStatus());
        }
        return attempt;
    }

    private static boolean isOpen(TestAttempt a) {
        return a.getStatus() == TestAttempt.AttemptStatus.IN_PROGRESS
                || a.getStatus() == TestAttempt.AttemptStatus.PAUSED;
    }

    /** Un-pause the attempt; a paused section gets its timer extended by the pause length. */
    private void resume(TestAttempt attempt, LocalDateTime now) {
        if (attempt.getStatus() != TestAttempt.AttemptStatus.PAUSED) return;
        attempt.setStatus(TestAttempt.AttemptStatus.IN_PROGRESS);
        attempt.setPausedAt(null);
        attemptRepository.save(attempt);
        sessionRepository.findByAttemptIdOrderByCreatedAtAsc(attempt.getId()).stream()
                .filter(s -> s.getStatus() == TestSession.SessionStatus.PAUSED)
                .forEach(s -> {
                    if (s.getPausedAt() != null) {
                        long paused = Duration.between(s.getPausedAt(), now).getSeconds();
                        s.setExpiresAt(s.getExpiresAt().plusSeconds(Math.max(0, paused)));
                    }
                    s.setPausedAt(null);
                    s.setStatus(TestSession.SessionStatus.IN_PROGRESS);
                    sessionRepository.save(s);
                });
    }

    private void abandon(TestAttempt attempt) {
        attempt.setStatus(TestAttempt.AttemptStatus.ABANDONED);
        attemptRepository.save(attempt);
        sessionRepository.findByAttemptIdOrderByCreatedAtAsc(attempt.getId()).stream()
                .filter(s -> s.getStatus() == TestSession.SessionStatus.IN_PROGRESS
                        || s.getStatus() == TestSession.SessionStatus.PAUSED)
                .forEach(s -> {
                    s.setStatus(TestSession.SessionStatus.ABANDONED);
                    sessionRepository.save(s);
                });
        log.info("Attempt abandoned (restart): attemptId={}", attempt.getId());
    }

    private Optional<TestSession> existingSession(TestAttempt attempt, SubTest subTest) {
        return sessionRepository.findByAttemptIdOrderByCreatedAtAsc(attempt.getId()).stream()
                .filter(s -> s.getSubTest().getId().equals(subTest.getId()))
                .reduce((first, second) -> second);
    }

    private TestSession currentOrNewSession(TestAttempt attempt, SubTest subTest) {
        return existingSession(attempt, subTest).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            TestSession s = sessionRepository.save(TestSession.builder()
                    .user(attempt.getUser())
                    .subTest(subTest)
                    .attempt(attempt)
                    .status(TestSession.SessionStatus.IN_PROGRESS)
                    .currentIndex(0)
                    .startedAt(now)
                    .expiresAt(now.plusMinutes(subTest.getDurationMinutes()))
                    .build());
            log.info("Section started: attemptId={}, subTestId={}, sessionId={}", attempt.getId(), subTest.getId(), s.getId());
            return s;
        });
    }

    /** While the current section's timer has run out, close it and move on. */
    private void advancePastExpired(TestAttempt attempt, List<SubTest> sections) {
        while (isOpen(attempt) && attempt.getStatus() == TestAttempt.AttemptStatus.IN_PROGRESS) {
            SubTest subTest = sections.get(Math.min(attempt.getCurrentSectionIndex(), sections.size() - 1));
            Optional<TestSession> session = existingSession(attempt, subTest);
            if (session.isEmpty() || session.get().getStatus() != TestSession.SessionStatus.IN_PROGRESS
                    || !session.get().isExpired()) {
                return;
            }
            closeSession(session.get(), TestSession.SessionStatus.EXPIRED);
            moveToNextSection(attempt, sections);
        }
    }

    private void moveToNextSection(TestAttempt attempt, List<SubTest> sections) {
        int next = attempt.getCurrentSectionIndex() + 1;
        if (next >= sections.size()) {
            complete(attempt, sections);
        } else {
            attempt.setCurrentSectionIndex(next);
            attemptRepository.save(attempt);
        }
    }

    private void closeSession(TestSession session, TestSession.SessionStatus status) {
        List<UserAnswer> answers = answerRepository.findBySessionIdOrderByQuestionOrderIndex(session.getId());
        session.setCorrectAnswers((int) answers.stream().filter(UserAnswer::getIsCorrect).count());
        session.setEarnedPoints(answers.stream().filter(UserAnswer::getIsCorrect)
                .mapToInt(a -> a.getQuestion().getPointValue()).sum());
        session.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        session.setCompletedAt(status == TestSession.SessionStatus.EXPIRED && session.getExpiresAt().isBefore(now)
                ? session.getExpiresAt() : now);
        session.setPausedAt(null);
        sessionRepository.save(session);
    }

    private void complete(TestAttempt attempt, List<SubTest> sections) {
        Score score = score(attempt, sections);
        attempt.setStatus(TestAttempt.AttemptStatus.COMPLETED);
        attempt.setCompletedAt(LocalDateTime.now());
        attempt.setPausedAt(null);
        attempt.setCurrentSectionIndex(sections.size());
        attempt.setCorrectAnswers(score.correct);
        attempt.setTotalQuestions(score.totalQuestions);
        attempt.setEarnedPoints(score.earned);
        attempt.setTotalPoints(score.totalPoints);
        attempt.setOrtScore(score.ortScore);
        attempt.setMaxScore(score.maxScore);
        attemptRepository.save(attempt);
        log.info("Attempt completed: attemptId={}, ortScore={}/{}", attempt.getId(), score.ortScore, score.maxScore);
    }

    // ----- scoring -----

    private static final class Score {
        int correct, wrong, skipped, totalQuestions, earned, totalPoints, ortScore, maxScore;
        final List<AttemptResultResponse.SectionResult> sections = new ArrayList<>();
        final List<AttemptResultResponse.TopicResult> topics = new ArrayList<>();
    }

    private Score score(TestAttempt attempt, List<SubTest> sections) {
        return score(attempt, sections, "RU");
    }

    private Score score(TestAttempt attempt, List<SubTest> sections, String lang) {
        Score score = new Score();
        Map<Long, TestSession> sessionBySubTest = new HashMap<>();
        for (TestSession s : sessionRepository.findByAttemptIdOrderByCreatedAtAsc(attempt.getId())) {
            sessionBySubTest.put(s.getSubTest().getId(), s);
        }

        Map<Long, List<Question>> questionsBySection = new LinkedHashMap<>();
        List<OrtScoring.Section> allocationInput = new ArrayList<>();
        for (SubTest st : sections) {
            List<Question> qs = questionRepository.findBySubTestIdAndActiveTrueOrderByOrderIndexAsc(st.getId());
            questionsBySection.put(st.getId(), qs);
            allocationInput.add(new OrtScoring.Section(st.getId(), st.getMaxScore(),
                    qs.stream().mapToInt(Question::getPointValue).sum()));
        }
        int testMax = attempt.getTest().getMaxScore() != null ? attempt.getTest().getMaxScore() : 245;
        Map<Long, Integer> sectionMax = OrtScoring.allocate(testMax, allocationInput);

        // topic key = subTestId + topic name, insertion-ordered
        Map<String, int[]> topicCounts = new LinkedHashMap<>();
        Map<String, AttemptResultResponse.TopicResult.TopicResultBuilder> topicMeta = new LinkedHashMap<>();

        for (SubTest st : sections) {
            List<Question> qs = questionsBySection.get(st.getId());
            TestSession session = sessionBySubTest.get(st.getId());
            Map<Long, UserAnswer> answers = session == null ? Map.of()
                    : answerRepository.findBySessionIdOrderByQuestionOrderIndex(session.getId()).stream()
                    .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a, (a, b) -> b));

            int correct = 0, wrong = 0, skipped = 0, earned = 0, total = 0;
            String sectionTitle = L10n.pick(st.getTitle(), st.getTitleKy(), lang);
            for (Question q : qs) {
                total += q.getPointValue();
                UserAnswer a = answers.get(q.getId());
                boolean isCorrect = a != null && Boolean.TRUE.equals(a.getIsCorrect());
                boolean isSkipped = a == null || Boolean.TRUE.equals(a.getIsSkipped());
                if (isCorrect) { correct++; earned += q.getPointValue(); }
                else if (isSkipped) skipped++;
                else wrong++;

                String topic = L10n.pick(q.getSectionName(), q.getSectionNameKy(), lang);
                if (topic == null || topic.isBlank()) topic = sectionTitle;
                String key = st.getId() + "|" + topic;
                int[] c = topicCounts.computeIfAbsent(key, k -> new int[2]);
                c[0]++;
                if (isCorrect) c[1]++;
                String topicName = topic;
                topicMeta.computeIfAbsent(key, k -> AttemptResultResponse.TopicResult.builder()
                        .topic(topicName).subTestId(st.getId()).sectionTitle(sectionTitle));
            }

            int max = sectionMax.getOrDefault(st.getId(), 0);
            int ort = OrtScoring.scale(earned, total, max);
            String status = session == null ? "NOT_STARTED"
                    : session.getStatus() == TestSession.SessionStatus.EXPIRED ? "EXPIRED" : "COMPLETED";

            score.sections.add(AttemptResultResponse.SectionResult.builder()
                    .subTestId(st.getId())
                    .title(sectionTitle)
                    .sessionId(session != null ? session.getId() : null)
                    .status(status)
                    .totalQuestions(qs.size())
                    .correctAnswers(correct)
                    .wrongAnswers(wrong)
                    .skippedAnswers(skipped)
                    .earnedPoints(earned)
                    .totalPoints(total)
                    .percentage(OrtScoring.percentOneDecimal(earned, total))
                    .ortScore(ort)
                    .maxScore(max)
                    .build());

            score.correct += correct;
            score.wrong += wrong;
            score.skipped += skipped;
            score.totalQuestions += qs.size();
            score.earned += earned;
            score.totalPoints += total;
            score.ortScore += ort;
            score.maxScore += max;
        }

        topicCounts.forEach((key, c) -> score.topics.add(topicMeta.get(key)
                .totalQuestions(c[0])
                .correctAnswers(c[1])
                .percentage(OrtScoring.percentOneDecimal(c[1], c[0]))
                .build()));
        return score;
    }

    // ----- views -----

    private AttemptResultResponse buildResult(TestAttempt attempt, String lang) {
        List<SubTest> sections = sections(attempt.getTest().getId());
        Score score = score(attempt, sections, lang);
        // The stored score is authoritative (content may be edited later); breakdown is recomputed.
        int ortScore = attempt.getOrtScore() != null ? attempt.getOrtScore() : score.ortScore;
        int maxScore = attempt.getMaxScore() != null ? attempt.getMaxScore() : score.maxScore;
        User user = attempt.getUser();
        String fullName = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();

        long timeTaken = attempt.getCompletedAt() == null ? 0
                : sessionRepository.findByAttemptIdOrderByCreatedAtAsc(attempt.getId()).stream()
                .filter(s -> s.getCompletedAt() != null)
                .mapToLong(s -> Math.max(0, Duration.between(s.getStartedAt(), s.getCompletedAt()).getSeconds()))
                .sum();

        return AttemptResultResponse.builder()
                .attemptId(attempt.getId())
                .testId(attempt.getTest().getId())
                .testTitle(L10n.pick(attempt.getTest().getTitle(), attempt.getTest().getTitleKy(), lang))
                .userFullName(fullName.isEmpty() ? null : fullName)
                .completedAt(attempt.getCompletedAt())
                .ortScore(ortScore)
                .maxScore(maxScore)
                .thresholdScore(ORT_THRESHOLD)
                .passedThreshold(ortScore >= ORT_THRESHOLD)
                .totalQuestions(score.totalQuestions)
                .correctAnswers(score.correct)
                .wrongAnswers(score.wrong)
                .skippedAnswers(score.skipped)
                .earnedPoints(score.earned)
                .totalPoints(score.totalPoints)
                .percentage(OrtScoring.percentOneDecimal(score.earned, score.totalPoints))
                .timeTakenSeconds(timeTaken)
                .motivationalMessage(motivation(ortScore, maxScore, lang))
                .sections(score.sections)
                .topics(score.topics)
                .build();
    }

    private AttemptStateResponse buildState(TestAttempt attempt, List<SubTest> sections, boolean resumed, String lang) {
        Map<Long, TestSession> sessionBySubTest = new HashMap<>();
        for (TestSession s : sessionRepository.findByAttemptIdOrderByCreatedAtAsc(attempt.getId())) {
            sessionBySubTest.put(s.getSubTest().getId(), s);
        }
        Map<Long, long[]> stats = questionStats(attempt.getTest().getId());

        List<AttemptStateResponse.SectionState> states = new ArrayList<>();
        int totalDuration = 0, totalQuestions = 0;
        long totalRemaining = 0;
        for (int i = 0; i < sections.size(); i++) {
            SubTest st = sections.get(i);
            TestSession s = sessionBySubTest.get(st.getId());
            long remaining;
            String status;
            int answered = 0;
            if (s == null) {
                status = "NOT_STARTED";
                remaining = isOpen(attempt) ? st.getDurationMinutes() * 60L : 0;
            } else {
                status = s.getStatus().name();
                boolean running = s.getStatus() == TestSession.SessionStatus.IN_PROGRESS
                        || s.getStatus() == TestSession.SessionStatus.PAUSED;
                remaining = running ? remainingOf(s) : 0;
                answered = (int) answerRepository.findBySessionIdOrderByQuestionOrderIndex(s.getId()).stream()
                        .filter(a -> !a.getIsSkipped()).count();
            }
            int qCount = (int) stats.getOrDefault(st.getId(), new long[]{0, 0})[0];
            totalDuration += st.getDurationMinutes();
            totalQuestions += qCount;
            totalRemaining += remaining;
            states.add(AttemptStateResponse.SectionState.builder()
                    .index(i)
                    .subTestId(st.getId())
                    .title(L10n.pick(st.getTitle(), st.getTitleKy(), lang))
                    .iconUrl(minioService.presign(st.getIconUrl()))
                    .durationMinutes(st.getDurationMinutes())
                    .questionCount(qCount)
                    .status(status)
                    .answeredCount(answered)
                    .remainingSeconds(remaining)
                    .build());
        }

        return AttemptStateResponse.builder()
                .attemptId(attempt.getId())
                .testId(attempt.getTest().getId())
                .testTitle(L10n.pick(attempt.getTest().getTitle(), attempt.getTest().getTitleKy(), lang))
                .status(attempt.getStatus().name())
                .isResumed(resumed)
                .startedAt(attempt.getStartedAt())
                .totalDurationMinutes(totalDuration)
                .totalQuestions(totalQuestions)
                .maxScore(attempt.getTest().getMaxScore())
                .currentSectionIndex(Math.min(attempt.getCurrentSectionIndex(), sections.size()))
                .totalSections(sections.size())
                .totalRemainingSeconds(totalRemaining)
                .sections(states)
                .build();
    }

    /** Remaining seconds of a section; a paused one is frozen at its pause moment. */
    private static long remainingOf(TestSession s) {
        LocalDateTime ref = s.getStatus() == TestSession.SessionStatus.PAUSED && s.getPausedAt() != null
                ? s.getPausedAt() : LocalDateTime.now();
        return Math.max(0, Duration.between(ref, s.getExpiresAt()).getSeconds());
    }

    private long totalRemaining(TestAttempt attempt, List<SubTest> sections, TestSession current) {
        long total = current.getRemainingSeconds();
        for (int i = attempt.getCurrentSectionIndex() + 1; i < sections.size(); i++) {
            total += sections.get(i).getDurationMinutes() * 60L;
        }
        return total;
    }

    private Map<Long, long[]> questionStats(Long testId) {
        Map<Long, long[]> map = new HashMap<>();
        for (Object[] row : questionRepository.statsByTest(testId)) {
            map.put(((Number) row[0]).longValue(),
                    new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return map;
    }

    private Map<Long, List<Long>> selectedByQuestion(Long sessionId) {
        Map<Long, List<Long>> map = new HashMap<>();
        for (UserAnswer a : answerRepository.findBySessionIdOrderByQuestionOrderIndex(sessionId)) {
            map.put(a.getQuestion().getId(), a.getSelectedOptions().stream().map(AnswerOption::getId).toList());
        }
        return map;
    }

    private List<PassageView> buildPassages(Long subTestId, List<Question> questions, String lang) {
        List<ReadingPassage> passages = passageRepository.findBySubTestIdAndActiveTrueOrderByOrderIndexAsc(subTestId);
        List<PassageView> views = new ArrayList<>();
        int number = 1;
        for (ReadingPassage p : passages) {
            List<Long> questionIds = questions.stream()
                    .filter(q -> q.getPassage() != null && q.getPassage().getId().equals(p.getId()))
                    .map(Question::getId)
                    .toList();
            if (questionIds.isEmpty()) continue;
            views.add(PassageView.builder()
                    .id(p.getId())
                    .number(number++)
                    .title(L10n.pick(p.getTitle(), p.getTitleKy(), lang))
                    .text(L10n.pick(p.getText(), p.getTextKy(), lang))
                    .imageUrl(minioService.presign(p.getImageUrl()))
                    .questionIds(questionIds)
                    .build());
        }
        return views;
    }

    private static String motivation(int score, int max, String lang) {
        boolean ky = "KY".equalsIgnoreCase(lang);
        int pct = OrtScoring.percent(score, max);
        if (pct >= 80) return ky ? "Мыкты! Грантка татыктуу жыйынтык!" : "Отлично! Результат на уровне гранта!";
        if (score >= ORT_THRESHOLD) return ky ? "Босогодон өттүң! Дагы бир аз машык — грант жакын!"
                : "Порог пройден! Ещё немного практики — и грант близко!";
        return ky ? "Баш тартпа! Ар бир тест сени күчтүүрөөк кылат!" : "Не сдавайся! Каждый тест делает тебя сильнее!";
    }

    private static int nz(Integer v) {
        return v != null ? v : 0;
    }
}
