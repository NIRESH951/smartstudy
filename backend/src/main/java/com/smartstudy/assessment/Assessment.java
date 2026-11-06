package com.smartstudy.assessment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.smartstudy.common.BaseEntity;
import com.smartstudy.exam.Exam;
import com.smartstudy.subject.Subject;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Subject-level marks of an exam, quiz, assignment or mock test, optionally broken down by topic. */
@Entity
@Table(name = "assessments")
public class Assessment extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Exam exam;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type", nullable = false, length = 20)
    private AssessmentType assessmentType;

    @Column(name = "assessed_on", nullable = false)
    private LocalDate assessedOn;

    @Column(name = "max_marks", nullable = false, precision = 7, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "obtained_marks", nullable = false, precision = 7, scale = 2)
    private BigDecimal obtainedMarks;

    @Column(name = "score_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal scorePercent;

    @Column(name = "notes", length = 1000)
    private String notes;

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<AssessmentTopicScore> topicScores = new ArrayList<>();

    protected Assessment() {
    }

    public Assessment(Long userId, Subject subject, String title, AssessmentType assessmentType,
                      LocalDate assessedOn, BigDecimal maxMarks, BigDecimal obtainedMarks, BigDecimal scorePercent) {
        this.userId = userId;
        this.subject = subject;
        this.title = title;
        this.assessmentType = assessmentType;
        this.assessedOn = assessedOn;
        this.maxMarks = maxMarks;
        this.obtainedMarks = obtainedMarks;
        this.scorePercent = scorePercent;
    }

    public void addTopicScore(AssessmentTopicScore score) {
        topicScores.add(score);
    }

    public Long getUserId() {
        return userId;
    }

    public Subject getSubject() {
        return subject;
    }

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    public String getTitle() {
        return title;
    }

    public AssessmentType getAssessmentType() {
        return assessmentType;
    }

    public LocalDate getAssessedOn() {
        return assessedOn;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<AssessmentTopicScore> getTopicScores() {
        return topicScores;
    }
}
