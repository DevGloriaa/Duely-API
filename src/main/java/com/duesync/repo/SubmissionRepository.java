package com.duesync.repo;

import com.duesync.model.Submission;
import com.duesync.model.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByMatricNumber(String matricNumber);

    List<Submission> findByStatusIn(List<SubmissionStatus> statuses);

    List<Submission> findAllByOrderBySubmittedAtDesc();

    List<Submission> findByStatusOrderBySubmittedAtDesc(SubmissionStatus status);

    List<Submission> findByStatusOrderByStudentNameAsc(SubmissionStatus status);

    long countByStatus(SubmissionStatus status);
}
