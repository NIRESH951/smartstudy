package com.smartstudy.planner.engine;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** A weekly recurring study window in the student's local time. */
public record AvailabilityWindow(DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {
}
