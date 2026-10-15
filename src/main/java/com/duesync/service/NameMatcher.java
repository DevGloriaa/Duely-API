package com.duesync.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class NameMatcher {

    private NameMatcher() {
    }

    public static Set<String> tokens(String text) {
        if (text == null) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase().split("[^a-z]+"))
                .filter(t -> t.length() >= 2)
                .collect(Collectors.toSet());
    }

    public static int score(String payerName, String narration) {
        Set<String> narrationTokens = tokens(narration);
        int hits = 0;
        for (String token : tokens(payerName)) {
            if (narrationTokens.contains(token)) {
                hits++;
            }
        }
        return hits;
    }

    public static boolean strongMatch(String payerName, String narration) {
        int nameSize = tokens(payerName).size();
        int hits = score(payerName, narration);
        return hits >= 2 || (nameSize == 1 && hits == 1);
    }
}
