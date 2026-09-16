package synamyk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import synamyk.dto.game.GameEvent;
import synamyk.dto.game.GameHistoryEntry;
import synamyk.dto.game.GameStateResponse;
import synamyk.dto.game.JoinGameResponse;
import synamyk.entities.*;
import synamyk.enums.GameRoomStatus;
import synamyk.exception.AppException;
import synamyk.repo.*;

import org.springframework.data.domain.PageRequest;
import synamyk.enums.PushCategory;
import synamyk.enums.PushDataType;
import synamyk.util.PushMessages;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private static final Long BOT_ID = -1L;
    private static final List<String> BOT_NAMES = List.of(
            "Алтын", "Бекзат", "Нурлан", "Айгуль", "Дамир",
            "Зарина", "Тимур", "Асель", "Адиль", "Гульнара"
    );

    private final SimpMessagingTemplate messaging;
    private final GameTestRepository gameTestRepository;
    private final GameQuestionRepository gameQuestionRepository;
    private final GameAnswerOptionRepository gameAnswerOptionRepository;
    private final GameRoomRepository gameRoomRepository;
    private final GamePlayerResultRepository gamePlayerResultRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final PushNotificationService pushNotificationService;
    private final GameRatingService gameRatingService;
    private final synamyk.util.FigureValidator figureValidator;

    /** gameTestId -> roomId waiting for second player */
    private final ConcurrentHashMap<Long, Long> waitingRooms = new ConcurrentHashMap<>();
    /** gameTestId -> scheduled bot activation timer */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> botTimers = new ConcurrentHashMap<>();
    /** roomId -> bot display name (populated before startGame) */
    private final ConcurrentHashMap<Long, String> botRoomNames = new ConcurrentHashMap<>();
    /** roomId -> active game state */
    private final ConcurrentHashMap<Long, ActiveGameState> activeGames = new ConcurrentHashMap<>();

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    // ===== Public REST API =====

    public synchronized JoinGameResponse joinGame(Long gameTestId, Long userId) {
        GameTest gameTest = gameTestRepository.findById(gameTestId)
                .orElseThrow(() -> new AppException("Игровой тест не найден.", "Оюн тести табылган жок."));
        if (!gameTest.getActive())
            throw new AppException("Игровой тест неактивен.", "Оюн тести активдүү эмес.");
        if (gameQuestionRepository.countByGameTestIdAndActiveTrue(gameTestId) == 0)
            throw new AppException("В игровом тесте нет вопросов.", "Оюн тестинде суроолор жок.");

        Long waitingRoomId = waitingRooms.get(gameTestId);
        if (waitingRoomId != null) {
            Optional<GameRoom> roomOpt = gameRoomRepository.findById(waitingRoomId);
            if (roomOpt.isPresent()) {
                GameRoom room = roomOpt.get();
                if (room.getStatus() == GameRoomStatus.WAITING) {
                    if (room.getPlayer1Id().equals(userId)) {
                        // Same user calling join again while already waiting in this room —
                        // return the existing room as-is. Do NOT cancel its bot timer or
                        // create a new room: that would orphan the room the client already
                        // has the id for (and is likely subscribed to over WebSocket).
                        return JoinGameResponse.builder()
                                .status("WAITING")
                                .roomId(room.getId())
                                .message("Ожидание соперника...")
                                .build();
                    }

                    // Cancel bot timer — real player joined in time
                    ScheduledFuture<?> timer = botTimers.remove(gameTestId);
                    if (timer != null) timer.cancel(false);

                    room.setPlayer2Id(userId);
                    room.setStatus(GameRoomStatus.IN_PROGRESS);
                    room.setStartedAt(LocalDateTime.now());
                    gameRoomRepository.save(room);
                    waitingRooms.remove(gameTestId);

                    Long roomId = room.getId();
                    scheduler.schedule(() -> startGame(roomId), 2, TimeUnit.SECONDS);

                    return JoinGameResponse.builder()
                            .status("MATCHED")
                            .roomId(roomId)
                            .message("Соперник найден! Игра начинается...")
                            .build();
                }
            }
            // Stale entry (room already finished/abandoned by other means, or gone) — clean up
            waitingRooms.remove(gameTestId);
            ScheduledFuture<?> stale = botTimers.remove(gameTestId);
            if (stale != null) stale.cancel(false);
        }

        // No opponent — create waiting room
        GameRoom room = new GameRoom();
        room.setGameTest(gameTest);
        room.setPlayer1Id(userId);
        room.setStatus(GameRoomStatus.WAITING);
        room = gameRoomRepository.save(room);
        Long roomId = room.getId();
        waitingRooms.put(gameTestId, roomId);

        // Schedule bot activation after 15 seconds
        ScheduledFuture<?> botTimer = scheduler.schedule(
                () -> activateBot(roomId, gameTestId), 15, TimeUnit.SECONDS);
        botTimers.put(gameTestId, botTimer);

        return JoinGameResponse.builder()
                .status("WAITING")
                .roomId(roomId)
                .message("Ожидание соперника...")
                .build();
    }

    public synchronized void leaveQueue(Long gameTestId, Long userId) {
        Long roomId = waitingRooms.get(gameTestId);
        if (roomId == null) return;
        gameRoomRepository.findById(roomId).ifPresent(room -> {
            if (room.getPlayer1Id().equals(userId) && room.getStatus() == GameRoomStatus.WAITING) {
                room.setStatus(GameRoomStatus.ABANDONED);
                gameRoomRepository.save(room);
                waitingRooms.remove(gameTestId);
                ScheduledFuture<?> timer = botTimers.remove(gameTestId);
                if (timer != null) timer.cancel(false);
            }
        });
    }

    // ===== WebSocket — answer submission =====

    public void submitAnswer(Long roomId, Long userId, Long optionId) {
        ActiveGameState state = activeGames.get(roomId);
        if (state == null) {
            log.warn("submitAnswer: room {} not active", roomId);
            return;
        }
        synchronized (state.lock) {
            if (state.currentAnswers.containsKey(userId)) return;

            GameAnswerOption option = gameAnswerOptionRepository.findById(optionId).orElse(null);
            if (option == null) return;

            boolean correct = Boolean.TRUE.equals(option.getCorrect());
            state.currentAnswers.put(userId, optionId);

            if (correct) {
                if (userId.equals(state.player1Id)) state.player1Score++;
                else state.player2Score++;
            }

            // Only send answer result to real players
            if (!userId.equals(BOT_ID)) {
                GameEvent ack = GameEvent.builder()
                        .type("ANSWER_RESULT")
                        .roomId(roomId)
                        .correct(correct)
                        .player1Score(state.player1Score)
                        .player2Score(state.player2Score)
                        .build();
                messaging.convertAndSend("/topic/game/" + roomId + "/answers/" + userId, ack);
            }

            // Both answered → advance
            if (state.currentAnswers.size() == 2) {
                if (state.questionTimer != null) state.questionTimer.cancel(false);
                scheduler.schedule(() -> advanceQuestion(state), 1500, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Snapshot of a room's current state, for a client resyncing after a WebSocket reconnect.
     */
    public GameStateResponse getGameState(Long roomId, Long userId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new AppException("Комната не найдена.", "Бөлмө табылган жок."));

        boolean isParticipant = userId.equals(room.getPlayer1Id()) || userId.equals(room.getPlayer2Id());
        if (!isParticipant) {
            throw new AppException("Нет доступа.", "Мүмкүнчүлүк жок.");
        }

        if (room.getStatus() == GameRoomStatus.WAITING) {
            return GameStateResponse.builder().status("WAITING").roomId(roomId).build();
        }

        if (room.getStatus() == GameRoomStatus.IN_PROGRESS) {
            ActiveGameState state = activeGames.get(roomId);
            if (state == null) {
                // In-memory game state lost (e.g. server restart mid-game) — don't leave the client hanging forever.
                room.setStatus(GameRoomStatus.ABANDONED);
                gameRoomRepository.save(room);
                return GameStateResponse.builder().status("ABANDONED").roomId(roomId).build();
            }

            synchronized (state.lock) {
                GameQuestion q = state.currentQuestionIndex < state.questions.size()
                        ? state.questions.get(state.currentQuestionIndex) : null;

                GameEvent.QuestionPayload questionPayload = null;
                Integer remaining = null;
                if (q != null) {
                    questionPayload = questionPayload(state, q);

                    long elapsed = state.questionStartedAt == null ? 0
                            : java.time.Duration.between(state.questionStartedAt, java.time.Instant.now()).getSeconds();
                    remaining = (int) Math.max(0, state.timeLimitSeconds - elapsed);
                }

                Long yourOptionId = state.currentAnswers.get(userId);
                Boolean yourCorrect = null;
                if (yourOptionId != null) {
                    yourCorrect = gameAnswerOptionRepository.findById(yourOptionId)
                            .map(o -> Boolean.TRUE.equals(o.getCorrect()))
                            .orElse(null);
                }

                return GameStateResponse.builder()
                        .status("IN_PROGRESS")
                        .roomId(roomId)
                        .player1Id(state.player1Id)
                        .player2Id(state.player2Id)
                        .player1Name(state.player1Name)
                        .player2Name(state.player2Name)
                        .player1Avatar(state.player1Avatar)
                        .player2Avatar(state.player2Avatar)
                        .player1Score(state.player1Score)
                        .player2Score(state.player2Score)
                        .questionIndex(state.currentQuestionIndex)
                        .totalQuestions(state.questions.size())
                        .timeLimitSeconds(state.timeLimitSeconds)
                        .remainingSeconds(remaining)
                        .question(questionPayload)
                        .youAnswered(yourOptionId != null)
                        .yourAnswerCorrect(yourCorrect)
                        .build();
            }
        }

        // FINISHED or ABANDONED — DB is the source of truth, in-memory state is already gone
        return GameStateResponse.builder()
                .status(room.getStatus().name())
                .roomId(roomId)
                .player1Id(room.getPlayer1Id())
                .player2Id(room.getPlayer2Id())
                .player1Score(room.getPlayer1Score())
                .player2Score(room.getPlayer2Score())
                .winnerId(room.getWinnerId())
                .build();
    }

    public List<GameTestSummary> listActiveGameTests(Long userId, Boolean unplayedOnly) {
        Map<Long, GamePlayerResultRepository.GameStatsView> statsByTest =
                gamePlayerResultRepository.findMyGameStats(userId).stream()
                        .collect(Collectors.toMap(
                                GamePlayerResultRepository.GameStatsView::getGameTestId, v -> v));
        return gameTestRepository.findByActiveTrue().stream()
                .map(t -> {
                    GamePlayerResultRepository.GameStatsView st = statsByTest.get(t.getId());
                    return new GameTestSummary(
                            t.getId(), t.getTitle(), t.getDescription(),
                            t.getTimeLimitSeconds(), t.getQuestionsPerGame(),
                            gameQuestionRepository.countByGameTestIdAndActiveTrue(t.getId()),
                            st != null,
                            st != null && st.getBestScore() != null ? st.getBestScore().longValue() : null,
                            st != null ? st.getLastPlayedAt() : null,
                            st != null && st.getPlayCount() != null ? Math.toIntExact(st.getPlayCount()) : 0);
                })
                .filter(s -> !Boolean.TRUE.equals(unplayedOnly) || !s.played())
                .collect(Collectors.toList());
    }

    /**
     * Current user's completed-game history, newest first.
     */
    public List<GameHistoryEntry> getMyHistory(Long userId) {
        return gamePlayerResultRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(r -> {
                    GameRoom room = gameRoomRepository.findById(r.getRoomId()).orElse(null);
                    GameTest gameTest = gameTestRepository.findById(r.getGameTestId()).orElse(null);

                    boolean vsBot = room != null && BOT_ID.equals(room.getPlayer2Id());
                    boolean draw = room != null && room.getStatus() == GameRoomStatus.FINISHED
                            && room.getWinnerId() == null;

                    Integer opponentScore = null;
                    String opponentName = null;
                    String opponentAvatar = null;

                    if (room != null) {
                        boolean isPlayer1 = userId.equals(room.getPlayer1Id());
                        opponentScore = isPlayer1 ? room.getPlayer2Score() : room.getPlayer1Score();

                        if (vsBot) {
                            opponentName = "Бот";
                        } else {
                            Long opponentId = isPlayer1 ? room.getPlayer2Id() : room.getPlayer1Id();
                            if (opponentId != null) {
                                User opponent = userRepository.findById(opponentId).orElse(null);
                                if (opponent != null) {
                                    opponentName = displayName(opponent);
                                    opponentAvatar = minioService.presign(opponent.getAvatarUrl());
                                }
                            }
                        }
                    }

                    return GameHistoryEntry.builder()
                            .roomId(r.getRoomId())
                            .gameTestId(r.getGameTestId())
                            .gameTestTitle(gameTest != null ? gameTest.getTitle() : null)
                            .score(r.getScore())
                            .opponentScore(opponentScore)
                            .totalQuestions(r.getTotalQuestions())
                            .won(r.getWon())
                            .ratingChange(r.getRatingBefore() != null && r.getRatingAfter() != null
                                    ? r.getRatingAfter() - r.getRatingBefore() : null)
                            .ratingAfter(r.getRatingAfter())
                            .draw(draw)
                            .vsBot(vsBot)
                            .forfeited(room != null && room.getForfeitedBy() != null)
                            .opponentName(opponentName)
                            .opponentAvatar(opponentAvatar)
                            .playedAt(room != null && room.getFinishedAt() != null ? room.getFinishedAt() : r.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ===== Bot activation (runs after 15s if no real opponent) =====

    private void activateBot(Long roomId, Long gameTestId) {
        try {
            GameRoom room = gameRoomRepository.findById(roomId).orElse(null);
            if (room == null || room.getStatus() != GameRoomStatus.WAITING) return;

            waitingRooms.remove(gameTestId);
            botTimers.remove(gameTestId);

            // Pick a random bot name
            String botName = BOT_NAMES.get(RANDOM.nextInt(BOT_NAMES.size()));
            botRoomNames.put(roomId, botName);

            room.setPlayer2Id(BOT_ID);
            room.setStatus(GameRoomStatus.IN_PROGRESS);
            room.setStartedAt(LocalDateTime.now());
            gameRoomRepository.save(room);

            log.debug("Bot '{}' activated for room {}", botName, roomId);
            scheduler.schedule(() -> startGame(roomId), 500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("activateBot error for room {}: {}", roomId, e.getMessage(), e);
        }
    }

    // ===== Game lifecycle =====

    private void startGame(Long roomId) {
        try {
            GameRoom room = gameRoomRepository.findById(roomId).orElseThrow();
            // room.getGameTest() is a LAZY association loaded outside any request/transaction here
            // (this runs on the scheduler thread) — fetch it explicitly instead of touching the proxy.
            GameTest gameTest = gameTestRepository.findById(room.getGameTest().getId()).orElseThrow();

            List<GameQuestion> questions = new ArrayList<>(
                    gameQuestionRepository.findByGameTestIdAndActiveTrue(gameTest.getId()));
            Collections.shuffle(questions, RANDOM);

            int limit = gameTest.getQuestionsPerGame();
            if (limit > 0 && limit < questions.size()) {
                questions = questions.subList(0, limit);
            }

            User p1 = userRepository.findById(room.getPlayer1Id()).orElseThrow();
            boolean isBotGame = BOT_ID.equals(room.getPlayer2Id());
            String p2Name = isBotGame
                    ? botRoomNames.getOrDefault(roomId, "Соперник")
                    : displayName(userRepository.findById(room.getPlayer2Id()).orElseThrow());
            String p2Avatar = isBotGame ? null
                    : userRepository.findById(room.getPlayer2Id()).map(User::getAvatarUrl).orElse(null);

            ActiveGameState state = new ActiveGameState();
            state.roomId = roomId;
            state.gameTestId = gameTest.getId();
            state.player1Id = room.getPlayer1Id();
            state.player2Id = room.getPlayer2Id();
            state.player1Name = displayName(p1);
            state.player2Name = p2Name;
            state.player1Avatar = p1.getAvatarUrl();
            state.player2Avatar = p2Avatar;
            state.questions = questions;
            state.timeLimitSeconds = gameTest.getTimeLimitSeconds();
            state.isBotGame = isBotGame;
            // Shuffle answer options for every question so the position of the right answer can't be memorised.
            for (GameQuestion gq : questions) {
                List<GameEvent.OptionPayload> opts = gq.getOptions().stream()
                        .map(o -> new GameEvent.OptionPayload(o.getId(), o.getText()))
                        .collect(Collectors.toCollection(ArrayList::new));
                Collections.shuffle(opts, RANDOM);
                state.shuffledOptions.put(gq.getId(), opts);
            }
            if (isBotGame) {
                // The bot plays at a random strength each game, so it wins some and loses some.
                state.botAccuracy = 0.35 + RANDOM.nextDouble() * 0.5;
                int playerRating = gameRatingService.currentRating(p1.getId());
                state.botRating = Math.max(GameRatingService.MIN_RATING, playerRating - 150 + RANDOM.nextInt(301));
            }
            activeGames.put(roomId, state);

            GameEvent started = GameEvent.builder()
                    .type("GAME_STARTED")
                    .roomId(roomId)
                    .player1Id(p1.getId())
                    .player2Id(isBotGame ? BOT_ID : room.getPlayer2Id())
                    .player1Name(displayName(p1))
                    .player2Name(p2Name)
                    .player1Avatar(p1.getAvatarUrl())
                    .player2Avatar(p2Avatar)
                    .player1Score(0)
                    .player2Score(0)
                    .build();
            messaging.convertAndSend("/topic/game/" + roomId, started);

            scheduler.schedule(() -> sendNextQuestion(state), 500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("startGame error for room {}: {}", roomId, e.getMessage(), e);
        }
    }

    private void sendNextQuestion(ActiveGameState state) {
        synchronized (state.lock) {
            if (state.currentQuestionIndex >= state.questions.size()) {
                finishGame(state);
                return;
            }

            GameQuestion q = state.questions.get(state.currentQuestionIndex);
            state.currentAnswers.clear();
            state.questionStartedAt = java.time.Instant.now();

            GameEvent event = GameEvent.builder()
                    .type("NEXT_QUESTION")
                    .roomId(state.roomId)
                    .questionIndex(state.currentQuestionIndex)
                    .totalQuestions(state.questions.size())
                    .timeLimitSeconds(state.timeLimitSeconds)
                    .question(questionPayload(state, q))
                    .player1Score(state.player1Score)
                    .player2Score(state.player2Score)
                    .build();
            messaging.convertAndSend("/topic/game/" + state.roomId, event);

            // Auto-advance timer when nobody answers
            state.questionTimer = scheduler.schedule(
                    () -> advanceQuestion(state), state.timeLimitSeconds, TimeUnit.SECONDS);

            // Schedule bot answer (2–8 seconds, correct with the game's random bot accuracy)
            if (state.isBotGame) {
                int botDelay = 2 + RANDOM.nextInt(Math.max(1, Math.min(7, state.timeLimitSeconds - 2)));
                GameQuestion question = q;
                scheduler.schedule(() -> submitBotAnswer(state, question), botDelay, TimeUnit.SECONDS);
            }
        }
    }

    private void submitBotAnswer(ActiveGameState state, GameQuestion q) {
        boolean answerCorrectly = RANDOM.nextDouble() < state.botAccuracy;
        Long optionId;
        if (answerCorrectly) {
            optionId = q.getOptions().stream()
                    .filter(o -> Boolean.TRUE.equals(o.getCorrect()))
                    .findFirst()
                    .map(GameAnswerOption::getId)
                    .orElse(q.getOptions().get(0).getId());
        } else {
            List<GameAnswerOption> wrong = q.getOptions().stream()
                    .filter(o -> !Boolean.TRUE.equals(o.getCorrect()))
                    .collect(Collectors.toList());
            optionId = wrong.isEmpty()
                    ? q.getOptions().get(0).getId()
                    : wrong.get(RANDOM.nextInt(wrong.size())).getId();
        }
        submitAnswer(state.roomId, BOT_ID, optionId);
    }

    private void advanceQuestion(ActiveGameState state) {
        synchronized (state.lock) {
            state.currentQuestionIndex++;
            if (state.currentQuestionIndex >= state.questions.size()) {
                finishGame(state);
            } else {
                sendNextQuestion(state);
            }
        }
    }

    private void finishGame(ActiveGameState state) {
        boolean p1Won = state.player1Score > state.player2Score;
        boolean p2Won = state.player2Score > state.player1Score;
        Long winnerId = p1Won ? state.player1Id : p2Won ? state.player2Id : null;
        endGame(state, winnerId, null);
    }

    /**
     * Shared game-ending path for both a normal finish (winner derived from score, forfeitedBy=null)
     * and a forfeit (winner forced to the opponent, regardless of score).
     */
    private void endGame(ActiveGameState state, Long winnerId, Long forfeitedBy) {
        activeGames.remove(state.roomId);
        botRoomNames.remove(state.roomId);
        try {
            GameRoom room = gameRoomRepository.findById(state.roomId).orElseThrow();
            room.setStatus(GameRoomStatus.FINISHED);
            room.setFinishedAt(LocalDateTime.now());
            room.setPlayer1Score(state.player1Score);
            room.setPlayer2Score(state.player2Score);
            room.setWinnerId(winnerId);
            room.setForfeitedBy(forfeitedBy);
            gameRoomRepository.save(room);

            int total = state.questions.size();

            // Game rating (Elo): both ratings are read before either is updated.
            double p1Points = winnerId == null ? 0.5 : state.player1Id.equals(winnerId) ? 1.0 : 0.0;
            GameRatingService.Change p1Change = null, p2Change = null;
            try {
                int p1Before = gameRatingService.currentRating(state.player1Id);
                int p2Before = state.isBotGame ? state.botRating : gameRatingService.currentRating(state.player2Id);
                p1Change = gameRatingService.apply(state.player1Id, p2Before, p1Points, state.isBotGame);
                if (!state.isBotGame) {
                    p2Change = gameRatingService.apply(state.player2Id, p1Before, 1.0 - p1Points, false);
                }
            } catch (Exception e) {
                log.error("rating update failed for room {}: {}", state.roomId, e.getMessage(), e);
            }

            // Save result only for real players
            saveResult(state.player1Id, state.gameTestId, state.roomId, state.player1Score, total,
                    state.player1Id.equals(winnerId), p1Change);
            if (!state.isBotGame) {
                saveResult(state.player2Id, state.gameTestId, state.roomId, state.player2Score, total,
                        state.player2Id.equals(winnerId), p2Change);
            }

            GameEvent gameOver = GameEvent.builder()
                    .type("GAME_OVER")
                    .roomId(state.roomId)
                    .player1Score(state.player1Score)
                    .player2Score(state.player2Score)
                    .winnerId(winnerId)
                    .forfeitedBy(forfeitedBy)
                    .player1RatingChange(p1Change != null ? p1Change.delta() : null)
                    .player1Rating(p1Change != null ? p1Change.after() : null)
                    .player2RatingChange(p2Change != null ? p2Change.delta() : null)
                    .player2Rating(state.isBotGame ? Integer.valueOf(state.botRating)
                            : p2Change != null ? p2Change.after() : null)
                    .build();
            messaging.convertAndSend("/topic/game/" + state.roomId, gameOver);
        } catch (Exception e) {
            log.error("endGame error for room {}: {}", state.roomId, e.getMessage(), e);
        }
    }

    /**
     * A participant concedes an in-progress game. The opponent is declared the winner
     * immediately, regardless of the current score.
     */
    public void forfeitGame(Long roomId, Long userId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new AppException("Комната не найдена.", "Бөлмө табылган жок."));

        boolean isParticipant = userId.equals(room.getPlayer1Id()) || userId.equals(room.getPlayer2Id());
        if (!isParticipant) {
            throw new AppException("Нет доступа.", "Мүмкүнчүлүк жок.");
        }

        if (room.getStatus() != GameRoomStatus.IN_PROGRESS) {
            throw new AppException(
                    "Можно сдаться только в игре, которая уже идёт.",
                    "Учурда жүрүп жаткан оюндан гана баш тартууга болот.");
        }

        ActiveGameState state = activeGames.get(roomId);
        if (state == null) {
            // In-memory state already gone (e.g. server restart) — just close out the room
            // without clobbering a status another path may have already set concurrently.
            if (room.getStatus() == GameRoomStatus.IN_PROGRESS) {
                room.setStatus(GameRoomStatus.ABANDONED);
                gameRoomRepository.save(room);
            }
            return;
        }

        Long opponentId = userId.equals(state.player1Id) ? state.player2Id : state.player1Id;

        synchronized (state.lock) {
            // Guard against a race with a normal finish (timer/answers) that already ended the game.
            if (activeGames.get(roomId) != state) return;
            if (state.questionTimer != null) state.questionTimer.cancel(false);
            endGame(state, opponentId, userId);
        }
    }

    private void saveResult(Long userId, Long gameTestId, Long roomId, int score, int total, boolean won,
                            GameRatingService.Change ratingChange) {
        int prevTop = gamePlayerResultRepository.findTopScore(gameTestId);
        Long prevLeader = gamePlayerResultRepository
                .findTopUserIds(gameTestId, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);

        GamePlayerResult result = new GamePlayerResult();
        result.setUserId(userId);
        result.setGameTestId(gameTestId);
        result.setRoomId(roomId);
        result.setScore(score);
        result.setTotalQuestions(total);
        result.setWon(won);
        if (ratingChange != null) {
            result.setRatingBefore(ratingChange.before());
            result.setRatingAfter(ratingChange.after());
        }
        gamePlayerResultRepository.save(result);

        // Notify the dethroned leader (real user, not a bot, not the same person).
        if (!BOT_ID.equals(userId) && prevTop > 0 && score > prevTop
                && prevLeader != null && !prevLeader.equals(userId) && !BOT_ID.equals(prevLeader)) {
            gameTestRepository.findById(gameTestId).ifPresent(gt -> {
                try {
                    pushNotificationService.notifyUser(prevLeader, PushCategory.MARKETING,
                            new PushMessages.Text(
                                    "Твой рекорд побит!",
                                    "В игре «" + gt.getTitle() + "» кто-то набрал больше очков",
                                    "Рекордуң жеңилди!",
                                    "«" + gt.getTitle() + "» оюнунда бирөө көбүрөөк упай алды"),
                            PushDataType.GAME, gameTestId);
                } catch (Exception e) {
                    log.warn("high-score notification failed: {}", e.getMessage());
                }
            });
        }
    }

    private GameEvent.QuestionPayload questionPayload(ActiveGameState state, GameQuestion q) {
        List<GameEvent.OptionPayload> options = state.shuffledOptions.getOrDefault(q.getId(),
                q.getOptions().stream().map(o -> new GameEvent.OptionPayload(o.getId(), o.getText())).toList());
        return GameEvent.QuestionPayload.builder()
                .id(q.getId())
                .text(q.getText())
                .imageUrl(minioService.presign(q.getImageUrl()))
                .figure(figureValidator.fromJson(q.getFigure()))
                .options(options)
                .build();
    }

    private String displayName(User u) {
        if (u.getFirstName() != null) {
            return u.getFirstName() + (u.getLastName() != null ? " " + u.getLastName() : "");
        }
        return u.getPhone();
    }

    // ===== Inner state class =====

    private static class ActiveGameState {
        Long roomId;
        Long gameTestId;
        Long player1Id;
        Long player2Id;
        String player1Name;
        String player2Name;
        String player1Avatar;
        String player2Avatar;
        List<GameQuestion> questions;
        int timeLimitSeconds;
        boolean isBotGame;
        volatile int currentQuestionIndex = 0;
        volatile int player1Score = 0;
        volatile int player2Score = 0;
        volatile java.time.Instant questionStartedAt;
        final Map<Long, Long> currentAnswers = new ConcurrentHashMap<>();
        volatile ScheduledFuture<?> questionTimer;
        final Object lock = new Object();
        /** questionId -> options in this game's random order */
        final Map<Long, List<GameEvent.OptionPayload>> shuffledOptions = new ConcurrentHashMap<>();
        /** Bot: probability of answering correctly (random per game) and virtual rating. */
        double botAccuracy = 0.55;
        int botRating = UserGameRating.INITIAL_RATING;
    }

    public record GameTestSummary(Long id, String title, String description,
                                   Integer timeLimitSeconds, Integer questionsPerGame,
                                   long questionCount, boolean played,
                                   Long myBestScore, java.time.LocalDateTime lastPlayedAt,
                                   Integer playCount) {}
}
