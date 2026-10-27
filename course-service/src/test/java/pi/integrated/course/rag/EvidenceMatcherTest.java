package pi.integrated.course.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceMatcherTest {

    private static final String PASSAGE = "Every morning, my alarm goes off at 6:30 a.m. I don’t like it, but I jump "
            + "out of bed and I make a big cup of coffee before going to work.";

    @Test
    void normalizesCaseQuotesPunctuationAndSpaces() {
        assertEquals("i don t like it", EvidenceMatcher.normalize("  I   DON’T like it!! "), "apostrophes unified then removed like all punctuation");
        assertEquals("6 30 a m", EvidenceMatcher.normalize("6:30 a.m."));
        assertEquals("cafe", EvidenceMatcher.normalize("ｃａｆｅ"), "NFKC: full-width letters");
    }

    @Test
    void acceptsExactQuoteWithDifferentCaseOrPunctuation() {
        assertTrue(EvidenceMatcher.isSupported("my alarm goes off at 6:30 a.m.", PASSAGE));
        assertTrue(EvidenceMatcher.isSupported("MY ALARM GOES OFF, AT 6:30 A.M.!", PASSAGE));
        assertTrue(EvidenceMatcher.isSupported("\"I don't like it, but I jump out of bed\"", PASSAGE));
    }

    @Test
    void toleratesAFewChangedWords() {
        // 9 of 10 words in order (one word changed): 90 % ≥ 80 %.
        assertTrue(EvidenceMatcher.isSupported("I make a huge cup of coffee before going to", PASSAGE));
    }

    @Test
    void rejectsQuotesThatAreNotInThePassage() {
        assertFalse(EvidenceMatcher.isSupported("She drinks tea every evening before going to sleep", PASSAGE));
        // Words of the passage, but scattered: not in one contiguous stretch.
        assertFalse(EvidenceMatcher.isSupported("morning coffee work bed alarm", PASSAGE));
        assertFalse(EvidenceMatcher.isSupported("", PASSAGE));
    }

    @Test
    void quoteMustHaveFiveToFortyWords() {
        assertFalse(EvidenceMatcher.hasValidLength("alarm goes off"));
        assertTrue(EvidenceMatcher.hasValidLength("my alarm goes off at"));
        assertFalse(EvidenceMatcher.hasValidLength("word ".repeat(41)));
    }
}
