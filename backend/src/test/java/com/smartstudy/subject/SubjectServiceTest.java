package com.smartstudy.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.smartstudy.common.exception.ConflictException;
import com.smartstudy.common.exception.ResourceNotFoundException;
import com.smartstudy.exam.ExamRepository;
import com.smartstudy.performance.TopicMasteryRepository;
import com.smartstudy.topic.TopicRepository;
import com.smartstudy.user.User;
import com.smartstudy.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    private static final Long STUDENT = 1L;
    private static final Long OTHER_STUDENT = 2L;

    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private TopicRepository topicRepository;
    @Mock
    private TopicMasteryRepository masteryRepository;
    @Mock
    private ExamRepository examRepository;
    @Mock
    private UserService userService;

    private SubjectService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-31T06:00:00Z"), ZoneOffset.UTC);
        service = new SubjectService(subjectRepository, topicRepository, masteryRepository, examRepository,
                userService, clock);
        lenient().when(userService.require(anyLong())).thenReturn(new User("Asha", "asha@example.com", "hash", "UTC"));
        lenient().when(topicRepository.countBySubject(anyLong(), any())).thenReturn(List.of());
        lenient().when(masteryRepository.findAllWithTopic(anyLong())).thenReturn(List.of());
        lenient().when(examRepository.findUpcoming(anyLong(), any())).thenReturn(List.of());
    }

    @Test
    void createsASubjectOwnedByTheCurrentStudent() {
        when(subjectRepository.existsByUserIdAndNameIgnoreCase(STUDENT, "Physics")).thenReturn(false);
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubjectResponse response = service.create(STUDENT, new SubjectRequest("  Physics ", "PH101", "#3B82F6", 4,
                new BigDecimal("85"), "Mechanics", null));

        ArgumentCaptor<Subject> saved = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(STUDENT);
        assertThat(saved.getValue().getName()).isEqualTo("Physics");
        assertThat(saved.getValue().getImportance()).isEqualTo(4);
        assertThat(response.name()).isEqualTo("Physics");
        assertThat(response.color()).isEqualTo("#3B82F6");
        assertThat(response.topicCount()).isZero();
        assertThat(response.averageMastery()).isNull();
    }

    @Test
    void blankColourFallsBackToTheDefault() {
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubjectResponse response = service.create(STUDENT, new SubjectRequest("Chemistry", null, " ", 3, null, null,
                null));

        assertThat(response.color()).isEqualTo("#10B981");
        assertThat(response.code()).isNull();
    }

    @Test
    void duplicateNamesAreRejected() {
        when(subjectRepository.existsByUserIdAndNameIgnoreCase(STUDENT, "Physics")).thenReturn(true);

        assertThatThrownBy(() -> service.create(STUDENT, new SubjectRequest("Physics", null, null, 3, null, null,
                null))).isInstanceOf(ConflictException.class);
        verify(subjectRepository, never()).save(any());
    }

    @Test
    void anotherStudentsSubjectBehavesAsIfItDidNotExist() {
        when(subjectRepository.findByIdAndUserId(5L, OTHER_STUDENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(OTHER_STUDENT, 5L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update(OTHER_STUDENT, 5L,
                new SubjectRequest("Hacked", null, null, 3, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(OTHER_STUDENT, 5L)).isInstanceOf(ResourceNotFoundException.class);
        verify(subjectRepository, never()).delete(any());
    }
}
