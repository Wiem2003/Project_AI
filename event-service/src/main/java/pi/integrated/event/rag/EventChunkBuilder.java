package pi.integrated.event.rag;

import org.springframework.stereotype.Component;
import pi.integrated.event.rag.model.EventIndexedChunk;
import pi.integrated.event.rag.model.EventKnowledgeDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Transforme un EventKnowledgeDocument en chunks indexables.
 * Chaque document produit un seul chunk contenant tout le texte pertinent.
 */
@Component
public class EventChunkBuilder {

    /**
     * Convertit un document en chunk indexable.
     */
    public EventIndexedChunk toChunk(EventKnowledgeDocument doc) {
        String passage = buildPassageText(doc);
        String searchable = buildSearchableText(doc);

        return EventIndexedChunk.builder()
                .chunkId(doc.getId() + "#0")
                .documentId(doc.getId())
                .title(doc.getTitle())
                .category(doc.getCategory() == null ? "" : doc.getCategory().trim().toUpperCase())
                .searchableText(searchable)
                .passageText(passage)
                .skills(doc.getSkills())
                .embedding(null) // sera rempli lors de l'indexation
                .build();
    }

    /**
     * Convertit une liste de documents en chunks.
     */
    public List<EventIndexedChunk> toChunks(List<EventKnowledgeDocument> documents) {
        List<EventIndexedChunk> out = new ArrayList<>();
        for (EventKnowledgeDocument doc : documents) {
            out.add(toChunk(doc));
        }
        return out;
    }

    /**
     * Construit le texte qui sera envoyé au LLM comme contexte.
     */
    private static String buildPassageText(EventKnowledgeDocument doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("Event: ").append(nullToEmpty(doc.getTitle())).append("\n");
        sb.append("Category: ").append(nullToEmpty(doc.getCategory())).append("\n");
        sb.append("Description: ").append(nullToEmpty(doc.getDescription())).append("\n");
        sb.append("Target Audience: ").append(nullToEmpty(doc.getTargetAudience())).append("\n");
        sb.append("Duration: ").append(nullToEmpty(doc.getDuration())).append("\n");
        sb.append("Format: ").append(nullToEmpty(doc.getFormat())).append("\n");
        if (doc.getSkills() != null && !doc.getSkills().isEmpty()) {
            sb.append("Skills developed: ").append(String.join(", ", doc.getSkills())).append("\n");
        }
        sb.append("Participant Feedback: ").append(nullToEmpty(doc.getTypicalFeedback()));
        return sb.toString().trim();
    }

    /**
     * Construit le texte utilisé pour la recherche vectorielle.
     * Inclut tous les mots-clés et informations pertinentes.
     */
    private static String buildSearchableText(EventKnowledgeDocument doc) {
        StringBuilder sb = new StringBuilder();
        sb.append(nullToEmpty(doc.getCategory())).append(' ');
        sb.append(nullToEmpty(doc.getTitle())).append(' ');
        sb.append(nullToEmpty(doc.getDescription())).append(' ');
        sb.append(nullToEmpty(doc.getTargetAudience())).append(' ');
        sb.append(nullToEmpty(doc.getTypicalFeedback())).append(' ');
        if (doc.getSkills() != null) {
            sb.append(String.join(" ", doc.getSkills())).append(' ');
        }
        if (doc.getKeywords() != null) {
            sb.append(String.join(" ", doc.getKeywords()));
        }
        return sb.toString().trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
