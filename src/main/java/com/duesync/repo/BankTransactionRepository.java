package com.duesync.repo;

import com.duesync.model.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    Optional<BankTransaction> findByFingerprint(String fingerprint);

    boolean existsByFingerprint(String fingerprint);

    List<BankTransaction> findBySubmissionIdIsNull();

    List<BankTransaction> findAllByOrderByIdDesc();

    List<BankTransaction> findBySubmissionIdIsNullOrderByIdDesc();

    long countBySubmissionIdIsNull();
}
