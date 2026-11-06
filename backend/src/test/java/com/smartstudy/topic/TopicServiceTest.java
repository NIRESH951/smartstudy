package com.smartstudy.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.smartstudy.common.exception.ConflictException;
import com.smartstudy.common.exception.ResourceNotFoundException;
import com.smartstudy.performance.TopicMasteryRepository;
import com.smartstudy.planner.StudySessionRepository;
import com.smartstudy.revision.SpacedRepetitionScheduler;
import com.smartstudy.subject.Subject;
import com.smartstudy.subject.SubjectService;
import com.smartstudy.user.User;
import com.smartstudy.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

    private static final Long STUDENT = 1L;

    @Mock
    private TopicRepository topicRepository;
    @Mock
    private SubjectService subjectService;
    @Mock
    private TopicMasteryRepository masteryRepository;
    @Mock
    private StudySessionRepository sessionRepository;
    @Mock
    private UserService userService;

    private TopicService service;
    private Subject subject;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-31T06:00:00Z"), ZoneOffset.UTC);
        service = new TopicService(topicRepository, subjectService, masteryRepository, sessionRepository, userService,
                new SpacedRepetitionScheduler(), clock);
        subject = new Subject(STUDENT, "Data Structures");
        lenient().when(userService.require(anyLong())).thenReturn(new User("Asha", "asha@example.com", "hash", "UTC"));
        lenient().when(masteryRepository.findAllForUser(anyLong())).thenReturn(List.of());
        lenient().when(sessionRepository.sumActualMinutesByTopic(anyLong(), any(), any())).thenReturn(List.of());
    }

    @Test
    void createsATopicUnderTheStudentsSubject() {
        when(subjectService.require(STUDENT, 10L)).thenReturn(subject);
        when(topicRepository.existsBySubjectIdAndNameIgnoreCase(any(), eq("AVL Trees"))).thenReturn(false);
        when(topicRepository.findMaxSortOrder(any())).thenReturn(2);
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TopicResponse response = service.create(STUDENT, 10L,
                new TopicRequest(" AVL Trees ", "Rotations", 4, 180, null));

        assertThat(response.name()).isEqualTo("AVL Trees");
        assertThat(response.subjectName()).isEqualTo("Data Structures");
        assertThat(response.difficulty()).isEqualTo(4);
        assertThat(response.estimatedMinutes()).isEqualTo(180);
        assertThat(response.sortOrder()).isEqualTo(3);
        assertThat(response.status()).isEqualTo(TopicStatus.NOT_STARTED);
        assertThat(response.masteryScore()).isNull();
        assertThat(response.masteryLevel()).isEqualTo("NOT_ASSESSED");
        assertThat(response.nextRevisionDate()).isNull();
    }

    @Test
    void duplicateTopicNamesInOneSubjectAreRejected() {
        when(subjectService.require(STUDENT, 10L)).thenReturn(subject);
        when(topicRepository.existsBySubjectIdAndNameIgnoreCase(any(), eq("AVL Trees"))).thenReturn(true);

        assertThatThrownBy(() -> service.create(STUDENT, 10L, new TopicRequest("AVL Trees", null, 3, 120, null)))
                .isInstanceOf(ConflictException.class);
        verify(topicRepository, never()).save(any());
    }

    @Test
    void topicsCannotBeAddedToAnotherStudentsSubject() {
        when(subjectService.require(STUDENT, 99L)).thenThrow(new ResourceNotFoundException("Subject", 99L));

        assertThatThrownBy(() -> service.create(STUDENT, 99L, new TopicRequest("Graphs", null, 3, 120, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(topicRepository, never()).save(any());
    }

    @Test
    void completingATopicStartsItsRevisionCycle() {
        Topic topic = new Topic(STUDENT, subject, "Heaps");
        LocalDate today = LocalDate.of(2026, 10, 31);
        Instant now = Instant.parse("2026-10-31T06:00:00Z");

        service.applyStatus(topic, TopicStatus.COMPLETED, today, now);
        assertThat(topic.getStatus()).isEqualTo(TopicStatus.COMPLETED);
        assertThat(topic.getCompletedAt()).isEqualTo(now);
        assertThat(topic.getNextRevisionDate()).isEqualTo(today.plusDays(1));

        // Re-opening keeps the revision schedule but clears the completion time.
        service.applyStatus(topic, TopicStatus.IN_PROGRESS, today, now);
        assertThat(topic.getCompletedAt()).isNull();
        assertThat(topic.getNextRevisionDate()).isEqualTo(today.plusDays(1));
    }
}
