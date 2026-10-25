package pi.integrated.preevaluation.rag;

import org.junit.jupiter.api.Test;
import pi.integrated.preevaluation.rag.model.RetrievedPassage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RagPromptTemplatesTest {

    @Test
    void includesRetrievedPassagesInUserPrompt() {
        RetrievedPassage p = RetrievedPassage.builder()
                .documentId("a2-grammar-present-continuous")
                .ruleTitle("Present continuous")
                .passageText("Use am/is/are + -ing for actions happening now.")
                .score(0.9)
                .sources(List.of())
                .build();

        String prompt = RagPromptTemplates.userPromptForMistake(
                "I ___ a book right now.",
                "read",
                "am reading",
                "GRAMMAR",
                "A2",
                List.of(p));

        assertTrue(prompt.contains("Present continuous"));
        assertTrue(prompt.contains("am reading"));
    }

    @Test
    void statesInsufficientDocsWhenNoPassages() {
        String ctx = RagPromptTemplates.formatRetrievedContext(List.of());
        assertTrue(ctx.toLowerCase().contains("no matching"));
    }
}
