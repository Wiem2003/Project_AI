package pi.integrated.preevaluation.rag;

import org.springframework.stereotype.Component;
import pi.integrated.preevaluation.rag.model.IndexedChunk;
import pi.integrated.preevaluation.rag.model.KnowledgeDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PedagogicalChunkBuilder {

    public List<IndexedChunk> toChunks(KnowledgeDocument doc) {
        String levels = doc.getLevels() == null ? "" : String.join(",", doc.getLevels());
        String category = doc.getCategory() == null ? "" : doc.getCategory().trim().toUpperCase();

        String passage = buildPassageText(doc);
        String searchable = buildSearchableText(doc, levels, category);

        IndexedChunk chunk = IndexedChunk.builder()
                .chunkId(doc.getId() + "#0")
                .documentId(doc.getId())
                .ruleTitle(doc.getRuleTitle())
                .levelScope(levels)
                .category(category)
                .searchableText(searchable)
                .passageText(passage)
                .sources(doc.getSources())
                .embedding(null)
                .build();
        return List.of(chunk);
    }

    public List<IndexedChunk> toChunks(List<KnowledgeDocument> documents) {
        List<IndexedChunk> out = new ArrayList<>();
        for (KnowledgeDocument doc : documents) {
            out.addAll(toChunks(doc));
        }
        return out;
    }

    private static String buildPassageText(KnowledgeDocument doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rule: ").append(nullToEmpty(doc.getRuleTitle())).append("\n");
        sb.append(nullToEmpty(doc.getRule())).append("\n\n");
        sb.append("Explanation: ").append(nullToEmpty(doc.getExplanation())).append("\n");
        if (doc.getCorrectExamples() != null && !doc.getCorrectExamples().isEmpty()) {
            sb.append("\nCorrect examples:\n- ")
                    .append(doc.getCorrectExamples().stream().collect(Collectors.joining("\n- ")));
        }
        if (doc.getIncorrectExamples() != null && !doc.getIncorrectExamples().isEmpty()) {
            sb.append("\n\nIncorrect examples:\n- ")
                    .append(doc.getIncorrectExamples().stream().collect(Collectors.joining("\n- ")));
        }
        if (doc.getExerciseHint() != null && !doc.getExerciseHint().isBlank()) {
            sb.append("\n\nPractice idea: ").append(doc.getExerciseHint().trim());
        }
        return sb.toString().trim();
    }

    private static String buildSearchableText(KnowledgeDocument doc, String levels, String category) {
        StringBuilder sb = new StringBuilder();
        sb.append(levels).append(' ').append(category).append(' ');
        sb.append(nullToEmpty(doc.getRuleTitle())).append(' ');
        sb.append(nullToEmpty(doc.getRule())).append(' ');
        sb.append(nullToEmpty(doc.getExplanation())).append(' ');
        if (doc.getKeywords() != null) {
            sb.append(String.join(" ", doc.getKeywords()));
        }
        if (doc.getCorrectExamples() != null) {
            sb.append(' ').append(String.join(" ", doc.getCorrectExamples()));
        }
        if (doc.getIncorrectExamples() != null) {
            sb.append(' ').append(String.join(" ", doc.getIncorrectExamples()));
        }
        return sb.toString().trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
