package com.duesync.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameMatcherTest {

    @Test
    void tokensAreLowercaseWordsOfAtLeastTwoLetters() {
        assertEquals(Set.of("ada", "obi", "neil"), NameMatcher.tokens("ADA-Obi O'Neil 2026"));
    }

    @Test
    void tokensOfNullIsEmpty() {
        assertTrue(NameMatcher.tokens(null).isEmpty());
    }

    @Test
    void scoreCountsNameWordsFoundInNarration() {
        assertEquals(2, NameMatcher.score("Ada Chioma Obi", "NIP/ADA OBI/DUES"));
    }

    @Test
    void twoWordsMatchInAnyOrder() {
        assertTrue(NameMatcher.strongMatch("Ada Obi", "TRF FROM OBI ADA /REF123"));
    }

    @Test
    void oneWordIsNotEnoughForMultiWordName() {
        assertFalse(NameMatcher.strongMatch("Ada Obi", "TRF FROM ADA NWOSU"));
    }

    @Test
    void oneWordIsEnoughForSingleWordName() {
        assertTrue(NameMatcher.strongMatch("Chinedu", "TRANSFER FROM CHINEDU"));
    }

    @Test
    void partialWordsDoNotMatch() {
        assertFalse(NameMatcher.strongMatch("Ada Obi", "TRF FROM ADAEZE OBINNA"));
    }

    @Test
    void matchingIsCaseInsensitive() {
        assertTrue(NameMatcher.strongMatch("emeka eze", "Trf From EMEKA EZE"));
    }

    @Test
    void twoOfThreeNamesIsAStrongMatch() {
        assertTrue(NameMatcher.strongMatch("Ada Chioma Obi", "MOB TRF ADA OBI"));
    }

    @Test
    void nullInputsNeverMatch() {
        assertFalse(NameMatcher.strongMatch(null, "TRF FROM ADA OBI"));
        assertFalse(NameMatcher.strongMatch("Ada Obi", null));
    }
}
