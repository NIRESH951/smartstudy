package com.smartstudy.planner;

/**
 * Response of complete / miss: the updated session plus, when the work was rescheduled,
 * the new session that took it over.
 *
 * <pre>
 * { "session": { ...status "MISSED"... },
 *   "rescheduledTo": { ...new PLANNED session... },
 *   "message": "Session marked as missed and rescheduled to Tue 19:00." }
 * </pre>
 */
public record SessionActionResponse(SessionResponse session, SessionResponse rescheduledTo, String message) {
}
