package pi.integrated.event.rag;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pi.integrated.event.rag.model.EventIndexedChunk;
import pi.integrated.event.rag.model.EventKnowledgeDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Exécuté au démarrage de l'application.
 * Charge la base de connaissances et crée l'index vectoriel.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class EventRagIndexingRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EventRagIndexingRunner.class);

    private final EventKnowledgeBaseLoader knowledgeBaseLoader;
    private final DatabaseEventLoader databaseEventLoader;
    private final EventChunkBuilder chunkBuilder;
    private final LocalHashEmbeddingService embeddingService;
    private final EventVectorIndex vectorIndex;

    @Value("${rag.enabled:true}")
    private boolean ragEnabled;

    @Value("${rag.index-on-startup:true}")
    private boolean indexOnStartup;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!ragEnabled || !indexOnStartup) {
            log.info("RAG indexing skipped (enabled={}, indexOnStartup={})", ragEnabled, indexOnStartup);
            return;
        }

        log.info("Starting RAG indexing...");

        // 1. Charger les documents de la base de connaissances JSON
        List<EventKnowledgeDocument> documents = knowledgeBaseLoader.loadAll();
        log.info("Loaded {} knowledge documents from JSON", documents.size());

        // 2. Charger les evenements reels depuis MySQL
        List<EventKnowledgeDocument> dbDocuments = databaseEventLoader.loadFromDatabase();
        documents.addAll(dbDocuments);
        log.info("Total documents (JSON + MySQL): {}", documents.size());

        // 2. Convertir en chunks
        List<EventIndexedChunk> rawChunks = chunkBuilder.toChunks(documents);
        if (rawChunks.isEmpty()) {
            log.warn("RAG knowledge base is empty — no event documents loaded.");
            return;
        }

        // 3. Calculer les embeddings pour chaque chunk
        List<EventIndexedChunk> indexed = new ArrayList<>(rawChunks.size());
        for (EventIndexedChunk chunk : rawChunks) {
            float[] embedding = embeddingService.embed(chunk.getSearchableText());
            indexed.add(EventIndexedChunk.builder()
                    .chunkId(chunk.getChunkId())
                    .documentId(chunk.getDocumentId())
                    .title(chunk.getTitle())
                    .category(chunk.getCategory())
                    .searchableText(chunk.getSearchableText())
                    .passageText(chunk.getPassageText())
                    .skills(chunk.getSkills())
                    .embedding(embeddingService.copy(embedding))
                    .build());
        }

        // 4. Stocker dans l'index vectoriel
        vectorIndex.replaceAll(indexed);
        log.info("✅ RAG index ready: {} chunks indexed with local hash embeddings", indexed.size());
    }
}
