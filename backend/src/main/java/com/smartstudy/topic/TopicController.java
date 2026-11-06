package com.smartstudy.topic;

import java.util.List;

import jakarta.validation.Valid;

import com.smartstudy.common.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TopicController {

    private final TopicService topicService;
    private final CurrentUser currentUser;

    public TopicController(TopicService topicService, CurrentUser currentUser) {
        this.topicService = topicService;
        this.currentUser = currentUser;
    }

    @GetMapping("/subjects/{subjectId}/topics")
    public List<TopicResponse> listBySubject(@PathVariable Long subjectId) {
        return topicService.listBySubject(currentUser.id(), subjectId);
    }

    @PostMapping("/subjects/{subjectId}/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public TopicResponse create(@PathVariable Long subjectId, @Valid @RequestBody TopicRequest request) {
        return topicService.create(currentUser.id(), subjectId, request);
    }

    @GetMapping("/topics")
    public List<TopicResponse> listAll() {
        return topicService.listAll(currentUser.id());
    }

    @GetMapping("/topics/{id}")
    public TopicResponse get(@PathVariable Long id) {
        return topicService.get(currentUser.id(), id);
    }

    @PutMapping("/topics/{id}")
    public TopicResponse update(@PathVariable Long id, @Valid @RequestBody TopicRequest request) {
        return topicService.update(currentUser.id(), id, request);
    }

    @PatchMapping("/topics/{id}/status")
    public TopicResponse updateStatus(@PathVariable Long id, @Valid @RequestBody TopicStatusRequest request) {
        return topicService.updateStatus(currentUser.id(), id, request.status());
    }

    @DeleteMapping("/topics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        topicService.delete(currentUser.id(), id);
    }
}
