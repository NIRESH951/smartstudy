package com.smartstudy.subject;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.smartstudy.common.BaseEntity;

/** An academic subject (course) owned by one student. */
@Entity
@Table(name = "subjects")
public class Subject extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", length = 20)
    private String code;

    @Column(name = "color", nullable = false, length = 7)
    private String color = "#10B981";

    /** 1 (low) .. 5 (critical); feeds the "Subject Importance" factor of the priority formula. */
    @Column(name = "importance", nullable = false)
    private int importance = 3;

    @Column(name = "target_score", precision = 5, scale = 2)
    private BigDecimal targetScore;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    protected Subject() {
    }

    public Subject(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getImportance() {
        return importance;
    }

    public void setImportance(int importance) {
        this.importance = importance;
    }

    public BigDecimal getTargetScore() {
        return targetScore;
    }

    public void setTargetScore(BigDecimal targetScore) {
        this.targetScore = targetScore;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }
}
