package com.duesync.service;

import com.duesync.model.BankTransaction;
import com.duesync.model.Submission;
import com.duesync.model.SubmissionStatus;
import com.duesync.repo.BankTransactionRepository;
import com.duesync.repo.SubmissionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MatchingService {

    public record Result(int verified, int flagged) {
    }

    private final SubmissionRepository submissions;
    private final BankTransactionRepository transactions;
    private final SettingsService settings;

    public MatchingService(SubmissionRepository submissions, BankTransactionRepository transactions, SettingsService settings) {
        this.submissions = submissions;
        this.transactions = transactions;
        this.settings = settings;
    }

    public Result run() {
        BigDecimal dues = settings.getDuesAmount();
        List<Submission> pending = submissions.findByStatusIn(List.of(SubmissionStatus.PENDING));
        List<BankTransaction> unclaimed = transactions.findBySubmissionIdIsNull();

        Map<Long, Integer> claimants = new HashMap<>();
        for (BankTransaction transaction : unclaimed) {
            int count = 0;
            for (Submission submission : pending) {
                if (NameMatcher.strongMatch(submission.getPayerName(), transaction.getNarration())) {
                    count++;
                }
            }
            claimants.put(transaction.getId(), count);
        }

        int verified = 0;
        int flagged = 0;
        for (Submission submission : pending) {
            String payer = submission.getPayerName();
            List<BankTransaction> nameMatches = unclaimed.stream()
                    .filter(t -> NameMatcher.strongMatch(payer, t.getNarration()))
                    .toList();
            List<BankTransaction> fullPayments = nameMatches.stream()
                    .filter(t -> t.getAmount().compareTo(dues) >= 0)
                    .toList();

            if (fullPayments.size() == 1 && claimants.get(fullPayments.get(0).getId()) == 1) {
                verify(submission, fullPayments.get(0));
                verified++;
            } else if (fullPayments.size() > 1) {
                flag(submission, "Found " + fullPayments.size() + " payments matching \"" + payer + "\" ("
                        + ids(fullPayments) + "). Please confirm which one is correct.");
                flagged++;
            } else if (fullPayments.size() == 1) {
                flag(submission, "The payment matching \"" + payer + "\" (" + ids(fullPayments)
                        + ") also matches another student. Please confirm who it belongs to.");
                flagged++;
            } else if (!nameMatches.isEmpty()) {
                flag(submission, "Found a payment matching \"" + payer + "\" (" + ids(nameMatches)
                        + ") but it is below the dues amount of " + dues.toPlainString() + ".");
                flagged++;
            }
        }
        return new Result(verified, flagged);
    }

    private void verify(Submission submission, BankTransaction transaction) {
        submission.setStatus(SubmissionStatus.VERIFIED);
        submission.setTransactionId(transaction.getId());
        submission.setNote(null);
        submission.setReviewedAt(LocalDateTime.now());
        transaction.setSubmissionId(submission.getId());
        submissions.save(submission);
        transactions.save(transaction);
    }

    private void flag(Submission submission, String note) {
        submission.setStatus(SubmissionStatus.FLAGGED);
        submission.setNote(note.length() > 255 ? note.substring(0, 252) + "..." : note);
        submissions.save(submission);
    }

    private String ids(List<BankTransaction> list) {
        String joined = list.stream().map(t -> "#" + t.getId()).collect(Collectors.joining(", "));
        return (list.size() == 1 ? "transaction " : "transactions ") + joined;
    }
}
