-- ============================================================================
-- ОРТ: test taken as a whole, ОРТ score, reading passages, figures,
-- «все тесты / все тексты», reading texts library, schools rating,
-- «пригласи друга», game Elo rating.
-- NOTE: runtime schema is managed by ddl-auto=update (flyway disabled); the only
-- statement Hibernate cannot do (DROP NOT NULL) is also applied by SchemaPatcher.
-- ============================================================================

-- Tests / sections -------------------------------------------------------------
ALTER TABLE tests      ADD COLUMN IF NOT EXISTS max_score INTEGER NOT NULL DEFAULT 245;
ALTER TABLE sub_tests  ADD COLUMN IF NOT EXISTS max_score INTEGER;
ALTER TABLE sub_tests  ADD COLUMN IF NOT EXISTS icon_url  VARCHAR(1000);

CREATE TABLE IF NOT EXISTS reading_passages
(
    id          BIGSERIAL PRIMARY KEY,
    sub_test_id BIGINT       NOT NULL REFERENCES sub_tests (id) ON DELETE CASCADE,
    title       VARCHAR(255),
    title_ky    VARCHAR(255),
    text        TEXT         NOT NULL,
    text_ky     TEXT,
    image_url   VARCHAR(1000),
    order_index INT          NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

ALTER TABLE questions ADD COLUMN IF NOT EXISTS question_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE questions ADD COLUMN IF NOT EXISTS column_a      TEXT;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS column_a_ky   TEXT;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS column_b      TEXT;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS column_b_ky   TEXT;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS figure        TEXT;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS passage_id    BIGINT REFERENCES reading_passages (id);

ALTER TABLE game_questions ADD COLUMN IF NOT EXISTS figure TEXT;

-- Whole-test attempts ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS test_attempts
(
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT      NOT NULL REFERENCES users (id),
    test_id               BIGINT      NOT NULL REFERENCES tests (id),
    status                VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    current_section_index INT         NOT NULL DEFAULT 0,
    started_at            TIMESTAMP   NOT NULL,
    paused_at             TIMESTAMP,
    completed_at          TIMESTAMP,
    correct_answers       INT,
    total_questions       INT,
    earned_points         INT,
    total_points          INT,
    ort_score             INT,
    max_score             INT,
    created_at            TIMESTAMP   NOT NULL,
    updated_at            TIMESTAMP   NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_test_attempts_user_test ON test_attempts (user_id, test_id);
CREATE INDEX IF NOT EXISTS idx_test_attempts_status    ON test_attempts (status);

ALTER TABLE test_sessions ADD COLUMN IF NOT EXISTS attempt_id BIGINT REFERENCES test_attempts (id);

-- Catalogue products -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS product_prices
(
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(20)    NOT NULL UNIQUE,
    price      NUMERIC(10, 2) NOT NULL DEFAULT 0,
    old_price  NUMERIC(10, 2),
    active     BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP      NOT NULL,
    updated_at TIMESTAMP      NOT NULL
);

CREATE TABLE IF NOT EXISTS user_all_access
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product    VARCHAR(20) NOT NULL,
    granted_at TIMESTAMP   NOT NULL,
    expires_at TIMESTAMP,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL,
    UNIQUE (user_id, product)
);

ALTER TABLE payments ALTER COLUMN test_id DROP NOT NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS product VARCHAR(20);

CREATE TABLE IF NOT EXISTS app_settings
(
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value TEXT,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS reading_texts
(
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255)  NOT NULL,
    title_ky    VARCHAR(255),
    content     TEXT,
    content_ky  TEXT,
    pdf_url     VARCHAR(1000),
    free        BOOLEAN       NOT NULL DEFAULT FALSE,
    order_index INT           NOT NULL DEFAULT 0,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP     NOT NULL,
    updated_at  TIMESTAMP     NOT NULL
);

-- Schools ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS districts
(
    id         BIGSERIAL PRIMARY KEY,
    region_id  BIGINT       NOT NULL REFERENCES regions (id),
    name       VARCHAR(255) NOT NULL,
    name_ky    VARCHAR(255),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS schools
(
    id          BIGSERIAL PRIMARY KEY,
    district_id BIGINT       NOT NULL REFERENCES districts (id),
    name        VARCHAR(500) NOT NULL,
    name_ky     VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS school_id BIGINT REFERENCES schools (id);
-- referral_code / referred_by_id already exist since V5

-- Referrals ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS referral_rewards
(
    id               BIGSERIAL PRIMARY KEY,
    inviter_id       BIGINT    NOT NULL REFERENCES users (id),
    invitee_id       BIGINT    NOT NULL UNIQUE REFERENCES users (id),
    payment_id       BIGINT REFERENCES payments (id),
    redeemed_test_id BIGINT REFERENCES tests (id),
    redeemed_at      TIMESTAMP,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL
);

-- Game rating --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_game_ratings
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT    NOT NULL UNIQUE,
    rating       INT       NOT NULL DEFAULT 1000,
    peak_rating  INT       NOT NULL DEFAULT 1000,
    games_played INT       NOT NULL DEFAULT 0,
    wins         INT       NOT NULL DEFAULT 0,
    losses       INT       NOT NULL DEFAULT 0,
    draws        INT       NOT NULL DEFAULT 0,
    win_streak   INT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_user_game_ratings_rating ON user_game_ratings (rating);

ALTER TABLE game_player_results ADD COLUMN IF NOT EXISTS rating_before INT;
ALTER TABLE game_player_results ADD COLUMN IF NOT EXISTS rating_after  INT;
