package com.smartstudy.performance;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.smartstudy.common.BaseEntity;
import com.smartstudy.topic.Topic;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Cached mastery result of an assessed topic (recomputed whenever its assessments change). */
@Entity
@Table(name = "topic_mastery")
public class TopicMastery extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Topic topic;

    @Column(name = "mastery_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal masteryScore;

    @Column(name = "latest_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal latestScore;

    @Column(name = "historical_average", nullable = false, precision = 5, scale = 2)
    private BigDecimal historicalAverage;

    @Column(name = "previous_mastery_score", precision = 5, scale = 2)
    private BigDecimal previousMasteryScore;

    @Column(name = "assessment_count", nullable = false)
    private int assessmentCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "mastery_level", nullable = false, length = 20)
    private MasteryLevel masteryLevel;

    @Column(name = "last_assessed_on", nullable = false)
    private LocalDate lastAssessedOn;

    protected TopicMastery() {
    }

    public TopicMastery(Long userId, Topic topic) {
        this.userId = userId;
        this.topic = topic;
    }

    /** Applies a freshly computed result and remembers the previous score for trend display. */
    public void apply(MasteryCalculator.MasteryResult result, LocalDate lastAssessedOn) {
        this.previousMasteryScore = this.masteryScore;
        this.masteryScore = BigDecimal.valueOf(result.masteryScore()).setScale(2, java.math.RoundingMode.HALF_UP);
        this.latestScore = BigDecimal.valueOf(result.latestScore()).setScale(2, java.math.RoundingMode.HALF_UP);
        this.historicalAverage = BigDecimal.valueOf(result.historicalAverage())
                .setScale(2, java.math.RoundingMode.HALF_UP);
        this.assessmentCount = result.assessmentCount();
        this.masteryLevel = result.level();
        this.lastAssessedOn = lastAssessedOn;
    }

    public Long getUserId() {
        return userId;
    }

    public Topic getTopic() {
        return topic;
    }

    public BigDecimal getMasteryScore() {
        return masteryScore;
    }

    public BigDecimal getLatestScore() {
        return latestScore;
    }

    public BigDecimal getHistoricalAverage() {
        return historicalAverage;
    }

    public BigDecimal getPreviousMasteryScore() {
        return previousMasteryScore;
    }

    public int getAssessmentCount() {
        return assessmentCount;
    }

    public MasteryLevel getMasteryLevel() {
        return masteryLevel;
    }

    public LocalDate getLastAssessedOn() {
        return lastAssessedOn;
    }
}
