package com.smartstudy.revision;

/** Self-assessment the student gives after revising a topic. */
public enum RevisionRating {
    /** Could not recall the topic - start the cycle again. */
    AGAIN,
    /** Recalled with serious difficulty. */
    HARD,
    /** Recalled correctly with some effort. */
    GOOD,
    /** Recalled effortlessly. */
    EASY
}
