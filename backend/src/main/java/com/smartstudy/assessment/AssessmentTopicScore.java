package com.smartstudy.assessment;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.smartstudy.common.BaseEntity;
import com.smartstudy.topic.Topic;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Marks obtained for one topic inside an assessment. Feeds the topic mastery calculation. */
@Entity
@Table(name = "assessment_topic_scores")
public class AssessmentTopicScore extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Topic topic;

    @Column(name = "max_marks", nullable = false, precision = 7, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "obtained_marks", nullable = false, precision = 7, scale = 2)
    private BigDecimal obtainedMarks;

    @Column(name = "score_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal scorePercent;

    protected AssessmentTopicScore() {
    }

    public AssessmentTopicScore(Long userId, Assessment assessment, Topic topic, BigDecimal maxMarks,
                                BigDecimal obtainedMarks, BigDecimal scorePercent) {
        this.userId = userId;
        this.assessment = assessment;
        this.topic = topic;
        this.maxMarks = maxMarks;
        this.obtainedMarks = obtainedMarks;
        this.scorePercent = scorePercent;
    }

    public Long getUserId() {
        return userId;
    }

    public Assessment getAssessment() {
        return assessment;
    }

    public Topic getTopic() {
        return topic;
    }

    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public BigDecimal getObtainedMarks() {
        return obtainedMarks;
    }

    public BigDecimal getScorePercent() {
        return scorePercent;
    }
}
