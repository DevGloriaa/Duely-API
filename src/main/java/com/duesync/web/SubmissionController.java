package com.duesync.web;

import com.duesync.service.SubmissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissions;

    public SubmissionController(SubmissionService submissions) {
        this.submissions = submissions;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionService.StatusView submit(@RequestParam(required = false) String studentName,
                                               @RequestParam(required = false) String matricNumber,
                                               @RequestParam(required = false) String payerName,
                                               @RequestPart(required = false) MultipartFile receipt) {
        return submissions.submit(studentName, matricNumber, payerName, receipt);
    }

    @GetMapping("/status/{*matricNumber}")
    public SubmissionService.StatusView status(@PathVariable String matricNumber) {
        return submissions.status(matricNumber.startsWith("/") ? matricNumber.substring(1) : matricNumber);
    }
}
