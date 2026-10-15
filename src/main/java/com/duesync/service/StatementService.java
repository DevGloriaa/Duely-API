package com.duesync.service;

import com.duesync.model.BankTransaction;
import com.duesync.repo.BankTransactionRepository;
import com.duesync.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

@Service
public class StatementService {

    public record ImportResult(int imported, int skipped, int newlyVerified, int flagged) {
    }

    private final StatementParser parser;
    private final BankTransactionRepository transactions;
    private final MatchingService matching;
    private final LockedTransaction locked;

    public StatementService(StatementParser parser, BankTransactionRepository transactions,
                            MatchingService matching, LockedTransaction locked) {
        this.parser = parser;
        this.transactions = transactions;
        this.matching = matching;
        this.locked = locked;
    }

    public ImportResult importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Please choose a CSV statement file.");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".csv") && !filename.endsWith(".txt")) {
            throw ApiException.badRequest("The statement must be a CSV file. Export it from your bank as CSV and try again.");
        }

        List<StatementParser.Row> rows;
        try (InputStream input = file.getInputStream()) {
            rows = parser.parse(input);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read statement", e);
        }

        return locked.run(() -> {
            int imported = 0;
            int skipped = 0;
            for (StatementParser.Row row : rows) {
                String date = limit(row.date(), 100);
                String narration = limit(row.narration(), 1000);
                String fingerprint = fingerprint(date, row, narration);
                if (transactions.existsByFingerprint(fingerprint)) {
                    skipped++;
                    continue;
                }
                BankTransaction transaction = new BankTransaction();
                transaction.setDate(date);
                transaction.setAmount(row.amount());
                transaction.setNarration(narration);
                transaction.setFingerprint(fingerprint);
                transactions.save(transaction);
                imported++;
            }
            MatchingService.Result result = matching.run();
            return new ImportResult(imported, skipped, result.verified(), result.flagged());
        });
    }

    private String fingerprint(String date, StatementParser.Row row, String narration) {
        return date.toLowerCase() + "|"
                + row.amount().stripTrailingZeros().toPlainString() + "|"
                + narration.toLowerCase().replaceAll("\\s+", " ");
    }

    private String limit(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
