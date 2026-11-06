package com.smartstudy.user;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.smartstudy.common.exception.BadRequestException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Weekly availability windows and study-time limits used by the planner. */
@Service
public class AvailabilityService {

    private static final int MIN_WINDOW_MINUTES = 15;

    private final WeeklyAvailabilityRepository availabilityRepository;
    private final UserService userService;

    public AvailabilityService(WeeklyAvailabilityRepository availabilityRepository, UserService userService) {
        this.availabilityRepository = availabilityRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse get(Long userId) {
        User user = userService.require(userId);
        return toResponse(user, availabilityRepository.findByUserIdOrderByDayOfWeekAscStartTimeAsc(userId));
    }

    @Transactional(readOnly = true)
    public List<WeeklyAvailability> windows(Long userId) {
        return availabilityRepository.findByUserIdOrderByDayOfWeekAscStartTimeAsc(userId);
    }

    /** Validates and replaces all windows. Windows on the same day must not overlap. */
    @Transactional
    public AvailabilityResponse replace(Long userId, AvailabilityRequest request) {
        User user = userService.require(userId);
        if (request.weeklyStudyMinutes() > 0 && request.dailyStudyMinutes() > request.weeklyStudyMinutes()) {
            throw new BadRequestException("dailyStudyMinutes", "Daily study time cannot exceed weekly study time.");
        }
        List<AvailabilityRequest.Window> sorted = new ArrayList<>(request.windows());
        sorted.sort(Comparator.comparingInt(AvailabilityRequest.Window::dayOfWeek)
                .thenComparing(AvailabilityRequest.Window::startTime));
        for (int i = 0; i < sorted.size(); i++) {
            AvailabilityRequest.Window window = sorted.get(i);
            if (!window.endTime().isAfter(window.startTime())) {
                throw new BadRequestException("windows", "Each window must end after it starts (overnight windows "
                        + "must be split at midnight).");
            }
            if (Duration.between(window.startTime(), window.endTime()).toMinutes() < MIN_WINDOW_MINUTES) {
                throw new BadRequestException("windows", "Each window must be at least " + MIN_WINDOW_MINUTES
                        + " minutes long.");
            }
            if (i > 0) {
                AvailabilityRequest.Window previous = sorted.get(i - 1);
                if (previous.dayOfWeek() == window.dayOfWeek() && window.startTime().isBefore(previous.endTime())) {
                    throw new BadRequestException("windows", "Availability windows on the same day must not overlap.");
                }
            }
        }

        user.setDailyStudyMinutes(request.dailyStudyMinutes());
        user.setWeeklyStudyMinutes(request.weeklyStudyMinutes());
        user.setPreferredSessionMinutes(request.preferredSessionMinutes());
        user.setBreakMinutes(request.breakMinutes());

        availabilityRepository.deleteAllForUser(userId);
        List<WeeklyAvailability> saved = availabilityRepository.saveAll(sorted.stream()
                .map(w -> new WeeklyAvailability(userId, w.dayOfWeek(), w.startTime(), w.endTime()))
                .toList());
        return toResponse(user, saved);
    }

    private AvailabilityResponse toResponse(User user, List<WeeklyAvailability> windows) {
        List<AvailabilityResponse.WindowResponse> items = windows.stream()
                .map(w -> new AvailabilityResponse.WindowResponse(w.getId(), w.getDayOfWeek(), w.getStartTime(),
                        w.getEndTime(), (int) Duration.between(w.getStartTime(), w.getEndTime()).toMinutes()))
                .toList();
        int total = items.stream().mapToInt(AvailabilityResponse.WindowResponse::minutes).sum();
        return new AvailabilityResponse(user.getDailyStudyMinutes(), user.getWeeklyStudyMinutes(),
                user.getPreferredSessionMinutes(), user.getBreakMinutes(), total, items);
    }
}
