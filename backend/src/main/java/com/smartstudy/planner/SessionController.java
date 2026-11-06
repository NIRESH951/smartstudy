package com.smartstudy.planner;

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
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final CurrentUser currentUser;

    public SessionController(SessionService sessionService, CurrentUser currentUser) {
        this.sessionService = sessionService;
        this.currentUser = currentUser;
    }

    @GetMapping("/{id}")
    public SessionResponse get(@PathVariable Long id) {
        return sessionService.get(currentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(@Valid @RequestBody CreateSessionRequest request) {
        return sessionService.create(currentUser.id(), request);
    }

    @PutMapping("/{id}")
    public SessionResponse update(@PathVariable Long id, @Valid @RequestBody UpdateSessionRequest request) {
        return sessionService.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        sessionService.cancel(currentUser.id(), id);
    }

    @PatchMapping("/{id}/lock")
    public SessionResponse lock(@PathVariable Long id, @Valid @RequestBody LockSessionRequest request) {
        return sessionService.setLocked(currentUser.id(), id, request.locked());
    }

    @PostMapping("/{id}/start")
    public SessionResponse start(@PathVariable Long id) {
        return sessionService.start(currentUser.id(), id);
    }

    @PostMapping("/{id}/complete")
    public SessionActionResponse complete(@PathVariable Long id,
                                          @Valid @RequestBody(required = false) CompleteSessionRequest request) {
        CompleteSessionRequest body = request == null ? new CompleteSessionRequest(null, null, null, null) : request;
        return sessionService.complete(currentUser.id(), id, body);
    }

    @PostMapping("/{id}/miss")
    public SessionActionResponse miss(@PathVariable Long id) {
        return sessionService.miss(currentUser.id(), id);
    }
}
