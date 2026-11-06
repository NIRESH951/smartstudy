package com.smartstudy.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.smartstudy.common.BaseEntity;

/** A student account together with study and Pomodoro preferences. */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role = UserRole.STUDENT;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "UTC";

    /** Maximum study minutes the planner may schedule on one day. */
    @Column(name = "daily_study_minutes", nullable = false)
    private int dailyStudyMinutes = 180;

    /** Maximum study minutes the planner may schedule in one week (Monday-Sunday). */
    @Column(name = "weekly_study_minutes", nullable = false)
    private int weeklyStudyMinutes = 900;

    @Column(name = "preferred_session_minutes", nullable = false)
    private int preferredSessionMinutes = 50;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes = 10;

    @Column(name = "focus_minutes", nullable = false)
    private int focusMinutes = 25;

    @Column(name = "short_break_minutes", nullable = false)
    private int shortBreakMinutes = 5;

    @Column(name = "long_break_minutes", nullable = false)
    private int longBreakMinutes = 15;

    @Column(name = "long_break_interval", nullable = false)
    private int longBreakInterval = 4;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {
    }

    public User(String fullName, String email, String passwordHash, String timeZone) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.timeZone = timeZone;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public int getDailyStudyMinutes() {
        return dailyStudyMinutes;
    }

    public void setDailyStudyMinutes(int dailyStudyMinutes) {
        this.dailyStudyMinutes = dailyStudyMinutes;
    }

    public int getWeeklyStudyMinutes() {
        return weeklyStudyMinutes;
    }

    public void setWeeklyStudyMinutes(int weeklyStudyMinutes) {
        this.weeklyStudyMinutes = weeklyStudyMinutes;
    }

    public int getPreferredSessionMinutes() {
        return preferredSessionMinutes;
    }

    public void setPreferredSessionMinutes(int preferredSessionMinutes) {
        this.preferredSessionMinutes = preferredSessionMinutes;
    }

    public int getBreakMinutes() {
        return breakMinutes;
    }

    public void setBreakMinutes(int breakMinutes) {
        this.breakMinutes = breakMinutes;
    }

    public int getFocusMinutes() {
        return focusMinutes;
    }

    public void setFocusMinutes(int focusMinutes) {
        this.focusMinutes = focusMinutes;
    }

    public int getShortBreakMinutes() {
        return shortBreakMinutes;
    }

    public void setShortBreakMinutes(int shortBreakMinutes) {
        this.shortBreakMinutes = shortBreakMinutes;
    }

    public int getLongBreakMinutes() {
        return longBreakMinutes;
    }

    public void setLongBreakMinutes(int longBreakMinutes) {
        this.longBreakMinutes = longBreakMinutes;
    }

    public int getLongBreakInterval() {
        return longBreakInterval;
    }

    public void setLongBreakInterval(int longBreakInterval) {
        this.longBreakInterval = longBreakInterval;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
