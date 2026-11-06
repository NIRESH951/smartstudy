package com.smartstudy.focus;

import java.util.List;

import jakarta.validation.Valid;

import com.smartstudy.common.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/focus")
public class FocusController {

    private final FocusService focusService;
    private final CurrentUser currentUser;

    public FocusController(FocusService focusService, CurrentUser currentUser) {
        this.focusService = focusService;
        this.currentUser = currentUser;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public FocusSessionResponse start(@Valid @RequestBody(required = false) FocusStartRequest request) {
        return focusService.start(currentUser.id(),
                request == null ? new FocusStartRequest(null, null, null, null) : request);
    }

    @PostMapping("/{id}/pause")
    public FocusSessionResponse pause(@PathVariable Long id) {
        return focusService.pause(currentUser.id(), id);
    }

    @PostMapping("/{id}/resume")
    public FocusSessionResponse resume(@PathVariable Long id) {
        return focusService.resume(currentUser.id(), id);
    }

    @PostMapping("/{id}/complete")
    public FocusCompleteResponse complete(@PathVariable Long id,
                                          @Valid @RequestBody(required = false) FocusCompleteRequest request) {
        return focusService.complete(currentUser.id(), id, request);
    }

    @PostMapping("/{id}/cancel")
    public FocusSessionResponse cancel(@PathVariable Long id) {
        return focusService.cancel(currentUser.id(), id);
    }

    /** 200 with the running/paused session, or 204 when there is none. */
    @GetMapping("/active")
    public ResponseEntity<FocusSessionResponse> active() {
        return focusService.active(currentUser.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/history")
    public List<FocusSessionResponse> history(@RequestParam(defaultValue = "30") int days) {
        return focusService.history(currentUser.id(), days);
    }

    @GetMapping("/stats")
    public FocusStatsResponse stats() {
        return focusService.stats(currentUser.id());
    }
}
