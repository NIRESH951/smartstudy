package com.smartstudy.user;

import jakarta.validation.Valid;

import com.smartstudy.common.CurrentUser;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final CurrentUser currentUser;

    public AvailabilityController(AvailabilityService availabilityService, CurrentUser currentUser) {
        this.availabilityService = availabilityService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public AvailabilityResponse get() {
        return availabilityService.get(currentUser.id());
    }

    @PutMapping
    public AvailabilityResponse replace(@Valid @RequestBody AvailabilityRequest request) {
        return availabilityService.replace(currentUser.id(), request);
    }
}
