package com.smartstudy.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-specific settings bound from the {@code smartstudy.*} keys in application.yml.
 *
 * <p>Every value has a sensible default so the application (and the unit tests, which
 * create this class with {@code new}) work without any configuration.</p>
 */
@ConfigurationProperties(prefix = "smartstudy")
public class SmartStudyProperties {

    private final Cors cors = new Cors();
    private final Scheduling scheduling = new Scheduling();
    private final Planner planner = new Planner();
    private final Focus focus = new Focus();

    public Cors getCors() {
        return cors;
    }

    public Scheduling getScheduling() {
        return scheduling;
    }

    public Planner getPlanner() {
        return planner;
    }

    public Focus getFocus() {
        return focus;
    }

    /** Browser origins that may call the API with credentials (cookies). */
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    /** Background jobs: missed-session detection, reminders and readiness snapshots. */
    public static class Scheduling {
        private boolean enabled = true;
        private long intervalMs = 900_000L;
        /** Delay after start-up before the first run (read by ReminderScheduler's @Scheduled). */
        private long initialDelayMs = 60_000L;

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }
    }

    /** Settings of the Adaptive Study Planning Engine. */
    public static class Planner {
        private final Weights weights = new Weights();
        /** Weakness score (0-100) assumed for a topic that has never been assessed. */
        private double unassessedWeakness = 50.0;
        /** Exams further away than this many days get an urgency score of 0. */
        private int urgencyHorizonDays = 30;
        /** A revision this many days overdue reaches the maximum overdue score of 100. */
        private int revisionOverdueCapDays = 14;
        /** Default number of days covered by a generated timetable. */
        private int defaultHorizonDays = 7;
        /** Largest timetable a student can generate in one request. */
        private int maxHorizonDays = 28;
        /** Free time shorter than this is not turned into a study session. */
        private int minSlotMinutes = 25;
        /** Each extra session given to the same topic multiplies its priority by this factor. */
        private double repeatDecayFactor = 0.80;
        /** Upper bound of sessions for one topic on one day. */
        private int maxSessionsPerTopicPerDay = 2;
        /** Length of a spaced-repetition revision session. */
        private int revisionSessionMinutes = 30;
        /** A planned session is marked as missed this many minutes after its end time. */
        private int missedGraceMinutes = 30;

        public Weights getWeights() {
            return weights;
        }

        public double getUnassessedWeakness() {
            return unassessedWeakness;
        }

        public void setUnassessedWeakness(double unassessedWeakness) {
            this.unassessedWeakness = unassessedWeakness;
        }

        public int getUrgencyHorizonDays() {
            return urgencyHorizonDays;
        }

        public void setUrgencyHorizonDays(int urgencyHorizonDays) {
            this.urgencyHorizonDays = urgencyHorizonDays;
        }

        public int getRevisionOverdueCapDays() {
            return revisionOverdueCapDays;
        }

        public void setRevisionOverdueCapDays(int revisionOverdueCapDays) {
            this.revisionOverdueCapDays = revisionOverdueCapDays;
        }

        public int getDefaultHorizonDays() {
            return defaultHorizonDays;
        }

        public void setDefaultHorizonDays(int defaultHorizonDays) {
            this.defaultHorizonDays = defaultHorizonDays;
        }

        public int getMaxHorizonDays() {
            return maxHorizonDays;
        }

        public void setMaxHorizonDays(int maxHorizonDays) {
            this.maxHorizonDays = maxHorizonDays;
        }

        public int getMinSlotMinutes() {
            return minSlotMinutes;
        }

        public void setMinSlotMinutes(int minSlotMinutes) {
            this.minSlotMinutes = minSlotMinutes;
        }

        public double getRepeatDecayFactor() {
            return repeatDecayFactor;
        }

        public void setRepeatDecayFactor(double repeatDecayFactor) {
            this.repeatDecayFactor = repeatDecayFactor;
        }

        public int getMaxSessionsPerTopicPerDay() {
            return maxSessionsPerTopicPerDay;
        }

        public void setMaxSessionsPerTopicPerDay(int maxSessionsPerTopicPerDay) {
            this.maxSessionsPerTopicPerDay = maxSessionsPerTopicPerDay;
        }

        public int getRevisionSessionMinutes() {
            return revisionSessionMinutes;
        }

        public void setRevisionSessionMinutes(int revisionSessionMinutes) {
            this.revisionSessionMinutes = revisionSessionMinutes;
        }

        public int getMissedGraceMinutes() {
            return missedGraceMinutes;
        }

        public void setMissedGraceMinutes(int missedGraceMinutes) {
            this.missedGraceMinutes = missedGraceMinutes;
        }
    }

    /**
     * Weights of the priority formula
     * {@code Priority = 0.30 W + 0.25 U + 0.20 D + 0.15 R + 0.10 I}.
     * They do not have to add up to 1: the calculator divides by their sum.
     */
    public static class Weights {
        private double weakness = 0.30;
        private double examUrgency = 0.25;
        private double difficulty = 0.20;
        private double revisionOverdue = 0.15;
        private double subjectImportance = 0.10;

        public double getWeakness() {
            return weakness;
        }

        public void setWeakness(double weakness) {
            this.weakness = weakness;
        }

        public double getExamUrgency() {
            return examUrgency;
        }

        public void setExamUrgency(double examUrgency) {
            this.examUrgency = examUrgency;
        }

        public double getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(double difficulty) {
            this.difficulty = difficulty;
        }

        public double getRevisionOverdue() {
            return revisionOverdue;
        }

        public void setRevisionOverdue(double revisionOverdue) {
            this.revisionOverdue = revisionOverdue;
        }

        public double getSubjectImportance() {
            return subjectImportance;
        }

        public void setSubjectImportance(double subjectImportance) {
            this.subjectImportance = subjectImportance;
        }
    }

    /** Pomodoro / focus-mode limits enforced by the server. */
    public static class Focus {
        /**
         * A running session that is still open this many seconds after its planned end (for example
         * because the browser tab was closed) is completed automatically with the planned length.
         */
        private int autoCompleteGraceSeconds = 600;
        private int maxPlannedMinutes = 120;

        public int getAutoCompleteGraceSeconds() {
            return autoCompleteGraceSeconds;
        }

        public void setAutoCompleteGraceSeconds(int autoCompleteGraceSeconds) {
            this.autoCompleteGraceSeconds = autoCompleteGraceSeconds;
        }

        public int getMaxPlannedMinutes() {
            return maxPlannedMinutes;
        }

        public void setMaxPlannedMinutes(int maxPlannedMinutes) {
            this.maxPlannedMinutes = maxPlannedMinutes;
        }
    }
}
