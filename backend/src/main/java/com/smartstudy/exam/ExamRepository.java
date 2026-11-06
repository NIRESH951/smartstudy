package com.smartstudy.exam;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    Optional<Exam> findByIdAndUserId(Long id, Long userId);

    @Query("select e from Exam e join fetch e.subject where e.id = :id and e.userId = :userId")
    Optional<Exam> findOwnedWithSubject(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select e from Exam e join fetch e.subject where e.userId = :userId order by e.examDate asc, e.id asc")
    List<Exam> findAllWithSubject(@Param("userId") Long userId);

    @Query("select e from Exam e join fetch e.subject s where e.userId = :userId and e.examDate >= :rangeStart "
            + "order by e.examDate asc, e.id asc")
    List<Exam> findUpcoming(@Param("userId") Long userId, @Param("rangeStart") LocalDate from);

    @Query("select e from Exam e join fetch e.subject s where e.userId = :userId and e.examDate >= :rangeStart "
            + "and e.examDate <= :rangeEnd order by e.examDate asc, e.id asc")
    List<Exam> findBetween(@Param("userId") Long userId, @Param("rangeStart") LocalDate from, @Param("rangeEnd") LocalDate to);
}
