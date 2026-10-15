package com.duesync.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementParserTest {

    private final StatementParser parser = new StatementParser();

    private List<StatementParser.Row> parse(String csv) throws IOException {
        return parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void skipsPreambleAndDebitRows() throws IOException {
        String csv = """
                Account Statement
                Account No: 0123456789

                Trans Date,Value Date,Narration,Debit,Credit,Balance
                01-Sep-2026,01-Sep-2026,TRF FROM JOHN DOE,,"5,000.00","15,000.00"
                02-Sep-2026,02-Sep-2026,POS PURCHASE,"2,000.00",,"13,000.00"
                """;

        List<StatementParser.Row> rows = parse(csv);

        assertEquals(1, rows.size());
        assertEquals("01-Sep-2026", rows.get(0).date());
        assertEquals("TRF FROM JOHN DOE", rows.get(0).narration());
        assertAmount("5000", rows.get(0).amount());
    }

    @Test
    void usesAmountColumnWhenThereIsNoCreditColumn() throws IOException {
        String csv = """
                Date,Description,Amount
                2026-09-01,Transfer from Ada Obi,5000
                2026-09-02,SMS charge,-50
                """;

        List<StatementParser.Row> rows = parse(csv);

        assertEquals(1, rows.size());
        assertEquals("Transfer from Ada Obi", rows.get(0).narration());
        assertAmount("5000", rows.get(0).amount());
    }

    @Test
    void handlesQuotedFieldsWithCommasAndEscapedQuotes() throws IOException {
        String csv = "Date,Narration,Credit\n2026-09-01,\"TRF, FROM \"\"ADA\"\" OBI\",5000\n";

        List<StatementParser.Row> rows = parse(csv);

        assertEquals(1, rows.size());
        assertEquals("TRF, FROM \"ADA\" OBI", rows.get(0).narration());
    }

    @Test
    void handlesByteOrderMarkAndWindowsLineEndings() throws IOException {
        String csv = "﻿Date,Narration,Credit\r\n2026-09-01,ADA OBI,5000\r\n2026-09-02,EMEKA EZE,6000\r\n";

        List<StatementParser.Row> rows = parse(csv);

        assertEquals(2, rows.size());
        assertEquals("2026-09-01", rows.get(0).date());
        assertEquals("ADA OBI", rows.get(0).narration());
        assertEquals("EMEKA EZE", rows.get(1).narration());
    }

    @Test
    void stripsCurrencySymbolsAndThousandSeparators() throws IOException {
        String csv = "Date,Narration,Credit\n2026-09-01,ADA OBI,\"₦5,000.50\"\n";

        List<StatementParser.Row> rows = parse(csv);

        assertAmount("5000.50", rows.get(0).amount());
    }

    @Test
    void skipsRowsWithBlankNarrationOrNoAmount() throws IOException {
        String csv = """
                Date,Narration,Credit
                2026-09-01,,5000
                2026-09-02,ADA OBI,
                2026-09-03,ADA OBI,abc
                2026-09-04,EMEKA EZE,5000
                """;

        List<StatementParser.Row> rows = parse(csv);

        assertEquals(1, rows.size());
        assertEquals("EMEKA EZE", rows.get(0).narration());
    }

    @Test
    void readsLastLineWithoutTrailingNewline() throws IOException {
        List<StatementParser.Row> rows = parse("Date,Narration,Credit\n2026-09-01,ADA OBI,5000");

        assertEquals(1, rows.size());
    }

    @Test
    void recognisesShortCrColumn() throws IOException {
        String csv = """
                Date,Remarks,Dr,Cr
                2026-09-01,ADA OBI,,5000
                2026-09-02,ATM WITHDRAWAL,1000,
                """;

        List<StatementParser.Row> rows = parse(csv);

        assertEquals(1, rows.size());
        assertEquals("ADA OBI", rows.get(0).narration());
    }

    @Test
    void returnsNoRowsForHeaderOnly() throws IOException {
        assertTrue(parse("Date,Narration,Credit\n").isEmpty());
    }

    @Test
    void throwsWhenHeaderIsMissing() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> parse("foo,bar\n1,2\n"));

        assertTrue(error.getMessage().contains("header"));
    }
}
