package com.duesync.service;

import com.duesync.model.BankTransaction;
import com.duesync.model.Submission;
import com.duesync.model.SubmissionStatus;
import com.duesync.repo.BankTransactionRepository;
import com.duesync.repo.SubmissionRepository;
import com.duesync.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SubmissionService {

    public record StatusView(String studentName, String matricNumber, SubmissionStatus status, String note,
                             LocalDateTime submittedAt, LocalDateTime reviewedAt) {

        static StatusView of(Submission submission) {
            return new StatusView(submission.getStudentName(), submission.getMatricNumber(), submission.getStatus(),
                    submission.getNote(), submission.getSubmittedAt(), submission.getReviewedAt());
        }
    }

    private final SubmissionRepository submissions;
    private final BankTransactionRepository transactions;
    private final ReceiptStorage storage;
    private final MatchingService matching;
    private final LockedTransaction locked;

    public SubmissionService(SubmissionRepository submissions, BankTransactionRepository transactions,
                             ReceiptStorage storage, MatchingService matching, LockedTransaction locked) {
        this.submissions = submissions;
        this.transactions = transactions;
        this.storage = storage;
        this.matching = matching;
        this.locked = locked;
    }

    public StatusView submit(String studentName, String matricNumber, String payerName, MultipartFile receipt) {
        String name = required(cleanName(studentName), "studentName", 150);
        String matric = required(normalizeMatric(matricNumber), "matricNumber", 50);
        String payer = cleanName(payerName);
        if (payer.isEmpty()) {
            payer = name;
        } else if (payer.length() > 150) {
            throw ApiException.badRequest("payerName must be at most 150 characters.");
        }

        submissions.findByMatricNumber(matric).ifPresent(this::ensureCanResubmit);
        String receiptFile = storage.store(receipt);
        String finalPayer = payer;

        AtomicReference<String> oldReceipt = new AtomicReference<>();
        Submission saved;
        try {
            saved = locked.run(() -> {
                Submission submission = submissions.findByMatricNumber(matric).orElse(null);
                if (submission == null) {
                    submission = new Submission();
                    submission.setMatricNumber(matric);
                } else {
                    ensureCanResubmit(submission);
                    oldReceipt.set(submission.getReceiptFile());
                }
                submission.setStudentName(name);
                submission.setPayerName(finalPayer);
                submission.setReceiptFile(receiptFile);
                submission.setStatus(SubmissionStatus.PENDING);
                submission.setTransactionId(null);
                submission.setNote(null);
                submission.setSubmittedAt(LocalDateTime.now());
                submission.setReviewedAt(null);
                Submission result = submissions.save(submission);
                matching.run();
                return result;
            });
        } catch (RuntimeException e) {
            storage.delete(receiptFile);
            throw e;
        }
        storage.delete(oldReceipt.get());
        return StatusView.of(saved);
    }

    public StatusView status(String matricNumber) {
        String matric = normalizeMatric(matricNumber);
        return submissions.findByMatricNumber(matric)
                .map(StatusView::of)
                .orElseThrow(() -> ApiException.notFound("No submission found for matric number " + matric + "."));
    }

    public List<Submission> list(SubmissionStatus status) {
        if (status == null) {
            return submissions.findAllByOrderBySubmittedAtDesc();
        }
        return submissions.findByStatusOrderBySubmittedAtDesc(status);
    }

    public Map<String, Long> summary() {
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0;
        for (SubmissionStatus status : SubmissionStatus.values()) {
            long count = submissions.countByStatus(status);
            counts.put(status.name().toLowerCase(), count);
            total += count;
        }
        counts.put("total", total);
        counts.put("unclaimedTransactions", transactions.countBySubmissionIdIsNull());
        return counts;
    }

    public Submission get(Long id) {
        return submissions.findById(id)
                .orElseThrow(() -> ApiException.notFound("Submission #" + id + " not found."));
    }

    public Submission verify(Long id, Long transactionId) {
        if (transactionId == null) {
            throw ApiException.badRequest("transactionId is required.");
        }
        return locked.run(() -> {
            Submission submission = get(id);
            BankTransaction transaction = transactions.findById(transactionId)
                    .orElseThrow(() -> ApiException.notFound("Transaction #" + transactionId + " not found."));
            if (transaction.getSubmissionId() != null && !transaction.getSubmissionId().equals(submission.getId())) {
                throw ApiException.conflict("Transaction #" + transactionId + " is already linked to another submission.");
            }
            if (submission.getTransactionId() != null && !submission.getTransactionId().equals(transactionId)) {
                release(submission.getTransactionId());
            }
            submission.setStatus(SubmissionStatus.VERIFIED);
            submission.setTransactionId(transactionId);
            submission.setNote(null);
            submission.setReviewedAt(LocalDateTime.now());
            transaction.setSubmissionId(submission.getId());
            transactions.save(transaction);
            return submissions.save(submission);
        });
    }

    public Submission reject(Long id, String note) {
        String reason = required(note == null ? "" : note.trim(), "note", 255);
        return locked.run(() -> {
            Submission submission = get(id);
            if (submission.getTransactionId() != null) {
                release(submission.getTransactionId());
            }
            submission.setStatus(SubmissionStatus.REJECTED);
            submission.setTransactionId(null);
            submission.setNote(reason);
            submission.setReviewedAt(LocalDateTime.now());
            return submissions.save(submission);
        });
    }

    public String exportCsv() {
        List<Submission> verified = submissions.findByStatusOrderByStudentNameAsc(SubmissionStatus.VERIFIED);
        List<Long> ids = verified.stream().map(Submission::getTransactionId).filter(Objects::nonNull).toList();
        Map<Long, BankTransaction> byId = transactions.findAllById(ids).stream()
                .collect(Collectors.toMap(BankTransaction::getId, Function.identity()));

        StringBuilder csv = new StringBuilder("Student Name,Matric Number,Payer Name,Amount,Payment Date,Verified At\r\n");
        for (Submission submission : verified) {
            BankTransaction transaction = byId.get(submission.getTransactionId());
            csv.append(String.join(",",
                    csvCell(submission.getStudentName()),
                    csvCell(submission.getMatricNumber()),
                    csvCell(submission.getPayerName()),
                    csvCell(transaction == null ? "" : transaction.getAmount().toPlainString()),
                    csvCell(transaction == null ? "" : transaction.getDate()),
                    csvCell(submission.getReviewedAt() == null ? "" : submission.getReviewedAt().withNano(0).toString())
            )).append("\r\n");
        }
        return csv.toString();
    }

    private void ensureCanResubmit(Submission existing) {
        if (existing.getStatus() != SubmissionStatus.REJECTED) {
            throw ApiException.conflict("A submission for " + existing.getMatricNumber()
                    + " already exists with status " + existing.getStatus() + ".");
        }
    }

    private void release(Long transactionId) {
        transactions.findById(transactionId).ifPresent(transaction -> {
            transaction.setSubmissionId(null);
            transactions.save(transaction);
        });
    }

    private String required(String value, String field, int maxLength) {
        if (value.isEmpty()) {
            throw ApiException.badRequest(field + " is required.");
        }
        if (value.length() > maxLength) {
            throw ApiException.badRequest(field + " must be at most " + maxLength + " characters.");
        }
        return value;
    }

    private String cleanName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeMatric(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toUpperCase();
    }

    private String csvCell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
