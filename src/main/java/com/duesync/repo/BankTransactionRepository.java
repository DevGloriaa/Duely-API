package com.duesync.repo;

import com.duesync.model.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    boolean existsByFingerprint(String fingerprint);

    List<BankTransaction> findBySubmissionIdIsNull();

    List<BankTransaction> findAllByOrderByIdDesc();

    List<BankTransaction> findBySubmissionIdIsNullOrderByIdDesc();

    long countBySubmissionIdIsNull();
}
