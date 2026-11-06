package com.smartstudy.assessment;

import java.util.List;

import jakarta.validation.Valid;

import com.smartstudy.common.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assessments")
public class AssessmentController {

    private final AssessmentService assessmentService;
    private final CurrentUser currentUser;

    public AssessmentController(AssessmentService assessmentService, CurrentUser currentUser) {
        this.assessmentService = assessmentService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<AssessmentResponse> list(@RequestParam(required = false) Long subjectId) {
        return assessmentService.list(currentUser.id(), subjectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentResponse create(@Valid @RequestBody AssessmentRequest request) {
        return assessmentService.create(currentUser.id(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        assessmentService.delete(currentUser.id(), id);
    }
}
