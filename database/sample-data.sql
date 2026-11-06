-- =====================================================================
-- SmartStudy - sample development data
--
-- WHEN:  run AFTER the backend has started once (Flyway must have created
--        the tables first).
-- WHERE: MySQL Workbench (File > Open SQL Script > Execute all) or
--        mysql -u smartstudy -p smartstudy < database/sample-data.sql
--
-- The script is re-runnable: it deletes the demo account (ON DELETE CASCADE
-- removes all of its data) and creates it again. All dates are relative to
-- "today", so the data always looks current.
--
-- Demo login:  demo@smartstudy.dev  /  Demo@12345
-- Time zone :  Asia/Kolkata (UTC+05:30)
-- =====================================================================

USE smartstudy;

-- Work in UTC so NOW() / CURRENT_TIMESTAMP match what the application stores.
SET time_zone = '+00:00';
-- "Today" in the demo student's time zone (India has no DST, a fixed offset is exact).
SET @today = DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+05:30'));

DELETE FROM users WHERE email = 'demo@smartstudy.dev';

-- ---------------------------------------------------------------------
-- Student (password: Demo@12345, BCrypt cost 12)
-- ---------------------------------------------------------------------
INSERT INTO users (full_name, email, password_hash, role, time_zone, daily_study_minutes, weekly_study_minutes,
                   preferred_session_minutes, break_minutes, focus_minutes, short_break_minutes, long_break_minutes,
                   long_break_interval, enabled, last_login_at, created_at, updated_at)
VALUES ('Demo Student', 'demo@smartstudy.dev', '$2a$12$oHLLZ4r83qy7SgeqIGTZuegw75NfJGnxnxK0mvf4chwv/h/XzwPpa',
        'STUDENT', 'Asia/Kolkata', 180, 900, 50, 10, 25, 5, 15, 4, TRUE, NULL,
        NOW(6) - INTERVAL 30 DAY, NOW(6));
SET @uid = LAST_INSERT_ID();

-- ---------------------------------------------------------------------
-- Weekly availability (local time): weekday evenings, weekend morning + afternoon
-- ---------------------------------------------------------------------
INSERT INTO weekly_availability (user_id, day_of_week, start_time, end_time) VALUES
    (@uid, 1, '18:00', '21:00'), (@uid, 2, '18:00', '21:00'), (@uid, 3, '18:00', '21:00'),
    (@uid, 4, '18:00', '21:00'), (@uid, 5, '18:00', '21:00'),
    (@uid, 6, '09:00', '12:00'), (@uid, 6, '16:00', '19:00'),
    (@uid, 7, '09:00', '12:00'), (@uid, 7, '16:00', '19:00');

-- ---------------------------------------------------------------------
-- Subjects
-- ---------------------------------------------------------------------
INSERT INTO subjects (user_id, name, code, color, importance, target_score, description)
VALUES (@uid, 'Data Structures & Algorithms', 'CS301', '#3B82F6', 5, 85.00, 'Core programming course: lists, trees, graphs.');
SET @dsa = LAST_INSERT_ID();
INSERT INTO subjects (user_id, name, code, color, importance, target_score, description)
VALUES (@uid, 'Database Management Systems', 'CS302', '#10B981', 4, 80.00, 'Relational model, SQL and transactions.');
SET @dbms = LAST_INSERT_ID();
INSERT INTO subjects (user_id, name, code, color, importance, target_score, description)
VALUES (@uid, 'Operating Systems', 'CS303', '#F59E0B', 4, 75.00, 'Processes, memory and file systems.');
SET @os = LAST_INSERT_ID();
INSERT INTO subjects (user_id, name, code, color, importance, target_score, description)
VALUES (@uid, 'Engineering Mathematics III', 'MA301', '#8B5CF6', 3, 70.00, 'Transforms and complex variables.');
SET @math = LAST_INSERT_ID();

-- ---------------------------------------------------------------------
-- Topics (completed topics already carry their spaced-repetition state)
-- ---------------------------------------------------------------------
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, completed_at, sort_order,
                    revision_ease_factor, revision_interval_days, revision_repetitions, next_revision_date, last_revised_on)
VALUES (@uid, @dsa, 'Arrays & Strings', 2, 120, 'COMPLETED', NOW(6) - INTERVAL 25 DAY, 1, 2.50, 15, 3, @today + INTERVAL 5 DAY, @today - INTERVAL 10 DAY);
SET @t_arrays = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, completed_at, sort_order,
                    revision_ease_factor, revision_interval_days, revision_repetitions, next_revision_date, last_revised_on)
VALUES (@uid, @dsa, 'Linked Lists', 2, 120, 'COMPLETED', NOW(6) - INTERVAL 18 DAY, 2, 2.50, 6, 2, @today - INTERVAL 2 DAY, @today - INTERVAL 8 DAY);
SET @t_lists = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, completed_at, sort_order,
                    revision_ease_factor, revision_interval_days, revision_repetitions, next_revision_date, last_revised_on)
VALUES (@uid, @dsa, 'Stacks & Queues', 2, 90, 'COMPLETED', NOW(6) - INTERVAL 9 DAY, 3, 2.35, 1, 1, @today, @today - INTERVAL 1 DAY);
SET @t_stacks = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, sort_order)
VALUES (@uid, @dsa, 'Trees & Binary Search Trees', 4, 180, 'IN_PROGRESS', 4);
SET @t_trees = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, sort_order)
VALUES (@uid, @dsa, 'AVL Trees', 5, 150, 'NOT_STARTED', 5);
SET @t_avl = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, sort_order)
VALUES (@uid, @dsa, 'Graph Algorithms', 5, 240, 'NOT_STARTED', 6);
SET @t_graphs = LAST_INSERT_ID();

INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, completed_at, sort_order,
                    revision_ease_factor, revision_interval_days, revision_repetitions, next_revision_date, last_revised_on)
VALUES (@uid, @dbms, 'ER Model', 2, 90, 'COMPLETED', NOW(6) - INTERVAL 15 DAY, 1, 2.50, 6, 2, @today + INTERVAL 3 DAY, @today - INTERVAL 3 DAY);
SET @t_er = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, completed_at, sort_order,
                    revision_ease_factor, revision_interval_days, revision_repetitions, next_revision_date, last_revised_on)
VALUES (@uid, @dbms, 'Relational Algebra', 3, 120, 'COMPLETED', NOW(6) - INTERVAL 7 DAY, 2, 2.50, 1, 1, @today - INTERVAL 1 DAY, @today - INTERVAL 2 DAY);
SET @t_ra = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, sort_order)
VALUES (@uid, @dbms, 'SQL Queries & Joins', 3, 150, 'IN_PROGRESS', 3);
SET @t_sql = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, sort_order)
VALUES (@uid, @dbms, 'Normalization', 4, 120, 'NOT_STARTED', 4);
SET @t_norm = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, sort_order)
VALUES (@uid, @dbms, 'Transactions & Concurrency', 4, 150, 'NOT_STARTED', 5);
SET @t_tx = LAST_INSERT_ID();

INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, completed_at, sort_order,
                    revision_ease_factor, revision_interval_days, revision_repetitions, next_revision_date, last_revised_on)
VALUES (@uid, @os, 'Process Scheduling', 3, 120, 'COMPLETED', NOW(6) - INTERVAL 6 DAY, 1, 2.65, 4, 1, @today, @today - INTERVAL 4 DAY);
SET @t_sched = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, sort_order)
VALUES (@uid, @os, 'Process Synchronization', 4, 150, 'IN_PROGRESS', 2);
SET @t_sync = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, sort_order)
VALUES (@uid, @os, 'Deadlocks', 4, 120, 'NOT_STARTED', 3);
SET @t_dead = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, sort_order)
VALUES (@uid, @os, 'Memory Management', 3, 150, 'NOT_STARTED', 4);
SET @t_mem = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, sort_order)
VALUES (@uid, @os, 'File Systems', 2, 90, 'NOT_STARTED', 5);
SET @t_fs = LAST_INSERT_ID();

INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, completed_at, sort_order,
                    revision_ease_factor, revision_interval_days, revision_repetitions, next_revision_date, last_revised_on)
VALUES (@uid, @math, 'Laplace Transforms', 3, 150, 'COMPLETED', NOW(6) - INTERVAL 20 DAY, 1, 2.50, 15, 3, @today + INTERVAL 9 DAY, @today - INTERVAL 6 DAY);
SET @t_laplace = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, sort_order)
VALUES (@uid, @math, 'Fourier Series', 4, 150, 'IN_PROGRESS', 2);
SET @t_fourier = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, sort_order)
VALUES (@uid, @math, 'Complex Variables', 4, 180, 'NOT_STARTED', 3);
SET @t_complex = LAST_INSERT_ID();
INSERT INTO topics (user_id, subject_id, name, difficulty, estimated_minutes, status, sort_order)
VALUES (@uid, @math, 'Z-Transforms', 3, 120, 'NOT_STARTED', 4);
SET @t_z = LAST_INSERT_ID();

-- ---------------------------------------------------------------------
-- Examinations (one past quiz for history, four upcoming exams)
-- ---------------------------------------------------------------------
INSERT INTO exams (user_id, subject_id, title, exam_type, exam_date, start_time, duration_minutes, location, target_score)
VALUES (@uid, @dsa, 'DSA Class Quiz 1', 'QUIZ', @today - INTERVAL 20 DAY, '10:00', 30, 'Room 204', NULL);
INSERT INTO exams (user_id, subject_id, title, exam_type, exam_date, start_time, duration_minutes, location, target_score)
VALUES (@uid, @dbms, 'DBMS Mid-Semester Exam', 'MIDTERM', @today + INTERVAL 6 DAY, '14:00', 90, 'Hall A', 75.00);
SET @e_dbms = LAST_INSERT_ID();
INSERT INTO exams (user_id, subject_id, title, exam_type, exam_date, start_time, duration_minutes, location, target_score)
VALUES (@uid, @dsa, 'DSA End-Semester Exam', 'FINAL', @today + INTERVAL 12 DAY, '10:00', 180, 'Main Block', 85.00);
SET @e_dsa = LAST_INSERT_ID();
INSERT INTO exams (user_id, subject_id, title, exam_type, exam_date, start_time, duration_minutes, location, target_score)
VALUES (@uid, @os, 'Operating Systems End-Semester Exam', 'FINAL', @today + INTERVAL 19 DAY, '10:00', 180, 'Main Block', 75.00);
SET @e_os = LAST_INSERT_ID();
INSERT INTO exams (user_id, subject_id, title, exam_type, exam_date, start_time, duration_minutes, location, target_score)
VALUES (@uid, @math, 'Maths Unit Test 3', 'QUIZ', @today + INTERVAL 25 DAY, '09:30', 60, 'Room 110', 70.00);
SET @e_math = LAST_INSERT_ID();

-- ---------------------------------------------------------------------
-- Assessments + topic-wise scores
-- ---------------------------------------------------------------------
INSERT INTO assessments (user_id, subject_id, exam_id, title, assessment_type, assessed_on, max_marks, obtained_marks, score_percent)
VALUES (@uid, @dsa, NULL, 'DSA Class Quiz 1', 'QUIZ', @today - INTERVAL 20 DAY, 30, 21, ROUND(21 * 100 / 30, 2));
SET @a1 = LAST_INSERT_ID();
INSERT INTO assessment_topic_scores (user_id, assessment_id, topic_id, max_marks, obtained_marks, score_percent) VALUES
    (@uid, @a1, @t_arrays, 10, 9, 90.00),
    (@uid, @a1, @t_lists, 10, 7, 70.00),
    (@uid, @a1, @t_stacks, 10, 5, 50.00);

INSERT INTO assessments (user_id, subject_id, exam_id, title, assessment_type, assessed_on, max_marks, obtained_marks, score_percent)
VALUES (@uid, @dsa, NULL, 'DSA Unit Test 2', 'QUIZ', @today - INTERVAL 6 DAY, 40, 22, ROUND(22 * 100 / 40, 2));
SET @a2 = LAST_INSERT_ID();
INSERT INTO assessment_topic_scores (user_id, assessment_id, topic_id, max_marks, obtained_marks, score_percent) VALUES
    (@uid, @a2, @t_lists, 10, 8, 80.00),
    (@uid, @a2, @t_stacks, 10, 7, 70.00),
    (@uid, @a2, @t_trees, 20, 7, 35.00);

INSERT INTO assessments (user_id, subject_id, exam_id, title, assessment_type, assessed_on, max_marks, obtained_marks, score_percent)
VALUES (@uid, @dsa, @e_dsa, 'DSA Mock Test 1', 'MOCK_TEST', @today - INTERVAL 3 DAY, 100, 58, 58.00);
SET @a3 = LAST_INSERT_ID();
INSERT INTO assessment_topic_scores (user_id, assessment_id, topic_id, max_marks, obtained_marks, score_percent) VALUES
    (@uid, @a3, @t_arrays, 25, 22, 88.00),
    (@uid, @a3, @t_trees, 25, 8, 32.00);

INSERT INTO assessments (user_id, subject_id, exam_id, title, assessment_type, assessed_on, max_marks, obtained_marks, score_percent)
VALUES (@uid, @dbms, NULL, 'DBMS Quiz 1', 'QUIZ', @today - INTERVAL 10 DAY, 25, 17, 68.00);
SET @a4 = LAST_INSERT_ID();
INSERT INTO assessment_topic_scores (user_id, assessment_id, topic_id, max_marks, obtained_marks, score_percent) VALUES
    (@uid, @a4, @t_er, 10, 9, 90.00),
    (@uid, @a4, @t_ra, 15, 8, 53.33);

INSERT INTO assessments (user_id, subject_id, exam_id, title, assessment_type, assessed_on, max_marks, obtained_marks, score_percent)
VALUES (@uid, @dbms, @e_dbms, 'DBMS Mock Mid-Sem', 'MOCK_TEST', @today - INTERVAL 2 DAY, 50, 31, 62.00);
SET @a5 = LAST_INSERT_ID();
INSERT INTO assessment_topic_scores (user_id, assessment_id, topic_id, max_marks, obtained_marks, score_percent) VALUES
    (@uid, @a5, @t_ra, 20, 13, 65.00),
    (@uid, @a5, @t_sql, 30, 18, 60.00);

INSERT INTO assessments (user_id, subject_id, exam_id, title, assessment_type, assessed_on, max_marks, obtained_marks, score_percent)
VALUES (@uid, @os, NULL, 'OS Quiz 1', 'QUIZ', @today - INTERVAL 8 DAY, 20, 9, 45.00);
SET @a6 = LAST_INSERT_ID();
INSERT INTO assessment_topic_scores (user_id, assessment_id, topic_id, max_marks, obtained_marks, score_percent) VALUES
    (@uid, @a6, @t_sched, 10, 7, 70.00),
    (@uid, @a6, @t_sync, 10, 2, 20.00);

INSERT INTO assessments (user_id, subject_id, exam_id, title, assessment_type, assessed_on, max_marks, obtained_marks, score_percent)
VALUES (@uid, @math, NULL, 'Laplace Transforms Assignment', 'ASSIGNMENT', @today - INTERVAL 12 DAY, 50, 41, 82.00);
SET @a7 = LAST_INSERT_ID();
INSERT INTO assessment_topic_scores (user_id, assessment_id, topic_id, max_marks, obtained_marks, score_percent) VALUES
    (@uid, @a7, @t_laplace, 50, 41, 82.00);

-- ---------------------------------------------------------------------
-- Topic mastery, computed with exactly the application's formula:
--   mastery  = 0.60 x latest score + 0.40 x average of all scores
--   previous = the same formula without the latest score (for the trend arrow)
-- ---------------------------------------------------------------------
INSERT INTO topic_mastery (user_id, topic_id, mastery_score, latest_score, historical_average, previous_mastery_score,
                           assessment_count, mastery_level, last_assessed_on)
WITH ordered AS (
    SELECT ats.user_id, ats.topic_id, ats.score_percent, a.assessed_on,
           ROW_NUMBER() OVER (PARTITION BY ats.topic_id ORDER BY a.assessed_on DESC, a.id DESC) AS rn,
           COUNT(*) OVER (PARTITION BY ats.topic_id) AS cnt
    FROM assessment_topic_scores ats
    JOIN assessments a ON a.id = ats.assessment_id
    WHERE ats.user_id = @uid
), agg AS (
    SELECT topic_id,
           MAX(user_id) AS user_id,
           MAX(cnt) AS cnt,
           AVG(score_percent) AS avg_all,
           MAX(assessed_on) AS last_date,
           MAX(CASE WHEN rn = 1 THEN score_percent END) AS latest,
           MAX(CASE WHEN rn = 2 THEN score_percent END) AS second_latest,
           CASE WHEN MAX(cnt) > 1
                THEN (SUM(score_percent) - MAX(CASE WHEN rn = 1 THEN score_percent END)) / (MAX(cnt) - 1)
           END AS avg_without_latest
    FROM ordered
    GROUP BY topic_id
), calc AS (
    SELECT agg.*,
           ROUND(0.6 * latest + 0.4 * avg_all, 2) AS mastery,
           CASE WHEN cnt > 1 THEN ROUND(0.6 * second_latest + 0.4 * avg_without_latest, 2) END AS prev_mastery
    FROM agg
)
SELECT user_id, topic_id, mastery, latest, ROUND(avg_all, 2), prev_mastery, cnt,
       CASE WHEN mastery < 40 THEN 'WEAK'
            WHEN mastery < 65 THEN 'NEEDS_IMPROVEMENT'
            WHEN mastery < 80 THEN 'GOOD'
            ELSE 'STRONG' END,
       last_date
FROM calc;

-- ---------------------------------------------------------------------
-- Study history of the last two weeks (an archived plan)
-- Local 18:00 in India = 12:30 UTC, 19:00 = 13:30 UTC, 20:00 = 14:30 UTC
-- ---------------------------------------------------------------------
INSERT INTO study_plans (user_id, start_date, end_date, status, generated_at, total_planned_minutes, session_count, strategy_summary)
VALUES (@uid, @today - INTERVAL 13 DAY, @today - INTERVAL 1 DAY, 'ARCHIVED', NOW(6) - INTERVAL 13 DAY, 1150, 23,
        'Previous two-week plan (sample data).');
SET @plan = LAST_INSERT_ID();

INSERT INTO study_sessions (user_id, plan_id, subject_id, topic_id, session_type, source, status, start_at, end_at,
                            planned_minutes, actual_minutes, started_at, completed_at, locked, priority_score, reason)
VALUES
 (@uid, @plan, @dsa,  @t_stacks,  'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 13 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 13 DAY, '13:20:00'), 50, 50, TIMESTAMP(@today - INTERVAL 13 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 13 DAY, '13:20:00'), FALSE, 58.50, 'Exam in 25 days'),
 (@uid, @plan, @dbms, @t_ra,      'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 13 DAY, '13:30:00'), TIMESTAMP(@today - INTERVAL 13 DAY, '14:20:00'), 50, 45, TIMESTAMP(@today - INTERVAL 13 DAY, '13:30:00'), TIMESTAMP(@today - INTERVAL 13 DAY, '14:15:00'), FALSE, 55.00, 'Exam in 19 days'),
 (@uid, @plan, @os,   @t_sched,   'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 12 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 12 DAY, '13:20:00'), 50, 50, TIMESTAMP(@today - INTERVAL 12 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 12 DAY, '13:20:00'), FALSE, 51.20, 'Balanced syllabus coverage'),
 (@uid, @plan, @math, @t_fourier, 'STUDY', 'GENERATED', 'MISSED',     TIMESTAMP(@today - INTERVAL 12 DAY, '13:30:00'), TIMESTAMP(@today - INTERVAL 12 DAY, '14:20:00'), 50, NULL, NULL, NULL, FALSE, 47.80, 'Hard topic (difficulty 4/5)'),
 (@uid, @plan, @dsa,  @t_trees,   'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 11 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 11 DAY, '13:20:00'), 50, 50, TIMESTAMP(@today - INTERVAL 11 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 11 DAY, '13:20:00'), FALSE, 62.10, 'Hard topic (difficulty 4/5)'),
 (@uid, @plan, @os,   @t_sched,   'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 11 DAY, '13:30:00'), TIMESTAMP(@today - INTERVAL 11 DAY, '14:20:00'), 50, 50, TIMESTAMP(@today - INTERVAL 11 DAY, '13:30:00'), TIMESTAMP(@today - INTERVAL 11 DAY, '14:20:00'), FALSE, 51.20, 'Balanced syllabus coverage'),
 (@uid, @plan, @dbms, @t_sql,     'STUDY', 'GENERATED', 'INCOMPLETE', TIMESTAMP(@today - INTERVAL 10 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 10 DAY, '13:20:00'), 50, 25, TIMESTAMP(@today - INTERVAL 10 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 10 DAY, '12:55:00'), FALSE, 54.30, 'Exam in 16 days'),
 (@uid, @plan, @dsa,  @t_trees,   'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 9 DAY, '04:00:00'),  TIMESTAMP(@today - INTERVAL 9 DAY, '04:50:00'),  50, 50, TIMESTAMP(@today - INTERVAL 9 DAY, '04:00:00'),  TIMESTAMP(@today - INTERVAL 9 DAY, '04:50:00'),  FALSE, 62.10, 'Hard topic (difficulty 4/5)'),
 (@uid, @plan, @os,   @t_sync,    'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 9 DAY, '05:00:00'),  TIMESTAMP(@today - INTERVAL 9 DAY, '05:50:00'),  50, 50, TIMESTAMP(@today - INTERVAL 9 DAY, '05:00:00'),  TIMESTAMP(@today - INTERVAL 9 DAY, '05:50:00'),  FALSE, 60.40, 'Hard topic (difficulty 4/5)'),
 (@uid, @plan, @math, @t_fourier, 'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 9 DAY, '10:30:00'), TIMESTAMP(@today - INTERVAL 9 DAY, '11:20:00'), 50, 50, TIMESTAMP(@today - INTERVAL 9 DAY, '10:30:00'), TIMESTAMP(@today - INTERVAL 9 DAY, '11:20:00'), FALSE, 47.80, 'Hard topic (difficulty 4/5)'),
 (@uid, @plan, @dbms, @t_sql,     'STUDY', 'GENERATED', 'MISSED',     TIMESTAMP(@today - INTERVAL 8 DAY, '04:00:00'),  TIMESTAMP(@today - INTERVAL 8 DAY, '04:50:00'),  50, NULL, NULL, NULL, FALSE, 54.30, 'Exam in 14 days'),
 (@uid, @plan, @dsa,  @t_stacks,  'REVISION', 'GENERATED', 'COMPLETED', TIMESTAMP(@today - INTERVAL 8 DAY, '10:30:00'), TIMESTAMP(@today - INTERVAL 8 DAY, '11:00:00'), 30, 30, TIMESTAMP(@today - INTERVAL 8 DAY, '10:30:00'), TIMESTAMP(@today - INTERVAL 8 DAY, '11:00:00'), FALSE, 49.00, 'Spaced-repetition review due today'),
 (@uid, @plan, @os,   @t_sync,    'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 7 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 7 DAY, '13:20:00'), 50, 50, TIMESTAMP(@today - INTERVAL 7 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 7 DAY, '13:20:00'), FALSE, 64.10, 'Weak topic (mastery 20%)'),
 (@uid, @plan, @dbms, @t_ra,      'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 7 DAY, '13:30:00'), TIMESTAMP(@today - INTERVAL 7 DAY, '14:20:00'), 50, 50, TIMESTAMP(@today - INTERVAL 7 DAY, '13:30:00'), TIMESTAMP(@today - INTERVAL 7 DAY, '14:20:00'), FALSE, 56.00, 'Exam in 13 days'),
 (@uid, @plan, @dsa,  @t_trees,   'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 6 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 6 DAY, '13:20:00'), 50, 48, TIMESTAMP(@today - INTERVAL 6 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 6 DAY, '13:18:00'), FALSE, 66.40, 'Weak topic (mastery 35%)'),
 (@uid, @plan, @math, @t_fourier, 'STUDY', 'GENERATED', 'INCOMPLETE', TIMESTAMP(@today - INTERVAL 6 DAY, '13:30:00'), TIMESTAMP(@today - INTERVAL 6 DAY, '14:20:00'), 50, 30, TIMESTAMP(@today - INTERVAL 6 DAY, '13:30:00'), TIMESTAMP(@today - INTERVAL 6 DAY, '14:00:00'), FALSE, 47.80, 'Hard topic (difficulty 4/5)'),
 (@uid, @plan, @os,   @t_sync,    'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 5 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 5 DAY, '13:20:00'), 50, 50, TIMESTAMP(@today - INTERVAL 5 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 5 DAY, '13:20:00'), FALSE, 64.10, 'Weak topic (mastery 20%)'),
 (@uid, @plan, @dbms, @t_sql,     'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 4 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 4 DAY, '13:20:00'), 50, 50, TIMESTAMP(@today - INTERVAL 4 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 4 DAY, '13:20:00'), FALSE, 58.90, 'Exam in 10 days'),
 (@uid, @plan, @dsa,  @t_trees,   'STUDY', 'GENERATED', 'MISSED',     TIMESTAMP(@today - INTERVAL 4 DAY, '13:30:00'), TIMESTAMP(@today - INTERVAL 4 DAY, '14:20:00'), 50, NULL, NULL, NULL, FALSE, 66.40, 'Weak topic (mastery 35%)'),
 (@uid, @plan, @dsa,  @t_trees,   'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 3 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 3 DAY, '13:20:00'), 50, 50, TIMESTAMP(@today - INTERVAL 3 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 3 DAY, '13:20:00'), FALSE, 69.80, 'Weak topic (mastery 34%)'),
 (@uid, @plan, @os,   @t_sync,    'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 2 DAY, '04:00:00'),  TIMESTAMP(@today - INTERVAL 2 DAY, '04:50:00'),  50, 45, TIMESTAMP(@today - INTERVAL 2 DAY, '04:00:00'),  TIMESTAMP(@today - INTERVAL 2 DAY, '04:45:00'),  FALSE, 64.10, 'Weak topic (mastery 20%)'),
 (@uid, @plan, @dbms, @t_ra,      'REVISION', 'GENERATED', 'COMPLETED', TIMESTAMP(@today - INTERVAL 2 DAY, '05:00:00'), TIMESTAMP(@today - INTERVAL 2 DAY, '05:30:00'), 30, 30, TIMESTAMP(@today - INTERVAL 2 DAY, '05:00:00'), TIMESTAMP(@today - INTERVAL 2 DAY, '05:30:00'), FALSE, 52.00, 'Spaced-repetition review due today'),
 (@uid, @plan, @math, @t_fourier, 'STUDY', 'GENERATED', 'COMPLETED',  TIMESTAMP(@today - INTERVAL 1 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 1 DAY, '13:20:00'), 50, 50, TIMESTAMP(@today - INTERVAL 1 DAY, '12:30:00'), TIMESTAMP(@today - INTERVAL 1 DAY, '13:20:00'), FALSE, 49.50, 'Hard topic (difficulty 4/5)');

-- ---------------------------------------------------------------------
-- Revision history consistent with the topics' SM-2 state
-- ---------------------------------------------------------------------
INSERT INTO revision_logs (user_id, topic_id, rating, revised_on, due_date, revised_at, duration_minutes,
                           previous_ease_factor, new_ease_factor, previous_interval_days, new_interval_days,
                           repetitions, next_revision_date, notes)
VALUES
 (@uid, @t_arrays, 'GOOD', @today - INTERVAL 24 DAY, @today - INTERVAL 24 DAY, TIMESTAMP(@today - INTERVAL 24 DAY, '13:00:00'), 20, 2.50, 2.50, 0, 1, 1, @today - INTERVAL 23 DAY, NULL),
 (@uid, @t_arrays, 'GOOD', @today - INTERVAL 16 DAY, @today - INTERVAL 23 DAY, TIMESTAMP(@today - INTERVAL 16 DAY, '13:00:00'), 20, 2.50, 2.50, 1, 6, 2, @today - INTERVAL 10 DAY, 'Late review'),
 (@uid, @t_arrays, 'GOOD', @today - INTERVAL 10 DAY, @today - INTERVAL 10 DAY, TIMESTAMP(@today - INTERVAL 10 DAY, '13:00:00'), 25, 2.50, 2.50, 6, 15, 3, @today + INTERVAL 5 DAY, NULL),
 (@uid, @t_lists,  'GOOD', @today - INTERVAL 14 DAY, @today - INTERVAL 17 DAY, TIMESTAMP(@today - INTERVAL 14 DAY, '13:00:00'), 20, 2.50, 2.50, 0, 1, 1, @today - INTERVAL 13 DAY, NULL),
 (@uid, @t_lists,  'GOOD', @today - INTERVAL 8 DAY,  @today - INTERVAL 13 DAY, TIMESTAMP(@today - INTERVAL 8 DAY, '13:00:00'),  25, 2.50, 2.50, 1, 6, 2, @today - INTERVAL 2 DAY, 'Late review'),
 (@uid, @t_stacks, 'HARD', @today - INTERVAL 1 DAY,  @today - INTERVAL 8 DAY,  TIMESTAMP(@today - INTERVAL 1 DAY, '13:00:00'),  30, 2.50, 2.35, 0, 1, 1, @today, 'Queues with two stacks still confusing'),
 (@uid, @t_er,     'GOOD', @today - INTERVAL 14 DAY, @today - INTERVAL 14 DAY, TIMESTAMP(@today - INTERVAL 14 DAY, '14:00:00'), 15, 2.50, 2.50, 0, 1, 1, @today - INTERVAL 13 DAY, NULL),
 (@uid, @t_er,     'GOOD', @today - INTERVAL 3 DAY,  @today - INTERVAL 13 DAY, TIMESTAMP(@today - INTERVAL 3 DAY, '14:00:00'),  15, 2.50, 2.50, 1, 6, 2, @today + INTERVAL 3 DAY, 'Late review'),
 (@uid, @t_ra,     'GOOD', @today - INTERVAL 2 DAY,  @today - INTERVAL 6 DAY,  TIMESTAMP(@today - INTERVAL 2 DAY, '05:30:00'),  30, 2.50, 2.50, 0, 1, 1, @today - INTERVAL 1 DAY, NULL),
 (@uid, @t_sched,  'EASY', @today - INTERVAL 4 DAY,  @today - INTERVAL 5 DAY,  TIMESTAMP(@today - INTERVAL 4 DAY, '13:00:00'),  20, 2.50, 2.65, 0, 4, 1, @today, NULL),
 (@uid, @t_laplace,'GOOD', @today - INTERVAL 19 DAY, @today - INTERVAL 19 DAY, TIMESTAMP(@today - INTERVAL 19 DAY, '13:00:00'), 20, 2.50, 2.50, 0, 1, 1, @today - INTERVAL 18 DAY, NULL),
 (@uid, @t_laplace,'GOOD', @today - INTERVAL 12 DAY, @today - INTERVAL 18 DAY, TIMESTAMP(@today - INTERVAL 12 DAY, '13:00:00'), 25, 2.50, 2.50, 1, 6, 2, @today - INTERVAL 6 DAY, 'Late review'),
 (@uid, @t_laplace,'GOOD', @today - INTERVAL 6 DAY,  @today - INTERVAL 6 DAY,  TIMESTAMP(@today - INTERVAL 6 DAY, '13:00:00'),  25, 2.50, 2.50, 6, 15, 3, @today + INTERVAL 9 DAY, NULL);

-- ---------------------------------------------------------------------
-- Focus (Pomodoro) sessions of the last week
-- ---------------------------------------------------------------------
INSERT INTO focus_sessions (user_id, subject_id, topic_id, status, planned_minutes, started_at, total_paused_seconds,
                            pause_count, ended_at, actual_focus_seconds, client_reported_seconds)
VALUES
 (@uid, @dsa,  @t_trees,   'COMPLETED', 25, TIMESTAMP(@today - INTERVAL 6 DAY, '15:00:00'), 0,   0, TIMESTAMP(@today - INTERVAL 6 DAY, '15:25:00'), 1500, 1500),
 (@uid, @dsa,  @t_trees,   'COMPLETED', 25, TIMESTAMP(@today - INTERVAL 6 DAY, '15:30:00'), 120, 1, TIMESTAMP(@today - INTERVAL 6 DAY, '15:57:00'), 1500, 1500),
 (@uid, @os,   @t_sync,    'COMPLETED', 25, TIMESTAMP(@today - INTERVAL 5 DAY, '15:00:00'), 0,   0, TIMESTAMP(@today - INTERVAL 5 DAY, '15:25:00'), 1500, 1500),
 (@uid, @dbms, @t_sql,     'COMPLETED', 25, TIMESTAMP(@today - INTERVAL 4 DAY, '15:00:00'), 0,   0, TIMESTAMP(@today - INTERVAL 4 DAY, '15:20:00'), 1200, 1200),
 (@uid, @dsa,  @t_avl,     'CANCELLED', 25, TIMESTAMP(@today - INTERVAL 4 DAY, '15:30:00'), 0,   0, TIMESTAMP(@today - INTERVAL 4 DAY, '15:38:00'), 480,  NULL),
 (@uid, @math, @t_fourier, 'COMPLETED', 25, TIMESTAMP(@today - INTERVAL 3 DAY, '15:00:00'), 0,   0, TIMESTAMP(@today - INTERVAL 3 DAY, '15:25:00'), 1500, 1500),
 (@uid, @math, @t_fourier, 'COMPLETED', 25, TIMESTAMP(@today - INTERVAL 3 DAY, '15:30:00'), 0,   0, TIMESTAMP(@today - INTERVAL 3 DAY, '15:55:00'), 1500, 1500),
 (@uid, @os,   @t_sync,    'COMPLETED', 25, TIMESTAMP(@today - INTERVAL 2 DAY, '15:00:00'), 60,  1, TIMESTAMP(@today - INTERVAL 2 DAY, '15:26:00'), 1500, 1500),
 (@uid, @dsa,  @t_trees,   'COMPLETED', 25, TIMESTAMP(@today - INTERVAL 1 DAY, '15:00:00'), 0,   0, TIMESTAMP(@today - INTERVAL 1 DAY, '15:25:00'), 1500, 1500);

-- ---------------------------------------------------------------------
-- Readiness history (the application adds today's snapshot when it is viewed)
-- ---------------------------------------------------------------------
INSERT INTO readiness_snapshots (user_id, exam_id, snapshot_date, readiness_score, syllabus_completion, topic_mastery,
                                 mock_test_performance, revision_completion, data_confidence, data_sufficiency)
VALUES
 (@uid, @e_dsa,  @today - INTERVAL 9 DAY, 44.20, 33.33, 63.00, NULL,  50.00, 75.00, 'PARTIAL'),
 (@uid, @e_dsa,  @today - INTERVAL 6 DAY, 47.10, 50.00, 58.40, NULL,  33.33, 75.00, 'PARTIAL'),
 (@uid, @e_dsa,  @today - INTERVAL 3 DAY, 51.30, 50.00, 61.20, 58.00, 33.33, 100.00, 'SUFFICIENT'),
 (@uid, @e_dsa,  @today - INTERVAL 1 DAY, 52.80, 50.00, 61.20, 58.00, 33.33, 100.00, 'SUFFICIENT'),
 (@uid, @e_dbms, @today - INTERVAL 9 DAY, 41.00, 20.00, 71.67, NULL,  0.00,  75.00, 'PARTIAL'),
 (@uid, @e_dbms, @today - INTERVAL 6 DAY, 45.50, 40.00, 71.67, NULL,  50.00, 75.00, 'PARTIAL'),
 (@uid, @e_dbms, @today - INTERVAL 2 DAY, 55.40, 40.00, 69.30, 62.00, 50.00, 100.00, 'SUFFICIENT'),
 (@uid, @e_os,   @today - INTERVAL 8 DAY, 35.80, 20.00, 45.00, NULL,  0.00,  75.00, 'PARTIAL'),
 (@uid, @e_os,   @today - INTERVAL 4 DAY, 38.90, 20.00, 45.00, NULL,  100.00, 75.00, 'PARTIAL');

-- ---------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------
INSERT INTO notifications (user_id, notification_type, title, message, link, is_read, read_at, dedupe_key, created_at)
VALUES
 (@uid, 'REVISION_DUE', 'Revision reminder',
  'Some topics are due for revision today, including Stacks & Queues and Process Scheduling.', '/revision', FALSE, NULL,
  CONCAT('sample-revision-', @today), NOW(6) - INTERVAL 2 HOUR),
 (@uid, 'EXAM_UPCOMING', 'Exam in 6 days: DBMS Mid-Semester Exam',
  'DBMS Mid-Semester Exam (Database Management Systems) is coming up. Check your readiness and focus on weak topics.',
  '/exams/readiness', FALSE, NULL, CONCAT('sample-exam-', @today), NOW(6) - INTERVAL 5 HOUR),
 (@uid, 'SESSION_MISSED', 'Missed study session',
  'You missed Trees & Binary Search Trees four days ago. The work was included in your next plan.', '/planner', TRUE,
  NOW(6) - INTERVAL 3 DAY, CONCAT('sample-missed-', @today), NOW(6) - INTERVAL 4 DAY);

SELECT 'Sample data loaded. Sign in with demo@smartstudy.dev / Demo@12345' AS result;
