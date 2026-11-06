-- =====================================================================
-- SmartStudy - V1 core schema (MySQL 8.4)
--
-- Conventions
--   * BIGINT AUTO_INCREMENT primary keys.
--   * Every student-owned table carries user_id so that every query can be
--     scoped to the signed-in student (data isolation).
--   * All DATETIME(6) columns hold UTC instants. The application connects
--     with connectionTimeZone=UTC and Hibernate uses hibernate.jdbc.time_zone=UTC.
--   * DATE / TIME columns hold calendar values in the student's own time zone
--     (users.time_zone), e.g. an exam date or an availability window.
--   * Scores are DECIMAL(5,2) on a 0-100 scale.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    full_name                 VARCHAR(100) NOT NULL,
    email                     VARCHAR(255) NOT NULL,
    password_hash             VARCHAR(100) NOT NULL,
    role                      VARCHAR(20)  NOT NULL DEFAULT 'STUDENT',
    time_zone                 VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    daily_study_minutes       INT          NOT NULL DEFAULT 180,
    weekly_study_minutes      INT          NOT NULL DEFAULT 900,
    preferred_session_minutes INT          NOT NULL DEFAULT 50,
    break_minutes             INT          NOT NULL DEFAULT 10,
    focus_minutes             INT          NOT NULL DEFAULT 25,
    short_break_minutes       INT          NOT NULL DEFAULT 5,
    long_break_minutes        INT          NOT NULL DEFAULT 15,
    long_break_interval       INT          NOT NULL DEFAULT 4,
    enabled                   BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at             DATETIME(6)  NULL,
    created_at                DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('STUDENT', 'ADMIN')),
    CONSTRAINT chk_users_daily_minutes CHECK (daily_study_minutes BETWEEN 0 AND 1440),
    CONSTRAINT chk_users_weekly_minutes CHECK (weekly_study_minutes BETWEEN 0 AND 10080),
    CONSTRAINT chk_users_session_minutes CHECK (preferred_session_minutes BETWEEN 15 AND 240),
    CONSTRAINT chk_users_break_minutes CHECK (break_minutes BETWEEN 0 AND 60),
    CONSTRAINT chk_users_focus_minutes CHECK (focus_minutes BETWEEN 5 AND 120),
    CONSTRAINT chk_users_short_break CHECK (short_break_minutes BETWEEN 1 AND 60),
    CONSTRAINT chk_users_long_break CHECK (long_break_minutes BETWEEN 1 AND 120),
    CONSTRAINT chk_users_long_break_interval CHECK (long_break_interval BETWEEN 2 AND 12)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 2. subjects
-- ---------------------------------------------------------------------
CREATE TABLE subjects (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    user_id      BIGINT        NOT NULL,
    name         VARCHAR(100)  NOT NULL,
    code         VARCHAR(20)   NULL,
    color        VARCHAR(7)    NOT NULL DEFAULT '#10B981',
    importance   INT           NOT NULL DEFAULT 3,
    target_score DECIMAL(5, 2) NULL,
    description  VARCHAR(500)  NULL,
    archived     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_subjects PRIMARY KEY (id),
    CONSTRAINT fk_subjects_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_subjects_user_name UNIQUE (user_id, name),
    CONSTRAINT chk_subjects_importance CHECK (importance BETWEEN 1 AND 5),
    CONSTRAINT chk_subjects_target CHECK (target_score IS NULL OR target_score BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 3. topics (also holds the SM-2 spaced-repetition state of the topic)
-- ---------------------------------------------------------------------
CREATE TABLE topics (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                BIGINT        NOT NULL,
    subject_id             BIGINT        NOT NULL,
    name                   VARCHAR(150)  NOT NULL,
    description            VARCHAR(1000) NULL,
    difficulty             INT           NOT NULL DEFAULT 3,
    estimated_minutes      INT           NOT NULL DEFAULT 120,
    status                 VARCHAR(20)   NOT NULL DEFAULT 'NOT_STARTED',
    completed_at           DATETIME(6)   NULL,
    sort_order             INT           NOT NULL DEFAULT 0,
    revision_ease_factor   DECIMAL(4, 2) NOT NULL DEFAULT 2.50,
    revision_interval_days INT           NOT NULL DEFAULT 0,
    revision_repetitions   INT           NOT NULL DEFAULT 0,
    next_revision_date     DATE          NULL,
    last_revised_on        DATE          NULL,
    created_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_topics PRIMARY KEY (id),
    CONSTRAINT fk_topics_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_topics_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE CASCADE,
    CONSTRAINT uk_topics_subject_name UNIQUE (subject_id, name),
    CONSTRAINT chk_topics_difficulty CHECK (difficulty BETWEEN 1 AND 5),
    CONSTRAINT chk_topics_estimated CHECK (estimated_minutes BETWEEN 15 AND 6000),
    CONSTRAINT chk_topics_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_topics_ease CHECK (revision_ease_factor BETWEEN 1.30 AND 5.00),
    CONSTRAINT chk_topics_interval CHECK (revision_interval_days >= 0),
    CONSTRAINT chk_topics_repetitions CHECK (revision_repetitions >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_topics_user_subject ON topics (user_id, subject_id);
CREATE INDEX idx_topics_user_next_revision ON topics (user_id, next_revision_date);

-- ---------------------------------------------------------------------
-- 4. weekly_availability (day_of_week: ISO-8601, 1 = Monday ... 7 = Sunday)
-- ---------------------------------------------------------------------
CREATE TABLE weekly_availability (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    day_of_week INT         NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_weekly_availability PRIMARY KEY (id),
    CONSTRAINT fk_availability_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_availability_slot UNIQUE (user_id, day_of_week, start_time),
    CONSTRAINT chk_availability_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_availability_range CHECK (end_time > start_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 5. exams
-- ---------------------------------------------------------------------
CREATE TABLE exams (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    user_id          BIGINT        NOT NULL,
    subject_id       BIGINT        NOT NULL,
    title            VARCHAR(150)  NOT NULL,
    exam_type        VARCHAR(20)   NOT NULL DEFAULT 'FINAL',
    exam_date        DATE          NOT NULL,
    start_time       TIME          NULL,
    duration_minutes INT           NULL,
    location         VARCHAR(150)  NULL,
    target_score     DECIMAL(5, 2) NULL,
    notes            VARCHAR(1000) NULL,
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_exams PRIMARY KEY (id),
    CONSTRAINT fk_exams_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_exams_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE CASCADE,
    CONSTRAINT chk_exams_type CHECK (exam_type IN ('MIDTERM', 'FINAL', 'QUIZ', 'PRACTICAL', 'ENTRANCE', 'OTHER')),
    CONSTRAINT chk_exams_duration CHECK (duration_minutes IS NULL OR duration_minutes BETWEEN 1 AND 1440),
    CONSTRAINT chk_exams_target CHECK (target_score IS NULL OR target_score BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_exams_user_date ON exams (user_id, exam_date);

-- ---------------------------------------------------------------------
-- 6. study_plans (one row per generated timetable)
-- ---------------------------------------------------------------------
CREATE TABLE study_plans (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,
    start_date            DATE         NOT NULL,
    end_date              DATE         NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    generated_at          DATETIME(6)  NOT NULL,
    total_planned_minutes INT          NOT NULL DEFAULT 0,
    session_count         INT          NOT NULL DEFAULT 0,
    strategy_summary      VARCHAR(500) NULL,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_study_plans PRIMARY KEY (id),
    CONSTRAINT fk_study_plans_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_study_plans_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_study_plans_range CHECK (end_date >= start_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_study_plans_user_status ON study_plans (user_id, status);

-- ---------------------------------------------------------------------
-- 7. study_sessions
-- ---------------------------------------------------------------------
CREATE TABLE study_sessions (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    user_id             BIGINT        NOT NULL,
    plan_id             BIGINT        NULL,
    subject_id          BIGINT        NOT NULL,
    topic_id            BIGINT        NULL,
    session_type        VARCHAR(20)   NOT NULL DEFAULT 'STUDY',
    source              VARCHAR(20)   NOT NULL DEFAULT 'GENERATED',
    status              VARCHAR(20)   NOT NULL DEFAULT 'PLANNED',
    start_at            DATETIME(6)   NOT NULL,
    end_at              DATETIME(6)   NOT NULL,
    planned_minutes     INT           NOT NULL,
    actual_minutes      INT           NULL,
    started_at          DATETIME(6)   NULL,
    completed_at        DATETIME(6)   NULL,
    locked              BOOLEAN       NOT NULL DEFAULT FALSE,
    priority_score      DECIMAL(5, 2) NULL,
    reason              VARCHAR(500)  NULL,
    notes               VARCHAR(1000) NULL,
    rescheduled_from_id BIGINT        NULL,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_study_sessions PRIMARY KEY (id),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_sessions_plan FOREIGN KEY (plan_id) REFERENCES study_plans (id) ON DELETE SET NULL,
    CONSTRAINT fk_sessions_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE CASCADE,
    CONSTRAINT fk_sessions_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    CONSTRAINT fk_sessions_rescheduled_from FOREIGN KEY (rescheduled_from_id) REFERENCES study_sessions (id) ON DELETE SET NULL,
    CONSTRAINT chk_sessions_type CHECK (session_type IN ('STUDY', 'REVISION', 'PRACTICE')),
    CONSTRAINT chk_sessions_source CHECK (source IN ('GENERATED', 'MANUAL')),
    CONSTRAINT chk_sessions_status CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'INCOMPLETE', 'MISSED', 'CANCELLED')),
    CONSTRAINT chk_sessions_range CHECK (end_at > start_at),
    CONSTRAINT chk_sessions_planned CHECK (planned_minutes > 0),
    CONSTRAINT chk_sessions_actual CHECK (actual_minutes IS NULL OR actual_minutes >= 0),
    CONSTRAINT chk_sessions_priority CHECK (priority_score IS NULL OR priority_score BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_sessions_user_start ON study_sessions (user_id, start_at);
CREATE INDEX idx_sessions_user_status ON study_sessions (user_id, status);

-- ---------------------------------------------------------------------
-- 8. assessments (subject-level marks: exams, quizzes, assignments, mock tests)
-- ---------------------------------------------------------------------
CREATE TABLE assessments (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    subject_id      BIGINT        NOT NULL,
    exam_id         BIGINT        NULL,
    title           VARCHAR(150)  NOT NULL,
    assessment_type VARCHAR(20)   NOT NULL,
    assessed_on     DATE          NOT NULL,
    max_marks       DECIMAL(7, 2) NOT NULL,
    obtained_marks  DECIMAL(7, 2) NOT NULL,
    score_percent   DECIMAL(5, 2) NOT NULL,
    notes           VARCHAR(1000) NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_assessments PRIMARY KEY (id),
    CONSTRAINT fk_assessments_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_assessments_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE CASCADE,
    CONSTRAINT fk_assessments_exam FOREIGN KEY (exam_id) REFERENCES exams (id) ON DELETE SET NULL,
    CONSTRAINT chk_assessments_type CHECK (assessment_type IN ('EXAM', 'QUIZ', 'ASSIGNMENT', 'MOCK_TEST')),
    CONSTRAINT chk_assessments_max CHECK (max_marks > 0),
    CONSTRAINT chk_assessments_obtained CHECK (obtained_marks >= 0 AND obtained_marks <= max_marks),
    CONSTRAINT chk_assessments_percent CHECK (score_percent BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_assessments_user_date ON assessments (user_id, assessed_on);

-- ---------------------------------------------------------------------
-- 9. assessment_topic_scores (topic-wise marks inside one assessment)
-- ---------------------------------------------------------------------
CREATE TABLE assessment_topic_scores (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    user_id        BIGINT        NOT NULL,
    assessment_id  BIGINT        NOT NULL,
    topic_id       BIGINT        NOT NULL,
    max_marks      DECIMAL(7, 2) NOT NULL,
    obtained_marks DECIMAL(7, 2) NOT NULL,
    score_percent  DECIMAL(5, 2) NOT NULL,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_assessment_topic_scores PRIMARY KEY (id),
    CONSTRAINT fk_topic_scores_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_topic_scores_assessment FOREIGN KEY (assessment_id) REFERENCES assessments (id) ON DELETE CASCADE,
    CONSTRAINT fk_topic_scores_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    CONSTRAINT uk_topic_scores_assessment_topic UNIQUE (assessment_id, topic_id),
    CONSTRAINT chk_topic_scores_max CHECK (max_marks > 0),
    CONSTRAINT chk_topic_scores_obtained CHECK (obtained_marks >= 0 AND obtained_marks <= max_marks),
    CONSTRAINT chk_topic_scores_percent CHECK (score_percent BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_topic_scores_user_topic ON assessment_topic_scores (user_id, topic_id);

-- ---------------------------------------------------------------------
-- 10. topic_mastery (one row per assessed topic; unassessed topics have no row)
-- ---------------------------------------------------------------------
CREATE TABLE topic_mastery (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                BIGINT        NOT NULL,
    topic_id               BIGINT        NOT NULL,
    mastery_score          DECIMAL(5, 2) NOT NULL,
    latest_score           DECIMAL(5, 2) NOT NULL,
    historical_average     DECIMAL(5, 2) NOT NULL,
    previous_mastery_score DECIMAL(5, 2) NULL,
    assessment_count       INT           NOT NULL,
    mastery_level          VARCHAR(20)   NOT NULL,
    last_assessed_on       DATE          NOT NULL,
    created_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_topic_mastery PRIMARY KEY (id),
    CONSTRAINT fk_mastery_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_mastery_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    CONSTRAINT uk_mastery_topic UNIQUE (topic_id),
    CONSTRAINT chk_mastery_level CHECK (mastery_level IN ('WEAK', 'NEEDS_IMPROVEMENT', 'GOOD', 'STRONG')),
    CONSTRAINT chk_mastery_score CHECK (mastery_score BETWEEN 0 AND 100),
    CONSTRAINT chk_mastery_count CHECK (assessment_count > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_mastery_user_score ON topic_mastery (user_id, mastery_score);

-- ---------------------------------------------------------------------
-- 11. revision_logs (complete history of every spaced-repetition review)
-- ---------------------------------------------------------------------
CREATE TABLE revision_logs (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                BIGINT        NOT NULL,
    topic_id               BIGINT        NOT NULL,
    study_session_id       BIGINT        NULL,
    rating                 VARCHAR(10)   NOT NULL,
    revised_on             DATE          NOT NULL,
    due_date               DATE          NULL,
    revised_at             DATETIME(6)   NOT NULL,
    duration_minutes       INT           NULL,
    previous_ease_factor   DECIMAL(4, 2) NOT NULL,
    new_ease_factor        DECIMAL(4, 2) NOT NULL,
    previous_interval_days INT           NOT NULL,
    new_interval_days      INT           NOT NULL,
    repetitions            INT           NOT NULL,
    next_revision_date     DATE          NOT NULL,
    notes                  VARCHAR(500)  NULL,
    created_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_revision_logs PRIMARY KEY (id),
    CONSTRAINT fk_revision_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_revision_logs_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    CONSTRAINT fk_revision_logs_session FOREIGN KEY (study_session_id) REFERENCES study_sessions (id) ON DELETE SET NULL,
    CONSTRAINT chk_revision_logs_rating CHECK (rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY')),
    CONSTRAINT chk_revision_logs_duration CHECK (duration_minutes IS NULL OR duration_minutes BETWEEN 1 AND 600)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_revision_logs_user_date ON revision_logs (user_id, revised_on);
CREATE INDEX idx_revision_logs_topic ON revision_logs (topic_id, revised_on);

-- ---------------------------------------------------------------------
-- 12. focus_sessions (Pomodoro focus blocks, timed by the server)
-- ---------------------------------------------------------------------
CREATE TABLE focus_sessions (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT       NOT NULL,
    subject_id              BIGINT       NULL,
    topic_id                BIGINT       NULL,
    study_session_id        BIGINT       NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',
    planned_minutes         INT          NOT NULL,
    started_at              DATETIME(6)  NOT NULL,
    paused_at               DATETIME(6)  NULL,
    total_paused_seconds    INT          NOT NULL DEFAULT 0,
    pause_count             INT          NOT NULL DEFAULT 0,
    ended_at                DATETIME(6)  NULL,
    actual_focus_seconds    INT          NULL,
    client_reported_seconds INT          NULL,
    notes                   VARCHAR(500) NULL,
    created_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_focus_sessions PRIMARY KEY (id),
    CONSTRAINT fk_focus_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_focus_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE SET NULL,
    CONSTRAINT fk_focus_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE SET NULL,
    CONSTRAINT fk_focus_study_session FOREIGN KEY (study_session_id) REFERENCES study_sessions (id) ON DELETE SET NULL,
    CONSTRAINT chk_focus_status CHECK (status IN ('RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_focus_planned CHECK (planned_minutes BETWEEN 1 AND 180),
    CONSTRAINT chk_focus_paused CHECK (total_paused_seconds >= 0),
    CONSTRAINT chk_focus_actual CHECK (actual_focus_seconds IS NULL OR actual_focus_seconds >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_focus_user_started ON focus_sessions (user_id, started_at);
CREATE INDEX idx_focus_user_status ON focus_sessions (user_id, status);

-- ---------------------------------------------------------------------
-- 13. notifications
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    notification_type VARCHAR(30)  NOT NULL,
    title             VARCHAR(150) NOT NULL,
    message           VARCHAR(500) NOT NULL,
    link              VARCHAR(255) NULL,
    is_read           BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at           DATETIME(6)  NULL,
    dedupe_key        VARCHAR(150) NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_notifications_dedupe UNIQUE (user_id, dedupe_key),
    CONSTRAINT chk_notifications_type CHECK (notification_type IN
        ('REVISION_DUE', 'SESSION_MISSED', 'SESSION_RESCHEDULED', 'EXAM_UPCOMING', 'PLAN_GENERATED', 'SYSTEM'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_notifications_user_read ON notifications (user_id, is_read, created_at);

-- ---------------------------------------------------------------------
-- 14. readiness_snapshots (one snapshot per exam per day)
-- ---------------------------------------------------------------------
CREATE TABLE readiness_snapshots (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    user_id               BIGINT        NOT NULL,
    exam_id               BIGINT        NOT NULL,
    snapshot_date         DATE          NOT NULL,
    readiness_score       DECIMAL(5, 2) NULL,
    syllabus_completion   DECIMAL(5, 2) NULL,
    topic_mastery         DECIMAL(5, 2) NULL,
    mock_test_performance DECIMAL(5, 2) NULL,
    revision_completion   DECIMAL(5, 2) NULL,
    data_confidence       DECIMAL(5, 2) NOT NULL,
    data_sufficiency      VARCHAR(20)   NOT NULL,
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_readiness_snapshots PRIMARY KEY (id),
    CONSTRAINT fk_readiness_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_readiness_exam FOREIGN KEY (exam_id) REFERENCES exams (id) ON DELETE CASCADE,
    CONSTRAINT uk_readiness_exam_date UNIQUE (exam_id, snapshot_date),
    CONSTRAINT chk_readiness_sufficiency CHECK (data_sufficiency IN ('SUFFICIENT', 'PARTIAL', 'INSUFFICIENT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_readiness_user_date ON readiness_snapshots (user_id, snapshot_date);
