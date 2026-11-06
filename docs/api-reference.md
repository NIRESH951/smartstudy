# SmartStudy – REST API Reference (v1)

* Base URL (local): `http://localhost:8080/api/v1`
* Interactive documentation: **Swagger UI** at `http://localhost:8080/swagger-ui.html`
  (OpenAPI JSON at `/v3/api-docs`; disabled in the `prod` profile unless `API_DOCS_ENABLED=true`).
* Postman: import `postman/SmartStudy.postman_collection.json` and
  `postman/SmartStudy-local.postman_environment.json`.
* JSON everywhere. Dates `YYYY-MM-DD`, times `HH:mm`, instants ISO-8601 UTC (`2026-10-31T06:00:00Z`).
* Requests and responses are DTO records – JPA entities are never exposed.

## 1. Authentication, session and CSRF

SmartStudy uses a **server-side session** (cookie `SMARTSTUDY_SESSION`, `HttpOnly`,
`SameSite=Lax`, `Secure` in production) – no tokens are stored in the browser.

1. `GET /auth/csrf` → `{ "headerName": "X-CSRF-TOKEN", "parameterName": "_csrf", "token": "…" }`
2. Send that token in the `X-CSRF-TOKEN` header on every `POST`, `PUT`, `PATCH` and `DELETE`
   (including login and register).
3. `POST /auth/login` (or `/auth/register`) sets the session cookie. The session id **and** the
   CSRF token are rotated after login – fetch a new token (step 1) afterwards.
4. `POST /auth/logout` → `204`, session invalidated, cookie deleted.

Public endpoints: `GET /health`, `GET /auth/csrf`, `POST /auth/register`, `POST /auth/login`
(and Swagger UI in development). Everything else requires a signed-in student.

**Data isolation.** Every query is scoped to the signed-in student. Requesting another
student's resource by ID returns **404 NOT_FOUND** (indistinguishable from a non-existent ID).

### Example (curl, Linux/macOS/Git Bash)

```bash
B=http://localhost:8080/api/v1
TOKEN=$(curl -s -c jar.txt -b jar.txt $B/auth/csrf | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -s -c jar.txt -b jar.txt -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $TOKEN" \
     -d '{"email":"demo@smartstudy.dev","password":"Demo@12345"}' $B/auth/login
curl -s -b jar.txt $B/planner/today
```

### Example (PowerShell)

```powershell
$B = "http://localhost:8080/api/v1"
$csrf = Invoke-RestMethod "$B/auth/csrf" -SessionVariable s
Invoke-RestMethod "$B/auth/login" -Method Post -WebSession $s -ContentType "application/json" `
  -Headers @{ "X-CSRF-TOKEN" = $csrf.token } `
  -Body '{"email":"demo@smartstudy.dev","password":"Demo@12345"}'
Invoke-RestMethod "$B/planner/today" -WebSession $s
```

## 2. Error format

Every error has the same JSON shape:

```json
{
  "timestamp": "2026-10-31T05:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Please correct the highlighted fields.",
  "path": "/api/v1/subjects",
  "fieldErrors": [ { "field": "name", "message": "must not be blank" } ]
}
```

| HTTP | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED`, `BAD_REQUEST`, `MALFORMED_REQUEST` | Bean Validation / invalid values / unreadable JSON |
| 401 | `UNAUTHENTICATED` | Not signed in or session expired |
| 401 | `INVALID_CREDENTIALS` | Wrong email or password |
| 403 | `CSRF_INVALID` | Missing or stale CSRF token |
| 403 | `ACCESS_DENIED` | Not allowed |
| 404 | `NOT_FOUND` | Unknown ID **or another student's ID** |
| 405 | `METHOD_NOT_ALLOWED` | Wrong HTTP method |
| 409 | `CONFLICT` | Duplicate name/email, overlapping study session, concurrent update |
| 422 | `BUSINESS_RULE` | Valid request that breaks a rule (e.g. completing a session before it started) |
| 500 | `INTERNAL_ERROR` | Unexpected error (details only in the server log) |

## 3. Endpoints

### Health & auth
| Method | Path | Description |
|---|---|---|
| GET | `/health` | API + database status, MySQL version, Flyway schema version |
| GET | `/auth/csrf` | CSRF token for the current session |
| POST | `/auth/register` | Create account and sign in → `201` + profile |
| POST | `/auth/login` | Sign in → profile |
| GET | `/auth/me` | Current student |
| POST | `/auth/logout` | Sign out → `204` |

### Profile & availability
| Method | Path | Description |
|---|---|---|
| GET / PUT | `/users/me` | Read / update name, time zone, Pomodoro settings |
| PUT | `/users/me/password` | Change password (current password required) → `204` |
| GET / PUT | `/availability` | Study budgets + weekly windows (PUT replaces everything) |

### Subjects, topics, exams
| Method | Path | Description |
|---|---|---|
| GET / POST | `/subjects` | List (with progress, mastery, next exam) / create |
| GET / PUT / DELETE | `/subjects/{id}` | Read / update / delete (cascades) |
| GET / POST | `/subjects/{subjectId}/topics` | Topics of a subject / create topic |
| GET | `/topics` | All topics of the student |
| GET / PUT / DELETE | `/topics/{id}` | Read / update / delete |
| PATCH | `/topics/{id}/status` | `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED` (completing starts SM-2) |
| GET / POST | `/exams` | List / create |
| GET / PUT / DELETE | `/exams/{id}` | Read / update / delete |

### Assessments & weak topics
| Method | Path | Description |
|---|---|---|
| GET / POST | `/assessments[?subjectId=]` | List / record (with optional per-topic scores) |
| DELETE | `/assessments/{id}` | Delete and recalculate mastery |
| GET | `/performance/weak-topics` | Weak topics with mastery, trend and recommendation |
| GET | `/performance/summary` | Mastery level counts and per-subject averages |
| GET | `/performance/topics/{topicId}/history` | Score history of one topic |

### Planner & sessions
| Method | Path | Description |
|---|---|---|
| POST | `/planner/generate` | Generate a plan `{ "startDate": null, "days": 7 }` (both optional, 1–28 days) |
| POST | `/planner/regenerate` | Re-plan the active period from now |
| GET | `/planner/today` | Today's sessions, progress, top recommendations |
| GET | `/planner/weekly[?date=]` | Monday–Sunday week |
| GET | `/planner/calendar?from=&to=` | Any range up to 62 days (monthly view) |
| GET | `/planner/recommendations[?limit=5]` | What to study next and why |
| GET | `/planner/priorities` | Full priority ranking with factor break-down |
| GET | `/planner/active` | Active plan summary (`204` if none) |
| POST | `/sessions` | Create a manual session (locked by default; `409` on overlap) |
| GET / PUT / DELETE | `/sessions/{id}` | Read / move-edit (`409` on overlap) / cancel |
| PATCH | `/sessions/{id}/lock` | `{ "locked": true }` |
| POST | `/sessions/{id}/start` | Start (from 60 min before the planned start) |
| POST | `/sessions/{id}/complete` | Complete: ≥ 80 % → `COMPLETED`, else `INCOMPLETE` + reschedule |
| POST | `/sessions/{id}/miss` | Mark missed + reschedule |

### Revision, readiness, focus, analytics, notifications
| Method | Path | Description |
|---|---|---|
| GET | `/revisions/due` | Overdue, due today and next 7 days |
| POST | `/revisions/{topicId}/complete` | Record a review `{ "rating": "GOOD", … }` → SM-2 result |
| GET | `/revisions/history[?topicId=&limit=]` | Review log |
| GET | `/exams/{id}/readiness` | Readiness of one exam (stores today's snapshot) |
| GET | `/readiness` | Readiness of all upcoming exams |
| POST | `/focus/start` | Start a Pomodoro (one active per student) |
| POST | `/focus/{id}/pause` · `/resume` · `/complete` · `/cancel` | Timer control (server-timed) |
| GET | `/focus/active` | Running/paused session (`204` if none) |
| GET | `/focus/history[?days=30]` · `/focus/stats` | History and statistics |
| GET | `/analytics/overview` | Everything the dashboard needs |
| GET | `/analytics/weekly[?date=]` | Weekly report (by day and subject) |
| GET | `/analytics/subjects` | Per-subject progress |
| GET | `/analytics/productivity[?days=30]` | Trends, streaks, consistency, monthly, readiness trends |
| GET | `/notifications[?unreadOnly=&limit=]` | Notifications + unread count |
| POST | `/notifications/{id}/read` · `/notifications/read-all` | Mark read |

## 4. Request / response examples

**Register** – `POST /auth/register` → `201`
```json
{ "fullName": "Asha Verma", "email": "asha@example.com", "password": "Study@2026", "timeZone": "Asia/Kolkata" }
```
Password: 8–72 characters with at least one letter and one digit.

**Profile** (response of login / register / `GET /auth/me`)
```json
{ "id": 1, "fullName": "Demo Student", "email": "demo@smartstudy.dev", "role": "STUDENT",
  "timeZone": "Asia/Kolkata", "dailyStudyMinutes": 180, "weeklyStudyMinutes": 900,
  "preferredSessionMinutes": 50, "breakMinutes": 10, "focusMinutes": 25,
  "shortBreakMinutes": 5, "longBreakMinutes": 15, "longBreakInterval": 4,
  "createdAt": "2026-10-01T10:00:00Z" }
```

**Availability** – `PUT /availability` (dayOfWeek 1 = Monday … 7 = Sunday, local time)
```json
{ "dailyStudyMinutes": 180, "weeklyStudyMinutes": 900, "preferredSessionMinutes": 50, "breakMinutes": 10,
  "windows": [ { "dayOfWeek": 1, "startTime": "18:00", "endTime": "21:00" },
               { "dayOfWeek": 6, "startTime": "09:00", "endTime": "12:30" } ] }
```

**Subject** – `POST /subjects`
```json
{ "name": "Data Structures", "code": "CS201", "color": "#3B82F6", "importance": 5,
  "targetScore": 85, "description": "Trees, graphs, hashing", "archived": false }
```
Response adds `id`, `topicCount`, `completedTopicCount`, `syllabusCompletion`, `averageMastery`,
`assessedTopicCount`, `weakTopicCount`, `nextExamId`, `nextExamTitle`, `nextExamDate`,
`daysUntilNextExam`, `createdAt`.

**Topic** – `POST /subjects/3/topics`
```json
{ "name": "AVL Trees", "description": "Rotations and balancing", "difficulty": 4,
  "estimatedMinutes": 180, "status": "NOT_STARTED" }
```

**Exam** – `POST /exams`
```json
{ "subjectId": 3, "title": "End Semester Exam", "examType": "FINAL", "examDate": "2026-11-20",
  "startTime": "10:00", "durationMinutes": 180, "location": "Hall B", "targetScore": 85, "notes": null }
```
`examType`: `MIDTERM`, `FINAL`, `QUIZ`, `PRACTICAL`, `ENTRANCE`, `OTHER`.

**Assessment with topic scores** – `POST /assessments`
```json
{ "subjectId": 3, "examId": null, "title": "Unit Test 2", "assessmentType": "QUIZ",
  "assessedOn": "2026-10-28", "maxMarks": 50, "obtainedMarks": 31, "notes": null,
  "topicScores": [ { "topicId": 12, "maxMarks": 20, "obtainedMarks": 7 },
                   { "topicId": 13, "maxMarks": 30, "obtainedMarks": 24 } ] }
```
`assessmentType`: `EXAM`, `QUIZ`, `ASSIGNMENT`, `MOCK_TEST` (mock tests feed exam readiness).

**Generate plan** – `POST /planner/generate` → `PlanResponse`
```json
{ "planId": 5, "startDate": "2026-10-31", "endDate": "2026-11-06", "generatedAt": "2026-10-31T06:00:00Z",
  "totalPlannedMinutes": 840, "sessionCount": 16,
  "strategySummary": "16 sessions (14 h 0 min) for 9 topics. Focus: AVL Trees, Normalization, Deadlocks.",
  "sessions": [ { "id": 41, "planId": 5, "subjectId": 3, "subjectName": "Data Structures",
                  "subjectColor": "#3B82F6", "topicId": 12, "topicName": "AVL Trees",
                  "sessionType": "STUDY", "source": "GENERATED", "status": "PLANNED",
                  "startAt": "2026-11-02T12:30:00Z", "endAt": "2026-11-02T13:20:00Z",
                  "localDate": "2026-11-02", "localStartTime": "18:00:00", "localEndTime": "18:50:00",
                  "plannedMinutes": 50, "actualMinutes": null, "startedAt": null, "completedAt": null,
                  "locked": false, "priorityScore": 72.5,
                  "reason": "Weak topic (mastery 32%); Exam in 6 days; Hard topic (difficulty 4/5)",
                  "notes": null, "rescheduledFromId": null } ],
  "unscheduled": [ { "topicId": 20, "topicName": "Graph Coloring", "subjectName": "Discrete Maths",
                     "sessionType": "STUDY", "remainingMinutes": 90,
                     "reason": "Did not fit into this planning period; it will be planned in the next timetable." } ],
  "warnings": [] }
```

**Manual session** – `POST /sessions`
```json
{ "subjectId": 3, "topicId": 12, "sessionType": "STUDY", "date": "2026-11-02",
  "startTime": "07:00", "durationMinutes": 45, "notes": "Before college", "locked": true }
```
Overlap → `409 CONFLICT` "This time overlaps another study session on …".

**Complete session** – `POST /sessions/41/complete`
```json
{ "actualMinutes": 45, "topicCompleted": false, "revisionRating": null, "notes": "Did 10 problems" }
```
Response (`SessionActionResponse`): `{ "session": {…}, "rescheduledTo": {…} | null, "message": "…" }`.
REVISION sessions require `revisionRating` (`AGAIN`, `HARD`, `GOOD`, `EASY`).

**Revision** – `POST /revisions/12/complete`
```json
{ "rating": "GOOD", "durationMinutes": 25, "studySessionId": null, "notes": "Rotations are clear now" }
```
```json
{ "topicId": 12, "topicName": "AVL Trees", "rating": "GOOD", "revisedOn": "2026-10-31",
  "previousIntervalDays": 6, "newIntervalDays": 15, "previousEaseFactor": 2.5, "newEaseFactor": 2.5,
  "repetitions": 3, "nextRevisionDate": "2026-11-15",
  "explanation": "Rated GOOD: interval 6 -> 15 days, ease factor 2.50 -> 2.50. Next revision on 2026-11-15.",
  "completedSessionId": 44 }
```

**Readiness** – `GET /exams/7/readiness`
```json
{ "examId": 7, "examTitle": "End Semester Exam", "subjectName": "Data Structures",
  "examDate": "2026-11-20", "daysRemaining": 20,
  "readinessScore": 63.4, "level": "ON_TRACK", "dataConfidence": 100.0, "dataSufficiency": "SUFFICIENT",
  "components": [ { "key": "syllabusCompletion", "label": "Syllabus completion", "score": 50.0,
                    "weight": 0.30, "available": true, "detail": "3 of 6 topics completed" } ],
  "pendingTopics": [ … ], "focusAreas": [ … ], "history": [ { "date": "2026-10-31", "score": 63.4 } ],
  "calculatedOn": "2026-10-31",
  "disclaimer": "This is an estimate of your preparation based on your recorded progress, not a prediction or guarantee of your actual exam result." }
```

**Focus** – `POST /focus/start` `{ "subjectId": 3, "topicId": 12, "studySessionId": null, "plannedMinutes": 25 }`
→ session with `elapsedFocusSeconds`, `remainingSeconds`, `serverTime`.
`POST /focus/9/complete` `{ "clientReportedSeconds": 1500, "notes": null }` →
`{ "session": { "status": "COMPLETED", "actualFocusSeconds": 1500, … }, "completedToday": 4, "nextBreak": "LONG", "breakMinutes": 15 }`.

**Health** – `GET /health`
```json
{ "status": "UP", "application": "SmartStudy API", "version": "1.0.0", "timestamp": "2026-10-31T05:30:00Z",
  "database": { "status": "UP", "product": "MySQL", "productVersion": "8.4.6",
                "databaseName": "smartstudy", "schemaVersion": "1", "message": null } }
```
