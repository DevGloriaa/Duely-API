package com.duesync.web;

import com.duesync.model.BankTransaction;
import com.duesync.model.Submission;
import com.duesync.model.SubmissionStatus;
import com.duesync.repo.BankTransactionRepository;
import com.duesync.service.ReceiptStorage;
import com.duesync.service.SettingsService;
import com.duesync.service.StatementService;
import com.duesync.service.SubmissionService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record DuesAmountRequest(BigDecimal amount) {
    }

    public record VerifyRequest(Long transactionId) {
    }

    public record RejectRequest(String note) {
    }

    private final SettingsService settings;
    private final StatementService statements;
    private final SubmissionService submissions;
    private final ReceiptStorage storage;
    private final BankTransactionRepository transactions;

    public AdminController(SettingsService settings, StatementService statements, SubmissionService submissions,
                           ReceiptStorage storage, BankTransactionRepository transactions) {
        this.settings = settings;
        this.statements = statements;
        this.submissions = submissions;
        this.storage = storage;
        this.transactions = transactions;
    }

    @GetMapping("/settings/dues-amount")
    public Map<String, BigDecimal> getDuesAmount() {
        return Map.of("amount", settings.getDuesAmount());
    }

    @PutMapping("/settings/dues-amount")
    public Map<String, BigDecimal> setDuesAmount(@RequestBody DuesAmountRequest request) {
        return Map.of("amount", settings.setDuesAmount(request.amount()));
    }

    @PostMapping(value = "/statements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StatementService.ImportResult uploadStatement(@RequestPart(required = false) MultipartFile file) {
        return statements.importCsv(file);
    }

    @GetMapping("/summary")
    public Map<String, Long> summary() {
        return submissions.summary();
    }

    @GetMapping("/submissions")
    public List<Submission> listSubmissions(@RequestParam(required = false) SubmissionStatus status) {
        return submissions.list(status);
    }

    @GetMapping("/submissions/{id}/receipt")
    public ResponseEntity<Resource> receipt(@PathVariable Long id) {
        Submission submission = submissions.get(id);
        Resource file = storage.load(submission.getReceiptFile());
        String filename = submission.getMatricNumber().replaceAll("[^A-Za-z0-9]+", "-")
                + "-receipt." + storage.extension(submission.getReceiptFile());
        return ResponseEntity.ok()
                .contentType(storage.mediaType(submission.getReceiptFile()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file);
    }

    @PostMapping("/submissions/{id}/verify")
    public Submission verify(@PathVariable Long id, @RequestBody VerifyRequest request) {
        return submissions.verify(id, request.transactionId());
    }

    @PostMapping("/submissions/{id}/reject")
    public Submission reject(@PathVariable Long id, @RequestBody RejectRequest request) {
        return submissions.reject(id, request.note());
    }

    @GetMapping("/transactions")
    public List<BankTransaction> listTransactions(@RequestParam(defaultValue = "false") boolean unclaimed) {
        return unclaimed ? transactions.findBySubmissionIdIsNullOrderByIdDesc() : transactions.findAllByOrderByIdDesc();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        byte[] body = ("﻿" + submissions.exportCsv()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("verified-students.csv").build().toString())
                .body(body);
    }
}
