package com.smartstudy.subject;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subjects")
public class SubjectController {

    private final SubjectService subjectService;
    private final CurrentUser currentUser;

    public SubjectController(SubjectService subjectService, CurrentUser currentUser) {
        this.subjectService = subjectService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<SubjectResponse> list() {
        return subjectService.list(currentUser.id());
    }

    @GetMapping("/{id}")
    public SubjectResponse get(@PathVariable Long id) {
        return subjectService.get(currentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectResponse create(@Valid @RequestBody SubjectRequest request) {
        return subjectService.create(currentUser.id(), request);
    }

    @PutMapping("/{id}")
    public SubjectResponse update(@PathVariable Long id, @Valid @RequestBody SubjectRequest request) {
        return subjectService.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        subjectService.delete(currentUser.id(), id);
    }
}
