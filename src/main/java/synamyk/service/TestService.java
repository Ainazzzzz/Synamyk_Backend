package synamyk.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import synamyk.dto.SubTestResponse;
import synamyk.dto.TestDetailResponse;
import synamyk.dto.TestListResponse;
import synamyk.entities.SubTest;
import synamyk.entities.Test;
import synamyk.entities.TestAttempt;
import synamyk.entities.TestSession;
import synamyk.exception.AppException;
import synamyk.repo.QuestionRepository;
import synamyk.repo.SubTestRepository;
import synamyk.repo.TestAttemptRepository;
import synamyk.repo.TestRepository;
import synamyk.repo.TestSessionRepository;
import synamyk.repo.UserTestAccessRepository;
import synamyk.util.L10n;
import synamyk.util.OrtScoring;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TestService {

    private final TestRepository testRepository;
    private final SubTestRepository subTestRepository;
    private final QuestionRepository questionRepository;
    private final UserTestAccessRepository accessRepository;
    private final TestSessionRepository sessionRepository;
    private final TestAttemptRepository attemptRepository;
    private final MinioService minioService;
    private final AccessResolver accessResolver;

    @Transactional(readOnly = true)
    public List<TestListResponse> getAllTests(Long userId, String lang) {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Object[]> best = new HashMap<>();
        for (Object[] row : attemptRepository.findBestScoresByUser(userId)) {
            best.put(((Number) row[0]).longValue(), row);
        }
        Map<Long, Long> resumable = new HashMap<>();
        for (Object[] row : attemptRepository.findResumableIdsByUser(userId)) {
            resumable.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()); // newest wins
        }

        return testRepository.findByActiveTrueOrderByCreatedAtAsc().stream()
                .map(t -> {
                    List<SubTest> subTests = subTestRepository
                            .findByTestIdAndActiveTrueOrderByLevelOrderAsc(t.getId());
                    int subTestCount = subTests.size();
                    int completed = sessionRepository.findCompletedSubTestCounts(userId, t.getId()).size();
                    int progress = subTestCount > 0 ? (completed * 100) / subTestCount : 0;
                    Map<Long, long[]> stats = questionStats(t.getId());
                    Object[] bestRow = best.get(t.getId());

                    return TestListResponse.builder()
                            .id(t.getId())
                            .title(L10n.pick(t.getTitle(), t.getTitleKy(), lang))
                            .description(L10n.pick(t.getDescription(), t.getDescriptionKy(), lang))
                            .iconUrl(minioService.presign(t.getIconUrl()))
                            .price(t.getPrice())
                            .subTestCount(subTestCount)
                            .completedSubTestCount(Math.min(completed, subTestCount))
                            .progressPercent(Math.min(progress, 100))
                            .totalDurationMinutes(subTests.stream().mapToInt(SubTest::getDurationMinutes).sum())
                            .totalQuestions((int) stats.values().stream().mapToLong(v -> v[0]).sum())
                            .maxScore(t.getMaxScore())
                            .isFree(AccessResolver.isTestFree(t, subTests, now))
                            .hasAccess(!subTests.isEmpty() && accessResolver.hasTestAccess(userId, t, subTests, now))
                            .bestOrtScore(bestRow != null && bestRow[1] != null ? ((Number) bestRow[1]).intValue() : null)
                            .completedAttempts(bestRow != null ? ((Number) bestRow[2]).intValue() : 0)
                            .resumableAttemptId(resumable.get(t.getId()))
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public TestDetailResponse getTestDetail(Long testId, Long userId, String lang) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new AppException("Тест не найден.", "Тест табылган жок."));

        LocalDateTime now = LocalDateTime.now();
        boolean hasPaidAccess = accessRepository.existsActiveAccess(userId, testId, now);
        List<SubTest> active = subTestRepository.findByTestIdAndActiveTrueOrderByLevelOrderAsc(testId);
        Map<Long, long[]> stats = questionStats(testId);

        List<OrtScoring.Section> allocationInput = new ArrayList<>();
        for (SubTest st : active) {
            allocationInput.add(new OrtScoring.Section(st.getId(), st.getMaxScore(),
                    (int) stats.getOrDefault(st.getId(), new long[]{0, 0})[1]));
        }
        Map<Long, Integer> sectionMax = OrtScoring.allocate(
                test.getMaxScore() != null ? test.getMaxScore() : 245, allocationInput);

        List<SubTestResponse> subTests = active.stream()
                .map(st -> {
                    long questionCount = stats.getOrDefault(st.getId(), new long[]{0, 0})[0];
                    List<TestSession> sessions = sessionRepository
                            .findByUserIdAndSubTestIdOrderByCreatedAtDesc(userId, st.getId());

                    TestSession bestSession = sessions.stream()
                            .filter(s -> s.getStatus() == TestSession.SessionStatus.COMPLETED)
                            .filter(s -> s.getEarnedPoints() != null)
                            .max(java.util.Comparator.comparingInt(TestSession::getEarnedPoints))
                            .orElse(null);

                    return SubTestResponse.builder()
                            .id(st.getId())
                            .title(L10n.pick(st.getTitle(), st.getTitleKy(), lang))
                            .levelName(L10n.pick(st.getLevelName(), st.getLevelNameKy(), lang))
                            .levelOrder(st.getLevelOrder())
                            .isPaid(st.getIsPaid())
                            .price(st.getPrice())
                            .durationMinutes(st.getDurationMinutes())
                            .iconUrl(minioService.presign(st.getIconUrl()))
                            .maxScore(sectionMax.get(st.getId()))
                            .questionCount(questionCount)
                            .hasAccess(accessResolver.hasSubTestAccess(userId, st, now))
                            .effectiveFree(accessResolver.isEffectivelyFree(st, now))
                            .freeUntil(accessResolver.freeUntilBoundary(st, now))
                            .hasCompleted(sessions.stream().anyMatch(s -> s.getStatus() == TestSession.SessionStatus.COMPLETED))
                            .bestScore(bestSession != null ? bestSession.getEarnedPoints() : null)
                            .bestSessionId(bestSession != null ? bestSession.getId() : null)
                            .attemptsCount(sessions.size())
                            .build();
                })
                .toList();

        TestAttempt resumable = attemptRepository.findResumable(userId, testId).stream().findFirst().orElse(null);
        Integer bestOrt = attemptRepository.findBestScoresByUser(userId).stream()
                .filter(r -> ((Number) r[0]).longValue() == testId && r[1] != null)
                .map(r -> ((Number) r[1]).intValue())
                .findFirst().orElse(null);

        return TestDetailResponse.builder()
                .id(test.getId())
                .title(L10n.pick(test.getTitle(), test.getTitleKy(), lang))
                .description(L10n.pick(test.getDescription(), test.getDescriptionKy(), lang))
                .iconUrl(minioService.presign(test.getIconUrl()))
                .price(test.getPrice())
                .hasPaidAccess(hasPaidAccess)
                .isFree(AccessResolver.isTestFree(test, active, now))
                .hasAccess(!active.isEmpty() && accessResolver.hasTestAccess(userId, test, active, now))
                .totalDurationMinutes(active.stream().mapToInt(SubTest::getDurationMinutes).sum())
                .totalQuestions((int) stats.values().stream().mapToLong(v -> v[0]).sum())
                .maxScore(test.getMaxScore())
                .bestOrtScore(bestOrt)
                .resumableAttemptId(resumable != null ? resumable.getId() : null)
                .resumableSectionIndex(resumable != null ? resumable.getCurrentSectionIndex() : null)
                .subTests(subTests)
                .build();
    }

    /** subTestId → [activeQuestionCount, totalPoints]. */
    private Map<Long, long[]> questionStats(Long testId) {
        Map<Long, long[]> map = new HashMap<>();
        for (Object[] row : questionRepository.statsByTest(testId)) {
            map.put(((Number) row[0]).longValue(),
                    new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return map;
    }
}
