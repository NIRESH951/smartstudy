package com.smartstudy.readiness;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.smartstudy.common.BaseEntity;
import com.smartstudy.common.ScoreMath;
import com.smartstudy.exam.Exam;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Readiness of one exam on one day (unique per exam and date) - the source of the trend chart. */
@Entity
@Table(name = "readiness_snapshots")
public class ReadinessSnapshot extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Exam exam;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "readiness_score", precision = 5, scale = 2)
    private BigDecimal readinessScore;

    @Column(name = "syllabus_completion", precision = 5, scale = 2)
    private BigDecimal syllabusCompletion;

    @Column(name = "topic_mastery", precision = 5, scale = 2)
    private BigDecimal topicMastery;

    @Column(name = "mock_test_performance", precision = 5, scale = 2)
    private BigDecimal mockTestPerformance;

    @Column(name = "revision_completion", precision = 5, scale = 2)
    private BigDecimal revisionCompletion;

    @Column(name = "data_confidence", nullable = false, precision = 5, scale = 2)
    private BigDecimal dataConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_sufficiency", nullable = false, length = 20)
    private DataSufficiency dataSufficiency;

    protected ReadinessSnapshot() {
    }

    public ReadinessSnapshot(Long userId, Exam exam, LocalDate snapshotDate) {
        this.userId = userId;
        this.exam = exam;
        this.snapshotDate = snapshotDate;
    }

    public void apply(ReadinessCalculator.ReadinessResult result, ReadinessCalculator.ReadinessInputs inputs) {
        this.readinessScore = ScoreMath.toDecimalOrNull(result.readinessScore());
        this.syllabusCompletion = ScoreMath.toDecimalOrNull(inputs.syllabusCompletion());
        this.topicMastery = ScoreMath.toDecimalOrNull(inputs.topicMastery());
        this.mockTestPerformance = ScoreMath.toDecimalOrNull(inputs.mockTestPerformance());
        this.revisionCompletion = ScoreMath.toDecimalOrNull(inputs.revisionCompletion());
        this.dataConfidence = ScoreMath.toDecimal(result.dataConfidence());
        this.dataSufficiency = result.dataSufficiency();
    }

    public Long getUserId() {
        return userId;
    }

    public Exam getExam() {
        return exam;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public BigDecimal getReadinessScore() {
        return readinessScore;
    }

    public BigDecimal getSyllabusCompletion() {
        return syllabusCompletion;
    }

    public BigDecimal getTopicMastery() {
        return topicMastery;
    }

    public BigDecimal getMockTestPerformance() {
        return mockTestPerformance;
    }

    public BigDecimal getRevisionCompletion() {
        return revisionCompletion;
    }

    public BigDecimal getDataConfidence() {
        return dataConfidence;
    }

    public DataSufficiency getDataSufficiency() {
        return dataSufficiency;
    }
}
