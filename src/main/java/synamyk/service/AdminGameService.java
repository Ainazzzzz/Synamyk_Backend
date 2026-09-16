package synamyk.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import synamyk.dto.game.*;
import synamyk.entities.*;
import synamyk.exception.AppException;
import synamyk.repo.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminGameService {

    private final GameTestRepository gameTestRepository;
    private final GameQuestionRepository gameQuestionRepository;
    private final GameRoomRepository gameRoomRepository;
    private final GamePlayerResultRepository gamePlayerResultRepository;
    private final UserRepository userRepository;
    private final synamyk.util.FigureValidator figureValidator;

    // ===== CRUD for game tests =====

    @Transactional
    public GameTestResponse createGameTest(CreateGameTestRequest req) {
        GameTest test = new GameTest();
        test.setTitle(req.getTitle());
        test.setDescription(req.getDescription());
        test.setTimeLimitSeconds(req.getTimeLimitSeconds() != null ? req.getTimeLimitSeconds() : 30);
        test.setQuestionsPerGame(req.getQuestionsPerGame() != null ? req.getQuestionsPerGame() : 0);
        test.setActive(true);
        test = gameTestRepository.save(test);

        if (req.getQuestions() != null) {
            for (int i = 0; i < req.getQuestions().size(); i++) {
                addQuestion(test, req.getQuestions().get(i), i);
            }
        }
        return toResponse(test, true);
    }

    @Transactional
    public GameTestResponse updateGameTest(Long id, CreateGameTestRequest req) {
        GameTest test = gameTestRepository.findById(id)
                .orElseThrow(() -> new AppException("Игровой тест не найден.", "Оюн тести табылган жок."));
        test.setTitle(req.getTitle());
        test.setDescription(req.getDescription());
        if (req.getTimeLimitSeconds() != null) test.setTimeLimitSeconds(req.getTimeLimitSeconds());
        if (req.getQuestionsPerGame() != null) test.setQuestionsPerGame(req.getQuestionsPerGame());
        gameTestRepository.save(test);
        return toResponse(test, true);
    }

    @Transactional
    public void deleteGameTest(Long id) {
        GameTest test = gameTestRepository.findById(id)
                .orElseThrow(() -> new AppException("Игровой тест не найден.", "Оюн тести табылган жок."));
        test.setActive(false);
        gameTestRepository.save(test);
    }

    public List<GameTestResponse> listAll() {
        return gameTestRepository.findAll().stream()
                .map(t -> toResponse(t, false))
                .collect(Collectors.toList());
    }

    public GameTestResponse getById(Long id) {
        GameTest test = gameTestRepository.findById(id)
                .orElseThrow(() -> new AppException("Игровой тест не найден.", "Оюн тести табылган жок."));
        return toResponse(test, true);
    }

    // ===== Questions =====

    @Transactional
    public GameTestResponse addQuestionToTest(Long testId, CreateGameQuestionRequest req) {
        GameTest test = gameTestRepository.findById(testId)
                .orElseThrow(() -> new AppException("Игровой тест не найден.", "Оюн тести табылган жок."));
        long nextOrder = gameQuestionRepository.countByGameTestIdAndActiveTrue(testId);
        addQuestion(test, req, (int) nextOrder);
        return toResponse(test, true);
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        GameQuestion q = gameQuestionRepository.findById(questionId)
                .orElseThrow(() -> new AppException("Вопрос не найден.", "Суроо табылган жок."));
        q.setActive(false);
        gameQuestionRepository.save(q);
    }

    // ===== Report =====

    @Transactional(readOnly = true)
    public AdminGameReportResponse getReport(Long gameTestId) {
        GameTest test = gameTestRepository.findById(gameTestId)
                .orElseThrow(() -> new AppException("Игровой тест не найден.", "Оюн тести табылган жок."));

        List<GameRoom> rooms = gameRoomRepository.findByGameTestId(gameTestId);
        long totalGames = rooms.stream()
                .filter(r -> r.getPlayer2Id() != null && r.getFinishedAt() != null)
                .count();
        long totalPlayers = gamePlayerResultRepository.countDistinctUsersByGameTestId(gameTestId);

        List<Long> userIds = rooms.stream()
                .flatMap(r -> {
                    List<Long> ids = new ArrayList<>();
                    ids.add(r.getPlayer1Id());
                    if (r.getPlayer2Id() != null) ids.add(r.getPlayer2Id());
                    return ids.stream();
                })
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> nameMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> u.getFirstName() != null
                                ? u.getFirstName() + (u.getLastName() != null ? " " + u.getLastName() : "")
                                : u.getPhone()
                ));

        int qTotal = gameQuestionRepository.findByGameTestIdAndActiveTrue(gameTestId).size();

        List<AdminGameReportEntry> entries = rooms.stream()
                .filter(r -> r.getPlayer2Id() != null && r.getFinishedAt() != null)
                .map(r -> {
                    String p1Name = nameMap.getOrDefault(r.getPlayer1Id(), "—");
                    String p2Name = nameMap.getOrDefault(r.getPlayer2Id(), "—");
                    Long winnerId = r.getPlayer1Score() > r.getPlayer2Score() ? r.getPlayer1Id()
                            : r.getPlayer2Score() > r.getPlayer1Score() ? r.getPlayer2Id() : null;
                    String winnerName = winnerId != null ? nameMap.getOrDefault(winnerId, "—") : "Ничья";
                    return AdminGameReportEntry.builder()
                            .roomId(r.getId())
                            .playedAt(r.getFinishedAt())
                            .player1Name(p1Name)
                            .player2Name(p2Name)
                            .player1Score(r.getPlayer1Score())
                            .player2Score(r.getPlayer2Score())
                            .winnerName(winnerName)
                            .totalQuestions(qTotal)
                            .build();
                })
                .collect(Collectors.toList());

        return AdminGameReportResponse.builder()
                .gameTestId(gameTestId)
                .gameTestTitle(test.getTitle())
                .totalGames(totalGames)
                .totalPlayers(totalPlayers)
                .games(entries)
                .build();
    }

    // ===== helpers =====

    private void addQuestion(GameTest test, CreateGameQuestionRequest req, int defaultOrder) {
        GameQuestion q = new GameQuestion();
        q.setGameTest(test);
        q.setText(req.getText());
        q.setImageUrl(req.getImageUrl());
        q.setFigure(figureValidator.toJson(req.getFigure()));
        q.setOrderIndex(req.getOrderIndex() != null ? req.getOrderIndex() : defaultOrder);
        q.setActive(true);
        q = gameQuestionRepository.save(q);

        List<GameAnswerOption> options = new ArrayList<>();
        for (int i = 0; i < req.getOptions().size(); i++) {
            var opt = req.getOptions().get(i);
            GameAnswerOption o = new GameAnswerOption();
            o.setGameQuestion(q);
            o.setText(opt.getText());
            o.setCorrect(opt.isCorrect());
            o.setOrderIndex(i);
            options.add(o);
        }
        q.setOptions(options);
        gameQuestionRepository.save(q);
    }

    private GameTestResponse toResponse(GameTest test, boolean includeQuestions) {
        long count = gameQuestionRepository.countByGameTestIdAndActiveTrue(test.getId());
        List<GameTestResponse.QuestionDetail> questions = null;
        if (includeQuestions) {
            questions = gameQuestionRepository.findByGameTestId(test.getId()).stream()
                    .map(q -> GameTestResponse.QuestionDetail.builder()
                            .figure(figureValidator.fromJson(q.getFigure()))
                            .id(q.getId())
                            .text(q.getText())
                            .imageUrl(q.getImageUrl())
                            .orderIndex(q.getOrderIndex())
                            .active(q.getActive())
                            .options(q.getOptions().stream()
                                    .map(o -> GameTestResponse.OptionDetail.builder()
                                            .id(o.getId())
                                            .text(o.getText())
                                            .correct(o.getCorrect())
                                            .orderIndex(o.getOrderIndex())
                                            .build())
                                    .collect(Collectors.toList()))
                            .build())
                    .collect(Collectors.toList());
        }
        return GameTestResponse.builder()
                .id(test.getId())
                .title(test.getTitle())
                .description(test.getDescription())
                .timeLimitSeconds(test.getTimeLimitSeconds())
                .questionsPerGame(test.getQuestionsPerGame())
                .active(test.getActive())
                .questionCount(count)
                .questions(questions)
                .build();
    }
}
