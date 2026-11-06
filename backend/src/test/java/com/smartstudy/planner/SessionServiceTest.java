package com.smartstudy.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.smartstudy.common.exception.BusinessRuleException;
import com.smartstudy.common.exception.ConflictException;
import com.smartstudy.focus.FocusSessionRepository;
import com.smartstudy.revision.RevisionService;
import com.smartstudy.subject.Subject;
import com.smartstudy.subject.SubjectService;
import com.smartstudy.topic.TopicService;
import com.smartstudy.user.User;
import com.smartstudy.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final Long STUDENT = 1L;
    private static final Instant TEN = Instant.parse("2030-01-07T10:00:00Z");
    private static final Instant ELEVEN = Instant.parse("2030-01-07T11:00:00Z");

    @Mock
    private StudySessionRepository sessionRepository;
    @Mock
    private SubjectService subjectService;
    @Mock
    private TopicService topicService;
    @Mock
    private UserService userService;
    @Mock
    private PlannerService plannerService;
    @Mock
    private RevisionService revisionService;
    @Mock
    private FocusSessionRepository focusRepository;

    private final Subject subject = new Subject(STUDENT, "Physics");

    private SessionService serviceAt(String now) {
        lenient().when(userService.require(anyLong())).thenReturn(new User("Asha", "asha@example.com", "h", "UTC"));
        Clock clock = Clock.fixed(Instant.parse(now), ZoneOffset.UTC);
        return new SessionService(sessionRepository, subjectService, topicService, userService, plannerService,
                revisionService, focusRepository, clock);
    }

    private StudySession existingSession(Long id, SessionStatus status) {
        StudySession session = new StudySession(STUDENT, subject, null, SessionType.STUDY, SessionSource.GENERATED,
                TEN, ELEVEN);
        session.setStatus(status);
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }

    @Test
    void overlappingManualSessionIsRejected() {
        SessionService service = serviceAt("2030-01-06T00:00:00Z");
        when(subjectService.require(STUDENT, 10L)).thenReturn(subject);
        when(sessionRepository.findOverlapping(eq(STUDENT), any(), any(), any()))
                .thenReturn(List.of(existingSession(7L, SessionStatus.PLANNED)));

        CreateSessionRequest request = new CreateSessionRequest(10L, null, SessionType.STUDY,
                LocalDate.of(2030, 1, 7), LocalTime.of(10, 30), 60, null, null);

        assertThatThrownBy(() -> service.create(STUDENT, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("overlaps");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void backToBackManualSessionIsAcceptedAndLockedByDefault() {
        SessionService service = serviceAt("2030-01-06T00:00:00Z");
        when(subjectService.require(STUDENT, 10L)).thenReturn(subject);
        when(sessionRepository.findOverlapping(eq(STUDENT), any(), any(), any())).thenReturn(List.of());
        when(sessionRepository.save(any(StudySession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionResponse response = service.create(STUDENT, new CreateSessionRequest(10L, null, null,
                LocalDate.of(2030, 1, 7), LocalTime.of(11, 0), 45, " notes ", null));

        assertThat(response.startAt()).isEqualTo(ELEVEN);
        assertThat(response.plannedMinutes()).isEqualTo(45);
        assertThat(response.locked()).isTrue();
        assertThat(response.source()).isEqualTo(SessionSource.MANUAL);
        assertThat(response.sessionType()).isEqualTo(SessionType.STUDY);
        assertThat(response.localDate()).isEqualTo(LocalDate.of(2030, 1, 7));
        assertThat(response.notes()).isEqualTo("notes");
    }

    @Test
    void cannotClaimMoreMinutesThanHaveElapsed() {
        SessionService service = serviceAt("2030-01-07T10:20:00Z");
        StudySession session = existingSession(5L, SessionStatus.PLANNED);
        when(sessionRepository.findOwned(5L, STUDENT)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.complete(STUDENT, 5L, new CompleteSessionRequest(50, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be more than");
        assertThat(session.getStatus()).isEqualTo(SessionStatus.PLANNED);
    }

    @Test
    void cannotCompleteASessionBeforeItStarts() {
        SessionService service = serviceAt("2030-01-07T09:00:00Z");
        when(sessionRepository.findOwned(5L, STUDENT)).thenReturn(Optional.of(existingSession(5L,
                SessionStatus.PLANNED)));

        assertThatThrownBy(() -> service.complete(STUDENT, 5L, new CompleteSessionRequest(null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void enoughStudyTimeCompletesTheSessionWithoutRescheduling() {
        SessionService service = serviceAt("2030-01-07T11:05:00Z");
        StudySession session = existingSession(5L, SessionStatus.PLANNED);
        when(sessionRepository.findOwned(5L, STUDENT)).thenReturn(Optional.of(session));

        SessionActionResponse response = service.complete(STUDENT, 5L,
                new CompleteSessionRequest(55, null, null, null));

        assertThat(response.session().status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(response.session().actualMinutes()).isEqualTo(55);
        assertThat(response.rescheduledTo()).isNull();
        verify(plannerService, never()).rescheduleAfterChange(anyLong(), anyList());
    }

    @Test
    void incompleteSessionIsRescheduled() {
        SessionService service = serviceAt("2030-01-07T11:05:00Z");
        StudySession session = existingSession(5L, SessionStatus.IN_PROGRESS);
        session.setStartedAt(TEN);
        when(sessionRepository.findOwned(5L, STUDENT)).thenReturn(Optional.of(session));
        StudySession replacement = existingSession(9L, SessionStatus.PLANNED);
        when(plannerService.rescheduleAfterChange(eq(STUDENT), anyList())).thenReturn(Map.of(5L, replacement));

        SessionActionResponse response = service.complete(STUDENT, 5L,
                new CompleteSessionRequest(20, null, null, null));

        assertThat(response.session().status()).isEqualTo(SessionStatus.INCOMPLETE);
        assertThat(response.rescheduledTo()).isNotNull();
        assertThat(response.rescheduledTo().id()).isEqualTo(9L);
        assertThat(response.message()).contains("incomplete").contains("rescheduled");
    }

    @Test
    void missingASessionTriggersRescheduling() {
        SessionService service = serviceAt("2030-01-07T09:00:00Z");
        StudySession session = existingSession(5L, SessionStatus.PLANNED);
        when(sessionRepository.findOwned(5L, STUDENT)).thenReturn(Optional.of(session));
        when(plannerService.rescheduleAfterChange(eq(STUDENT), anyList())).thenReturn(Map.of());

        SessionActionResponse response = service.miss(STUDENT, 5L);

        assertThat(response.session().status()).isEqualTo(SessionStatus.MISSED);
        assertThat(response.rescheduledTo()).isNull();
        verify(plannerService).rescheduleAfterChange(eq(STUDENT), anyList());
    }

    @Test
    void completedSessionsCannotBeMissed() {
        SessionService service = serviceAt("2030-01-07T12:00:00Z");
        when(sessionRepository.findOwned(5L, STUDENT)).thenReturn(Optional.of(existingSession(5L,
                SessionStatus.COMPLETED)));

        assertThatThrownBy(() -> service.miss(STUDENT, 5L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void responseTimesAreShownInTheStudentsZone() {
        StudySession session = existingSession(5L, SessionStatus.PLANNED);
        SessionResponse response = SessionResponse.from(session, ZoneId.of("Asia/Kolkata"));
        assertThat(response.localStartTime()).isEqualTo(LocalTime.of(15, 30));
        assertThat(response.localEndTime()).isEqualTo(LocalTime.of(16, 30));
    }
}
