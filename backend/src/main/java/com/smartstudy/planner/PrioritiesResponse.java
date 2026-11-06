package com.smartstudy.planner;

import java.util.List;

/** GET /api/v1/planner/priorities - the configured weights plus every topic's priority breakdown. */
public record PrioritiesResponse(Weights weights, List<RecommendationResponse> items) {

    public record Weights(double weakness, double examUrgency, double difficulty, double revisionOverdue,
                          double subjectImportance) {
    }
}
