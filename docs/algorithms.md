# SmartStudy – Algorithms

All formulas are implemented in small, framework-free classes so they can be unit-tested in
isolation. Every number shown to the student comes from these classes – there are no hard-coded
or random values in the UI.

| Module | Class | Unit tests |
|---|---|---|
| Weighted priority | `planner/engine/PriorityCalculator` | `PriorityCalculatorTest` |
| Timetable generation | `planner/engine/TimetableGenerator` | `TimetableGeneratorTest`, `TimeIntervalTest` |
| Plan orchestration, missed/incomplete handling | `planner/PlannerService`, `planner/SessionService` | `SessionServiceTest`, `SmartStudyApiIntegrationTest` |
| Topic mastery & weak topics | `performance/MasteryCalculator` | `MasteryCalculatorTest` |
| Spaced repetition (SM-2) | `revision/SpacedRepetitionScheduler` | `SpacedRepetitionSchedulerTest` |
| Exam readiness | `readiness/ReadinessCalculator`, `readiness/ReadinessService` | `ReadinessCalculatorTest` |
| Focus timing | `focus/FocusDurationCalculator` | `FocusDurationCalculatorTest` |

---

## 1. Adaptive Study Planning Engine

### 1.1 Weighted priority

```
Priority = 0.30 × W + 0.25 × U + 0.20 × D + 0.15 × R + 0.10 × I
```

Every factor is normalised to **0–100** first:

| Factor | Normalisation |
|---|---|
| **W** – Weakness | `100 − mastery`. A topic that has **never been assessed gets a neutral 50** (configurable `unassessed-weakness`), because "no data" is not "0 % mastery". |
| **U** – Exam urgency | `100 × (H − d) / H` for `d` days to the nearest exam of the subject, horizon `H = 30`: 100 on exam day, 50 at 15 days, 0 at ≥ 30 days or with no exam. |
| **D** – Difficulty | `(difficulty − 1) × 25` → 1..5 maps to 0, 25, 50, 75, 100. |
| **R** – Revision overdue | 0 if nothing is due; `20 + 80 × min(overdueDays, 14) / 14` when a revision is due (20 on the due day, 100 at 14+ days overdue). |
| **I** – Subject importance | `(importance − 1) × 25`. |

The weights live in `application.yml` (`smartstudy.planner.weights.*`). The weighted sum is
divided by the sum of the weights, so the result stays on the 0–100 scale even if edited weights
do not add up to exactly 1.

**Worked example** – mastery 30 % (W = 70), exam in 6 days (U = 80), difficulty 4 (D = 75),
revision 7 days overdue (R = 60), importance 5 (I = 100):
`0.30·70 + 0.25·80 + 0.20·75 + 0.15·60 + 0.10·100 = 21 + 20 + 15 + 9 + 10 = 75.0`.

The API returns the factor break-down and plain-language reasons ("Weak topic (mastery 30%)",
"Exam in 6 days", …), which the Study Planner shows next to every recommendation and session.

### 1.2 What gets planned (tasks)

For every topic of every non-archived subject:

* **STUDY** – if the topic is not completed: `remaining = estimatedMinutes − minutes actually studied − minutes reserved by sessions that are kept`. Remaining work below 10 minutes is ignored. Missed or incomplete work therefore flows back into the plan automatically.
* **REVISION** – if the topic's SM-2 `nextRevisionDate` falls inside the planning period (earliest date = due date).
* **PRACTICE** – a completed topic whose mastery is still **WEAK (< 40)** and that has no revision due gets an extra practice block.

Tasks of a subject with an exam get a **deadline** (the exam start) – nothing is scheduled after it.

### 1.3 Timetable generation (greedy, constraint-satisfying, deterministic)

1. **Free slots.** Each availability window of each day is converted from the student's local time
   to UTC (DST-safe), trimmed so nothing starts in the past (5-minute lead, 5-minute grid), and the
   time of every **kept session** (completed, in-progress and locked sessions – each padded by the
   break length) is subtracted. What remains is cut into slots of the preferred session
   length separated by the configured break; slots shorter than 25 minutes are dropped.
   Because occupied time is removed first, **generated sessions can never overlap existing ones**.
2. **Assignment.** Slots are processed chronologically. Each eligible task gets
   `score = priority × 0.80^(sessions already given to it)`, × 0.90 if it has the same subject as
   the previous slot that day (interleaving). The highest score wins the slot. A task is eligible
   when it still has minutes left, the slot is on/after its earliest date, it ends before the
   exam deadline, and the topic has fewer than 2 sessions that day.
3. **Limits.** The daily and weekly (Monday–Sunday) study limits – including minutes already used
   by preserved sessions – are never exceeded.
4. **Re-use.** A short task (e.g. a 30-minute revision) frees the rest of its slot for another task.
5. **Transparency.** Work that does not fit is returned as *unscheduled* with the reason
   (e.g. "Not enough free time before the exam…"), plus warnings such as "You have no weekly
   availability yet".

### 1.4 Adaptivity: missed and incomplete sessions

| Event | Rule | Result |
|---|---|---|
| **Missed** | A `PLANNED` session whose end time is more than 30 minutes (`missed-grace-minutes`) in the past, detected by the background job (every 15 min), when the dashboard loads and before every (re)generation. The student can also press *Mark as missed*. | Status `MISSED`; the plan is regenerated from *now*; the replacement session is linked through `rescheduled_from_id`; a notification says where the work moved to. |
| **Incomplete** | On *Complete*, actual minutes < 80 % of planned. | Status `INCOMPLETE`; the remaining minutes return to the topic and are rescheduled the same way. |
| **Completed** | Actual minutes ≥ 80 % of planned. | Status `COMPLETED`; minutes count as studied. Optionally marks the topic completed, which starts its revision cycle. |

**Never moved by regeneration:** completed, incomplete, in-progress and missed sessions (history)
and locked sessions. Sessions the student creates or moves by hand are locked automatically (they
can be unlocked in the planner). Only `PLANNED`, unlocked, future sessions are replaced. Manual create/move requests are rejected with **409 CONFLICT** if they overlap
another active session.

---

## 2. Weak Topic Detection

```
Mastery = 0.60 × LatestAssessmentScore + 0.40 × HistoricalAverage
```

* *Latest* = percentage of the most recent topic score; *historical average* = mean of **all**
  scores of the topic (including the latest).
* Example: scores 40, 60, 80 → `0.6·80 + 0.4·60 = 72` → GOOD.
* Levels: **WEAK 0–39 · NEEDS_IMPROVEMENT 40–64 · GOOD 65–79 · STRONG 80–100**.
* A topic with no scores is **NOT_ASSESSED** – it has no mastery row and is never counted as 0 %.
* Trend: IMPROVING / DECLINING / STABLE by comparing the new mastery with the previous one
  (± 2 points tolerance); NEW for the first assessment.
* Mastery is recalculated whenever an assessment with topic scores is recorded or deleted.

---

## 3. Spaced Repetition (simplified SM-2)

Each topic keeps an ease factor EF (1.30–5.00, starts at 2.50), an interval I (days) and the
number of successful repetitions n. A completed topic gets its first review the next day.

| Rating | EF change | New n | New interval |
|---|---|---|---|
| **Again** | −0.20 | 0 | 1 day (cycle restarts) |
| **Hard** | −0.15 | n+1 | n = 0: 1 day, otherwise `max(1, round(I × 1.2))` |
| **Good** | ±0 | n+1 | n = 0: 1, n = 1: 6, otherwise `round(I × EF′)` |
| **Easy** | +0.15 | n+1 | n = 0: 4, n = 1: 8, otherwise `round(I × EF′ × 1.3)` |

EF′ is clamped to [1.30, 5.00]; intervals to [1, 365]; Good/Easy always grow the interval by at
least one day. The next date is `review date + interval`, where the review date is the student's
**local** calendar date (not UTC), so a late review is scheduled from the day it really happened.
Example: Good, Good, Good, Easy on time → 1, 6, 15, 52 days.

Every review is stored in `revision_logs` with EF and interval before/after, so the student sees
*why* the next date was chosen.

---

## 4. Exam Readiness

```
Readiness = 0.30 × SyllabusCompletion + 0.30 × TopicMastery
          + 0.25 × MockTestPerformance + 0.15 × RevisionCompletion
```

| Component | Definition | Unavailable when |
|---|---|---|
| Syllabus completion | completed topics ÷ all topics of the subject | the subject has no topics |
| Topic mastery | average mastery of the **assessed** topics | no topic is assessed |
| Mock test performance | average of the latest 3 `MOCK_TEST` assessments | no mock test recorded |
| Revision completion | completed topics revised at least once and not overdue ÷ completed topics | no topic completed yet |

**Insufficient data handling.** Missing components are **not** counted as 0. The score is computed
from the available components with re-normalised weights and carries a *data confidence*
(`100 × Σ weights available`):

* **SUFFICIENT** – all four components available
* **PARTIAL** – confidence ≥ 55 %
* **INSUFFICIENT** – confidence < 55 % (score shown but clearly marked as unreliable)

Example: syllabus 80, mastery 70, no mock tests, revision 50 →
`(0.30·80 + 0.30·70 + 0.15·50) / 0.75 = 70.0`, confidence 75 % (PARTIAL).

Levels: **WELL_PREPARED ≥ 80 · ON_TRACK 60–79 · NEEDS_WORK 40–59 · AT_RISK < 40**.
One snapshot per exam per day (`readiness_snapshots`) feeds the trend chart. Every response
includes the disclaimer that the score is an estimate of preparation, **not a prediction of the
actual exam result**, plus concrete focus areas (weak topics, pending topics, missing mock tests,
overdue revisions).

---

## 5. Focus Mode (Pomodoro)

* Default 25 minutes focus, 5 minutes short break, 15 minutes long break after every 4 completed
  focus sessions (all configurable per student in Settings).
* Timing is **server-validated**:

```
elapsedFocus = (effectiveEnd − startedAt) − totalPausedSeconds
               effectiveEnd = pausedAt while paused, otherwise now
actualFocus  = min(elapsedFocus, plannedMinutes × 60, clientReportedSeconds if supplied)
```

  The browser's count can only *lower* the recorded time, never raise it.
* One running/paused session per student; a session left running more than 10 minutes past its
  planned end is completed automatically with the capped duration.
* Focus sessions can be linked to a study session; their time is used as the default actual
  duration when that session is completed. Focus minutes appear in the dashboard and analytics.
