# SmartStudy – Testing & Verification

## 1. How to run the tests on your machine

| What | Command (run from the folder shown) | Where |
|---|---|---|
| All backend tests (unit + Mockito + Spring Boot integration on H2) | `.\mvnw.cmd test` (Windows) / `./mvnw test` | `backend/` – IntelliJ Terminal or PowerShell |
| One backend test class | `.\mvnw.cmd test -Dtest=TimetableGeneratorTest` | `backend/` |
| Frontend unit tests | `npm test` | `frontend/` |
| Frontend type check | `npm run typecheck` | `frontend/` |
| Frontend production build | `npm run build` | `frontend/` |

In IntelliJ you can also right-click `backend/src/test/java` → **Run 'All Tests'**.
The backend tests use an in-memory H2 database in MySQL mode (profile `test`) – no MySQL is
needed to run them.

## 2. Test inventory

### Backend (JUnit 5 + AssertJ + Mockito + Spring Boot Test)

| Test class | Kind | What it proves |
|---|---|---|
| `PriorityCalculatorTest` (10) | unit | Worked example = 75.0, normalisation 0–100, unassessed topic = neutral weakness (not 0 %), no exam / nothing due, configurable weights, reasons ordered by contribution |
| `TimetableGeneratorTest` (12) | unit | No overlaps, stays inside availability windows, kept (completed/locked) sessions never double-booked, daily and weekly limits, priority order and decay, revision not before its due date, study ends before the exam, nothing in the past, max 2 sessions per topic per day, unscheduled work reported, short revisions free the rest of a slot |
| `TimeIntervalTest` (6) | unit | Conflict detection: partial, contained and identical overlaps conflict; touching and separate intervals do not |
| `MasteryCalculatorTest` (8) | unit | Mastery = 0.6 latest + 0.4 average, level boundaries (parameterised), unassessed has no mastery (≠ 0), clamping, trend detection |
| `SpacedRepetitionSchedulerTest` (10) | unit | Documented Good/Good/Good/Easy sequence, Again restarts, Hard grows slowly, Easy jumps ahead, EF minimum, 1-year cap, success never shortens, late reviews, calendar boundaries, explanation text |
| `ReadinessCalculatorTest` (6) | unit | Full formula, missing component re-normalised (PARTIAL), only syllabus data = INSUFFICIENT, no data = no score, readiness levels |
| `FocusDurationCalculatorTest` (8) | unit | Server-side elapsed time, pauses excluded, paused session does not accumulate, cap at planned length, client cannot inflate but may lower, never negative |
| `TimeUtilsTest` (4) | unit | "Today" in the student's zone, local time → UTC, daylight-saving gap, unknown zones |
| `SubjectServiceTest` (4) | Mockito | Subject owned by the current student, default colour, duplicate names rejected, another student's subject behaves as non-existent |
| `TopicServiceTest` (4) | Mockito | Topic created under the student's subject, duplicate names, cannot add to another student's subject, completing a topic starts its revision cycle |
| `AuthServiceTest` (6) | Mockito | Registration hashes the password and starts a session, duplicate email, unknown time zone, login via AuthenticationManager, wrong password creates no session, principal erases the hash |
| `SessionServiceTest` (9) | Mockito | Overlapping manual session rejected (conflict detection), back-to-back accepted and locked, cannot claim more minutes than elapsed, cannot complete before start, ≥ 80 % completes, incomplete is rescheduled, missing triggers rescheduling, completed sessions cannot be missed, times in the student's zone |
| `SmartStudyApiIntegrationTest` (10) | Spring Boot + MockMvc + H2 | Full HTTP flow with real security: register → me → logout → login with real CSRF tokens; wrong password; validation; 401 without session; 403 without CSRF; subject/topic creation; **data isolation between two students** (read/update/delete another student's IDs → 404); plan generation without overlaps; missed session rescheduled automatically; completion → SM-2 revision → weak topics → focus 25 min → readiness and analytics |

Requirement coverage: authentication ✔ · subject/topic creation ✔ · priority calculation ✔ ·
timetable generation ✔ · conflict detection ✔ · missed-session rescheduling ✔ · weak-topic
detection ✔ · mastery calculation ✔ · revision scheduling ✔ · readiness calculation ✔ ·
focus-session validation ✔ · user data isolation ✔.

### Frontend (Vitest)

| Test file | What it proves |
|---|---|
| `src/utils/format.test.ts` | Duration, date, week and time-zone helpers |
| `src/utils/validation.test.ts` | Client-side rules mirror the server (password, ranges, required text) |
| `src/services/http.test.ts` | Cookies are sent, CSRF token fetched once and sent on writes, automatic refresh + single retry on `CSRF_INVALID`, error mapping with field errors, 401 handling, network-error message |

## 3. Verification performed while building this project

The project was built in a sandbox where **Maven Central (repo.maven.apache.org) was blocked**,
so Maven could not download dependencies. Everything that could be verified without Maven was
verified with the real tools; the remaining steps must be run once on your machine
(section 1 and the README's "Verify the installation").

| Check | Result |
|---|---|
| Backend compile check: all 176 Java files (main + tests) compiled with **javac 25** against the official sources of Spring Boot 4.1.2, Spring Framework 7.0.10, Spring Security 7.1.2, Spring Data JPA, Hibernate 7.4.13, Jackson 3, springdoc-openapi 3.2.0, JUnit 6, Mockito 5 | **0 errors** |
| Framework-free backend unit tests (8 classes) executed with JUnit 5 | **87 / 87 passed** |
| All 50 JPQL queries parsed with Hibernate 7.4.13's own HQL grammar (ANTLR) | **0 syntax errors** |
| Every path in every JPQL query resolved against the entity model; all derived query method names checked | **0 problems** |
| Every entity field mapped to an existing column of the Flyway schema (14 tables, all columns used) | **0 mismatches** |
| Spring bean graph: no constructor-injection cycles; every injected type is provided | **OK** |
| `application.yml` keys bind to `SmartStudyProperties` | **OK** |
| `V1__create_core_schema.sql`, `database/schema.sql` and `database/sample-data.sql` executed on a real **MySQL 8.0.46** server (8.4 packages were unavailable in the sandbox; the scripts use only syntax supported by both) | **OK**, sample data re-runnable |
| Frontend: `tsc --noEmit` (strict), `vite build`, `vitest run` | **0 errors**, build OK, **15 / 15 passed** |
| Every frontend API call matched against a backend endpoint (69/69); every TypeScript DTO and enum compared field-by-field with the Java records | **all match** |
| Postman collection: every request path matched against the backend | **OK** |
| Production build rendered in Chromium (Playwright) against a mocked API: all 15 pages incl. signed-out redirects and the 404 page | **0 runtime errors**, no console warnings |
| Interactive flows in the browser: add subject (validation + save), generate plan, complete a session with SM-2 rating, revise a topic, focus start → pause → finish → break, notifications | **10 / 10 OK**, every write carries the CSRF header and a payload matching the backend DTO |
| Database setup path as the application user: `create-database.sql` → Flyway V1 → `sample-data.sql` | **OK** (14 tables, demo student) |

**Not executed in the sandbox** (needs Maven dependencies – run `.\mvnw.cmd test` locally):
the Mockito service tests, the Spring Boot integration test, starting the application against
MySQL 8.4, and clicking through the UI against a running backend.
