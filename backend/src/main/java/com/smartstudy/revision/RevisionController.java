package com.smartstudy.revision;

import java.util.List;

import jakarta.validation.Valid;

import com.smartstudy.common.CurrentUser;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/revisions")
public class RevisionController {

    private final RevisionService revisionService;
    private final CurrentUser currentUser;

    public RevisionController(RevisionService revisionService, CurrentUser currentUser) {
        this.revisionService = revisionService;
        this.currentUser = currentUser;
    }

    @GetMapping("/due")
    public RevisionDueResponse due() {
        return revisionService.due(currentUser.id());
    }

    @PostMapping("/{topicId}/complete")
    public RevisionResultResponse complete(@PathVariable Long topicId,
                                           @Valid @RequestBody CompleteRevisionRequest request) {
        return revisionService.complete(currentUser.id(), topicId, request);
    }

    @GetMapping("/history")
    public List<RevisionLogResponse> history(@RequestParam(required = false) Long topicId,
                                             @RequestParam(defaultValue = "50") int limit) {
        return revisionService.history(currentUser.id(), topicId, limit);
    }
}
