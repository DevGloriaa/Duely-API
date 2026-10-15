package com.duesync.service;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class StatementParser {

    public record Row(String date, BigDecimal amount, String narration) {
    }

    public List<Row> parse(InputStream input) throws IOException {
        List<List<String>> lines = readCsv(input);
        int headerIndex = findHeader(lines);
        if (headerIndex < 0) {
            throw new IllegalArgumentException("Could not find the header row. The file needs columns like Date, Narration/Description and Credit/Amount.");
        }

        List<String> header = lines.get(headerIndex);
        int dateCol = find(header, "date");
        int narrationCol = find(header, "narration", "description", "remarks", "details", "particulars");
        int creditCol = find(header, "credit", "deposit", "money in", "lodgement", "cr");
        int amountCol = creditCol >= 0 ? creditCol : find(header, "amount");

        List<Row> rows = new ArrayList<>();
        for (int i = headerIndex + 1; i < lines.size(); i++) {
            List<String> line = lines.get(i);
            BigDecimal amount = money(cell(line, amountCol));
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            String narration = cell(line, narrationCol);
            if (narration.isBlank()) {
                continue;
            }
            rows.add(new Row(cell(line, dateCol), amount, narration));
        }
        return rows;
    }

    private int findHeader(List<List<String>> lines) {
        for (int i = 0; i < Math.min(lines.size(), 30); i++) {
            List<String> line = lines.get(i);
            boolean hasNarration = find(line, "narration", "description", "remarks", "details", "particulars") >= 0;
            boolean hasAmount = find(line, "credit", "deposit", "money in", "lodgement", "amount", "cr") >= 0;
            if (hasNarration && hasAmount) {
                return i;
            }
        }
        return -1;
    }

    private int find(List<String> header, String... names) {
        for (String name : names) {
            for (int i = 0; i < header.size(); i++) {
                String h = header.get(i).toLowerCase().trim();
                if (h.equals(name) || (name.length() > 2 && h.contains(name))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String cell(List<String> line, int col) {
        if (col < 0 || col >= line.size()) {
            return "";
        }
        return line.get(col).trim();
    }

    private BigDecimal money(String raw) {
        String cleaned = raw.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<List<String>> readCsv(InputStream input) throws IOException {
        List<List<String>> lines = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder field = new StringBuilder();
        List<String> current = new ArrayList<>();
        boolean quoted = false;
        int c;
        while ((c = reader.read()) != -1) {
            char ch = (char) c;
            if (ch == '﻿') {
                continue;
            }
            if (quoted) {
                if (ch == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        if (next != -1) {
                            reader.reset();
                        }
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                current.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n') {
                current.add(field.toString());
                field.setLength(0);
                lines.add(current);
                current = new ArrayList<>();
            } else if (ch != '\r') {
                field.append(ch);
            }
        }
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            lines.add(current);
        }
        return lines;
    }
}
