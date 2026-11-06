package com.smartstudy.focus;

/**
 * Result of completing a focus block. The server decides the next break: after every
 * {@code longBreakInterval}-th completed focus session of the day the break is LONG.
 *
 * <pre>
 * { "session": { ... "status": "COMPLETED", "actualFocusSeconds": 1500 ... },
 *   "completedToday": 4, "nextBreak": "LONG", "breakMinutes": 15 }
 * </pre>
 */
public record FocusCompleteResponse(FocusSessionResponse session, int completedToday, String nextBreak,
                                    int breakMinutes) {
}
