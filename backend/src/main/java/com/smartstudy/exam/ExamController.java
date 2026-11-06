package com.smartstudy.exam;

import java.util.List;

import jakarta.validation.Valid;

import com.smartstudy.common.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exams")
public class ExamController {

    private final ExamService examService;
    private final CurrentUser currentUser;

    public ExamController(ExamService examService, CurrentUser currentUser) {
        this.examService = examService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<ExamResponse> list(@RequestParam(defaultValue = "false") boolean upcoming) {
        return examService.list(currentUser.id(), upcoming);
    }

    @GetMapping("/{id}")
    public ExamResponse get(@PathVariable Long id) {
        return examService.get(currentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExamResponse create(@Valid @RequestBody ExamRequest request) {
        return examService.create(currentUser.id(), request);
    }

    @PutMapping("/{id}")
    public ExamResponse update(@PathVariable Long id, @Valid @RequestBody ExamRequest request) {
        return examService.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        examService.delete(currentUser.id(), id);
    }
}
