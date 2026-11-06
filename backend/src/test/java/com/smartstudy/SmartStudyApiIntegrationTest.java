package com.smartstudy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;
import com.smartstudy.support.MutableClock;
import com.smartstudy.user.User;
import com.smartstudy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end API tests through the real security filter chain, controllers, services and JPA
 * (H2 in MySQL mode). Covers authentication, CSRF, subject/topic creation, student data
 * isolation, plan generation, conflict detection, missed-session rescheduling, revision,
 * weak-topic detection and focus timing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmartStudyApiIntegrationTest {

    /** Monday 06:00 UTC. */
    private static final Instant MONDAY_6AM = Instant.parse("2030-01-07T06:00:00Z");
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(MONDAY_6AM);
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MutableClock clock;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void resetClock() {
        clock.set(MONDAY_6AM);
    }

    // ------------------------------------------------------------------ authentication & security

    @Test
    @DisplayName("register -> me -> logout -> login, with real CSRF tokens and BCrypt hashing")
    void authenticationFlow() throws Exception {
        String email = uniqueEmail("auth");
        MockHttpSession session = new MockHttpSession();
        Csrf token = fetchCsrf(session);
        assertThat(token.header()).isEqualTo("X-CSRF-TOKEN");

        mvc.perform(post("/api/v1/auth/register").session(session).header(token.header(), token.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Asha Verma", email, "Asia/Kolkata")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Asha Verma"));

        User stored = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo("Study@2026");

        Csrf rotated = fetchCsrf(session);
        mvc.perform(post("/api/v1/auth/logout").session(session).header(rotated.header(), rotated.value()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());

        MockHttpSession second = new MockHttpSession();
        Csrf loginToken = fetchCsrf(second);
        mvc.perform(post("/api/v1/auth/login").session(second).header(loginToken.header(), loginToken.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Study@2026\"}".formatted(email.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/api/v1/auth/me").session(second)).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailAreRejected() throws Exception {
        String email = uniqueEmail("wrong");
        register(email, "UTC");
        for (String body : List.of(
                "{\"email\":\"%s\",\"password\":\"Wrong1234\"}".formatted(email),
                "{\"email\":\"nobody@test.dev\",\"password\":\"Wrong1234\"}")) {
            MockHttpSession session = new MockHttpSession();
            mvc.perform(post("/api/v1/auth/login").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Test
    void registrationValidatesInputAndRejectsDuplicates() throws Exception {
        String email = uniqueEmail("dup");
        register(email, "UTC");
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Again", email, "UTC")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"not-an-email\",\"password\":\"abc1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/subjects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database.status").value("UP"));
    }

    @Test
    void writesWithoutCsrfTokenAreForbidden() throws Exception {
        MockHttpSession session = register(uniqueEmail("csrf"), "UTC");
        mvc.perform(post("/api/v1/subjects").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(subjectJson("Physics")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    // ------------------------------------------------------------------ subjects & topics

    @Test
    void subjectAndTopicCreation() throws Exception {
        MockHttpSession session = register(uniqueEmail("subject"), "UTC");
        long subjectId = createSubject(session, "Physics");

        mvc.perform(post("/api/v1/subjects").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(subjectJson("physics")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/subjects").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"importance\":9,\"color\":\"green\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").isArray());

        long topicId = createTopic(session, subjectId, "Kinematics", 3);
        mvc.perform(get("/api/v1/subjects/{id}/topics", subjectId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(topicId))
                .andExpect(jsonPath("$[0].masteryLevel").value("NOT_ASSESSED"));
        mvc.perform(get("/api/v1/subjects").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topicCount").value(1))
                .andExpect(jsonPath("$[0].syllabusCompletion").value(0.0));
    }

    @Test
    @DisplayName("a student can never read or change another student's records by changing an id")
    void studentDataIsIsolated() throws Exception {
        MockHttpSession owner = register(uniqueEmail("owner"), "UTC");
        long subjectId = createSubject(owner, "Chemistry");
        long topicId = createTopic(owner, subjectId, "Organic", 3);
        long examId = createExam(owner, subjectId, "2030-01-20");

        MockHttpSession intruder = register(uniqueEmail("intruder"), "UTC");
        mvc.perform(get("/api/v1/subjects").session(intruder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/subjects/{id}", subjectId).session(intruder)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/subjects/{id}", subjectId).session(intruder).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(subjectJson("Stolen")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/subjects/{id}", subjectId).session(intruder).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/subjects/{id}/topics", subjectId).session(intruder))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/subjects/{id}/topics", subjectId).session(intruder).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(topicJson("Injected", 3)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/topics/{id}/status", topicId).session(intruder).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/exams/{id}", examId).session(intruder)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/exams/{id}/readiness", examId).session(intruder)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/performance/topics/{id}/history", topicId).session(intruder))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/focus/start").session(intruder).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"topicId\":%d}".formatted(topicId)))
                .andExpect(status().isNotFound());

        // The owner's data is untouched.
        mvc.perform(get("/api/v1/subjects/{id}", subjectId).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chemistry"));
        mvc.perform(get("/api/v1/topics/{id}", topicId).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"));
    }

    // ------------------------------------------------------------------ adaptive planner

    @Test
    @DisplayName("plan generation is conflict-free and a missed session is rescheduled automatically")
    void plannerGeneratesAndReschedules() throws Exception {
        MockHttpSession session = register(uniqueEmail("planner"), "UTC");
        setAvailability(session);
        long subjectId = createSubject(session, "Data Structures");
        long hardTopic = createTopic(session, subjectId, "AVL Trees", 5);
        createTopic(session, subjectId, "Arrays", 1);
        createExam(session, subjectId, "2030-01-17");

        MvcResult generated = mvc.perform(post("/api/v1/planner/generate").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").isNumber())
                .andReturn();
        String body = generated.getResponse().getContentAsString();
        List<Map<String, Object>> sessions = JsonPath.read(body, "$.sessions");
        assertThat(sessions).hasSizeGreaterThanOrEqualTo(4);
        assertNoOverlap(sessions);
        assertThat(sessions).allMatch(s -> !Instant.parse((String) s.get("startAt")).isBefore(MONDAY_6AM));

        Map<String, Object> first = sessions.getFirst();
        long firstId = ((Number) first.get("id")).longValue();
        assertThat(((Number) first.get("topicId")).longValue())
                .as("the hardest topic is planned first").isEqualTo(hardTopic);
        assertThat((String) first.get("reason")).isNotBlank();

        // A manual session on top of a planned one is rejected.
        mvc.perform(post("/api/v1/sessions").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":%d,\"date\":\"%s\",\"startTime\":\"%s\",\"durationMinutes\":30}"
                                .formatted(subjectId, first.get("localDate"),
                                        ((String) first.get("localStartTime")).substring(0, 5))))
                .andExpect(status().isConflict());

        // Missing the session moves its work to a new session of the same topic.
        mvc.perform(post("/api/v1/sessions/{id}/miss", firstId).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.status").value("MISSED"))
                .andExpect(jsonPath("$.rescheduledTo.topicId").value(hardTopic))
                .andExpect(jsonPath("$.rescheduledTo.status").value("PLANNED"))
                .andExpect(jsonPath("$.rescheduledTo.rescheduledFromId").value(firstId));

        mvc.perform(get("/api/v1/planner/today").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionsMissed").value(1))
                .andExpect(jsonPath("$.recommendations.length()").value(2));
        mvc.perform(get("/api/v1/analytics/overview").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today.sessionsMissed").value(1));
    }

    @Test
    @DisplayName("completing work feeds revision scheduling, weak-topic detection, focus timing and readiness")
    void progressFlow() throws Exception {
        MockHttpSession session = register(uniqueEmail("progress"), "UTC");
        setAvailability(session);
        long subjectId = createSubject(session, "Operating Systems");
        long topicA = createTopic(session, subjectId, "Deadlocks", 4);
        long topicB = createTopic(session, subjectId, "Paging", 3);
        long examId = createExam(session, subjectId, "2030-01-20");

        String plan = mvc.perform(post("/api/v1/planner/generate").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":2}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> sessions = JsonPath.read(plan, "$.sessions");
        Map<String, Object> firstA = sessions.stream()
                .filter(s -> ((Number) s.get("topicId")).longValue() == topicA)
                .findFirst().orElseThrow();
        long sessionId = ((Number) firstA.get("id")).longValue();
        Instant start = Instant.parse((String) firstA.get("startAt"));

        // Complete the session 55 minutes after it started and mark the topic as finished.
        clock.set(start.plus(Duration.ofMinutes(55)));
        mvc.perform(post("/api/v1/sessions/{id}/complete", sessionId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualMinutes\":50,\"topicCompleted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.status").value("COMPLETED"))
                .andExpect(jsonPath("$.session.actualMinutes").value(50));
        mvc.perform(get("/api/v1/topics/{id}", topicA).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.nextRevisionDate").value("2030-01-08"));

        // Next day the topic is due for revision; rating GOOD schedules the next one tomorrow.
        clock.set(Instant.parse("2030-01-08T09:00:00Z"));
        mvc.perform(get("/api/v1/revisions/due").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueToday[0].topicId").value(topicA));
        mvc.perform(post("/api/v1/revisions/{id}/complete", topicA).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\",\"durationMinutes\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newIntervalDays").value(1))
                .andExpect(jsonPath("$.nextRevisionDate").value("2030-01-09"));
        mvc.perform(get("/api/v1/revisions/history").param("topicId", String.valueOf(topicA)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // A weak quiz score on topic B marks it as weak; topic A stays "not assessed" (not weak).
        mvc.perform(post("/api/v1/assessments").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"subjectId\":%d,\"title\":\"Quiz 1\",\"assessmentType\":\"QUIZ\","
                                + "\"assessedOn\":\"2030-01-08\",\"maxMarks\":20,\"obtainedMarks\":6,"
                                + "\"topicScores\":[{\"topicId\":%d,\"maxMarks\":20,\"obtainedMarks\":6}]}")
                                .formatted(subjectId, topicB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scorePercent").value(30.0));
        mvc.perform(get("/api/v1/performance/weak-topics").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].topicId").value(topicB))
                .andExpect(jsonPath("$.items[0].masteryLevel").value("WEAK"))
                .andExpect(jsonPath("$.items[0].masteryScore").value(30.0))
                .andExpect(jsonPath("$.notAssessedTopics").value(1));

        // Focus: 35 minutes wall time with a 5 minute pause, planned 25 minutes, client claims more.
        String focus = mvc.perform(post("/api/v1/focus/start").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":%d,\"plannedMinutes\":25}".formatted(topicB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andReturn().getResponse().getContentAsString();
        long focusId = ((Number) JsonPath.read(focus, "$.id")).longValue();
        clock.advance(Duration.ofMinutes(10));
        mvc.perform(post("/api/v1/focus/{id}/pause", focusId).session(session).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED"));
        clock.advance(Duration.ofMinutes(5));
        mvc.perform(post("/api/v1/focus/{id}/resume", focusId).session(session).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPausedSeconds").value(300));
        clock.advance(Duration.ofMinutes(20));
        mvc.perform(post("/api/v1/focus/{id}/complete", focusId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"clientReportedSeconds\":9999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.status").value("COMPLETED"))
                .andExpect(jsonPath("$.session.actualFocusSeconds").value(1500))
                .andExpect(jsonPath("$.completedToday").value(1))
                .andExpect(jsonPath("$.nextBreak").value("SHORT"));

        mvc.perform(get("/api/v1/exams/{id}/readiness", examId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.length()").value(4))
                .andExpect(jsonPath("$.readinessScore").isNumber())
                .andExpect(jsonPath("$.disclaimer").isNotEmpty());
        mvc.perform(get("/api/v1/analytics/productivity").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analytics/weekly").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analytics/subjects").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].completedTopics").value(1));
    }

    // ------------------------------------------------------------------ helpers

    private record Csrf(String header, String value) {
    }

    private Csrf fetchCsrf(MockHttpSession session) throws Exception {
        String body = mvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Csrf(JsonPath.read(body, "$.headerName"), JsonPath.read(body, "$.token"));
    }

    private MockHttpSession register(String email, String timeZone) throws Exception {
        MockHttpSession session = new MockHttpSession();
        Csrf token = fetchCsrf(session);
        mvc.perform(post("/api/v1/auth/register").session(session).header(token.header(), token.value())
                        .contentType(MediaType.APPLICATION_JSON).content(registerJson("Student", email, timeZone)))
                .andExpect(status().isCreated());
        return session;
    }

    private void setAvailability(MockHttpSession session) throws Exception {
        StringBuilder windows = new StringBuilder();
        for (int day = 1; day <= 7; day++) {
            if (day > 1) {
                windows.append(',');
            }
            windows.append("{\"dayOfWeek\":%d,\"startTime\":\"08:00\",\"endTime\":\"20:00\"}".formatted(day));
        }
        mvc.perform(put("/api/v1/availability").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"dailyStudyMinutes\":180,\"weeklyStudyMinutes\":900,"
                                + "\"preferredSessionMinutes\":50,\"breakMinutes\":10,\"windows\":[%s]}")
                                .formatted(windows)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windows.length()").value(7));
    }

    private long createSubject(MockHttpSession session, String name) throws Exception {
        String body = mvc.perform(post("/api/v1/subjects").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(subjectJson(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createTopic(MockHttpSession session, long subjectId, String name, int difficulty) throws Exception {
        String body = mvc.perform(post("/api/v1/subjects/{id}/topics", subjectId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(topicJson(name, difficulty)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createExam(MockHttpSession session, long subjectId, String date) throws Exception {
        String body = mvc.perform(post("/api/v1/exams").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":%d,\"title\":\"Final exam\",\"examType\":\"FINAL\",\"examDate\":\"%s\"}"
                                .formatted(subjectId, date)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String registerJson(String name, String email, String timeZone) {
        return "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"Study@2026\",\"timeZone\":\"%s\"}"
                .formatted(name, email, timeZone);
    }

    private static String subjectJson(String name) {
        return "{\"name\":\"%s\",\"code\":\"S1\",\"color\":\"#3B82F6\",\"importance\":4}".formatted(name);
    }

    private static String topicJson(String name, int difficulty) {
        return "{\"name\":\"%s\",\"difficulty\":%d,\"estimatedMinutes\":120}".formatted(name, difficulty);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + COUNTER.incrementAndGet() + "@test.dev";
    }

    private static void assertNoOverlap(List<Map<String, Object>> sessions) {
        for (int i = 0; i < sessions.size(); i++) {
            for (int j = i + 1; j < sessions.size(); j++) {
                Instant aStart = Instant.parse((String) sessions.get(i).get("startAt"));
                Instant aEnd = Instant.parse((String) sessions.get(i).get("endAt"));
                Instant bStart = Instant.parse((String) sessions.get(j).get("startAt"));
                Instant bEnd = Instant.parse((String) sessions.get(j).get("endAt"));
                assertThat(aStart.isBefore(bEnd) && bStart.isBefore(aEnd))
                        .as("sessions %s and %s overlap", sessions.get(i).get("id"), sessions.get(j).get("id"))
                        .isFalse();
            }
        }
    }
}
