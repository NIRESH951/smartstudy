package com.smartstudy.performance;

import com.smartstudy.common.CurrentUser;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/performance")
public class PerformanceController {

    private final PerformanceService performanceService;
    private final CurrentUser currentUser;

    public PerformanceController(PerformanceService performanceService, CurrentUser currentUser) {
        this.performanceService = performanceService;
        this.currentUser = currentUser;
    }

    @GetMapping("/weak-topics")
    public WeakTopicResponse weakTopics() {
        return performanceService.weakTopics(currentUser.id());
    }

    @GetMapping("/summary")
    public PerformanceSummaryResponse summary() {
        return performanceService.summary(currentUser.id());
    }

    @GetMapping("/topics/{topicId}/history")
    public TopicHistoryResponse topicHistory(@PathVariable Long topicId) {
        return performanceService.topicHistory(currentUser.id(), topicId);
    }
}
