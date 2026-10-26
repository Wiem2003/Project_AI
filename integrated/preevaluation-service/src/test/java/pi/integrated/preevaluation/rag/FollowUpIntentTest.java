package pi.integrated.preevaluation.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FollowUpIntentTest {

    @Test
    void detectsCommonFollowUpPhrases() {
        assertEquals(FollowUpIntent.EXPLAIN_MORE, FollowUpIntent.detect("Explain more please"));
        assertEquals(FollowUpIntent.ANOTHER_EXAMPLE, FollowUpIntent.detect("Give me another example"));
        assertEquals(FollowUpIntent.SIMPLIFY, FollowUpIntent.detect("Can you simplify the explanation?"));
        assertEquals(FollowUpIntent.EXERCISE, FollowUpIntent.detect("Give me an exercise"));
    }
}
