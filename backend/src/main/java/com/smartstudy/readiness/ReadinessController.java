package com.smartstudy.readiness;

import java.util.List;

import com.smartstudy.common.CurrentUser;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReadinessController {

    private final ReadinessService readinessService;
    private final CurrentUser currentUser;

    public ReadinessController(ReadinessService readinessService, CurrentUser currentUser) {
        this.readinessService = readinessService;
        this.currentUser = currentUser;
    }

    @GetMapping("/exams/{id}/readiness")
    public ReadinessResponse forExam(@PathVariable Long id) {
        return readinessService.forExam(currentUser.id(), id);
    }

    @GetMapping("/readiness")
    public List<ReadinessResponse.OverviewItem> overview() {
        return readinessService.overview(currentUser.id());
    }
}
