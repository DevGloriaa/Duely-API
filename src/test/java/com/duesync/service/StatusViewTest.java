package com.duesync.service;

import com.duesync.model.Submission;
import com.duesync.model.SubmissionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JsonTest
class StatusViewTest {

    @Autowired
    private ObjectMapper json;

    private Submission submission(LocalDateTime submittedAt, LocalDateTime reviewedAt) {
        Submission submission = new Submission();
        submission.setStudentName("Ada Obi");
        submission.setMatricNumber("CSC/2021/001");
        submission.setStatus(SubmissionStatus.VERIFIED);
        submission.setSubmittedAt(submittedAt);
        submission.setReviewedAt(reviewedAt);
        return submission;
    }

    @Test
    void copiesAllFieldsIncludingTimestamps() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 10, 10, 6, 51, 20, 919282000);
        LocalDateTime reviewedAt = LocalDateTime.of(2026, 10, 10, 6, 51, 23, 284015900);

        SubmissionService.StatusView view = SubmissionService.StatusView.of(submission(submittedAt, reviewedAt));

        assertEquals("Ada Obi", view.studentName());
        assertEquals("CSC/2021/001", view.matricNumber());
        assertEquals(SubmissionStatus.VERIFIED, view.status());
        assertNull(view.note());
        assertEquals(submittedAt, view.submittedAt());
        assertEquals(reviewedAt, view.reviewedAt());
    }

    @Test
    void serializesTimestampsAsIsoStrings() throws Exception {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 10, 10, 6, 51, 20, 919282000);

        JsonNode node = json.valueToTree(SubmissionService.StatusView.of(submission(submittedAt, null)));

        assertTrue(node.get("submittedAt").isTextual());
        assertEquals("2026-10-10T06:51:20.919282", node.get("submittedAt").asText());
        assertTrue(node.get("reviewedAt").isNull());
    }
}
