package pi.integrated.event.rag;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pi.integrated.event.Repository.EventRepository;
import pi.integrated.event.entity.Event;
import pi.integrated.event.rag.model.EventKnowledgeDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Charge les evenements reels depuis la base de donnees MySQL
 * et les convertit en documents de connaissances pour le RAG.
 *
 * Cela permet au RAG d'utiliser des DONNEES REELLES en plus du fichier JSON statique.
 */
@Component
@RequiredArgsConstructor
public class DatabaseEventLoader {

    private static final Logger log = LoggerFactory.getLogger(DatabaseEventLoader.class);

    private final EventRepository eventRepository;

    /**
     * Charge tous les evenements de MySQL et les convertit en EventKnowledgeDocument.
     */
    public List<EventKnowledgeDocument> loadFromDatabase() {
        List<Event> events;
        try {
            events = eventRepository.findAll();
        } catch (Exception e) {
            log.warn("Could not load events from database: {}", e.getMessage());
            return List.of();
        }

        List<EventKnowledgeDocument> documents = new ArrayList<>();
        for (Event event : events) {
            EventKnowledgeDocument doc = EventKnowledgeDocument.builder()
                    .id("db-event-" + event.getId())
                    .category(event.getCategory() != null ? event.getCategory().name() : "UNKNOWN")
                    .title(event.getName())
                    .description(event.getDescription())
                    .targetAudience("All participants")
                    .skills(inferSkills(event))
                    .duration("Scheduled event")
                    .format(event.getLocation() != null ? "At " + event.getLocation() : "TBD")
                    .typicalFeedback("Real event from the platform")
                    .keywords(buildKeywords(event))
                    .build();
            documents.add(doc);
        }

        log.info("Loaded {} events from MySQL database for RAG", documents.size());
        return documents;
    }

    /**
     * Infere les competences developpees a partir de la categorie de l'evenement.
     */
    private List<String> inferSkills(Event event) {
        if (event.getCategory() == null) {
            return List.of("English learning");
        }
        return switch (event.getCategory()) {
            case WORKSHOP -> List.of("practical skills", "hands-on learning", "group work");
            case WEBINAR -> List.of("online learning", "self-paced", "remote skills");
            case CONFERENCE -> List.of("networking", "knowledge sharing", "industry insights");
            case TRAINING -> List.of("structured learning", "certification", "professional development");
            case EXAM_PREPARATION -> List.of("exam skills", "test strategies", "time management");
            case BUSINESS_ENGLISH -> List.of("business communication", "professional English", "career skills");
            case CULTURAL_EVENT -> List.of("cultural awareness", "social English", "fun learning");
        };
    }

    /**
     * Construit une liste de mots-cles a partir des donnees de l'evenement.
     */
    private List<String> buildKeywords(Event event) {
        List<String> keywords = new ArrayList<>();
        if (event.getName() != null) {
            keywords.addAll(List.of(event.getName().toLowerCase().split("\\s+")));
        }
        if (event.getCategory() != null) {
            keywords.add(event.getCategory().name().toLowerCase().replace("_", " "));
        }
        if (event.getLocation() != null) {
            keywords.add(event.getLocation().toLowerCase());
        }
        if (event.getStatus() != null) {
            keywords.add(event.getStatus().name().toLowerCase());
        }
        return keywords;
    }
}
