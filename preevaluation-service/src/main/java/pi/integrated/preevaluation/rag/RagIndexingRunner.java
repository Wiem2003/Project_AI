package pi.integrated.preevaluation.rag;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pi.integrated.preevaluation.config.PreevaluationAiProperties;
import pi.integrated.preevaluation.config.PreevaluationRagProperties;
import pi.integrated.preevaluation.rag.model.IndexedChunk;
import pi.integrated.preevaluation.rag.model.KnowledgeDocument;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class RagIndexingRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagIndexingRunner.class);

    private final PreevaluationRagProperties ragProperties;
    private final PreevaluationAiProperties aiProperties;
    private final KnowledgeBaseLoader knowledgeBaseLoader;
    private final PedagogicalChunkBuilder chunkBuilder;
    private final GroqEmbeddingClient groqEmbeddingClient;
    private final RagVectorIndex ragVectorIndex;
    private final LocalHashEmbeddingService localHashEmbeddingService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!ragProperties.isEnabled() || !ragProperties.isIndexOnStartup()) {
            log.info("RAG indexing skipped (enabled={}, indexOnStartup={})",
                    ragProperties.isEnabled(), ragProperties.isIndexOnStartup());
            return;
        }

        List<KnowledgeDocument> documents = knowledgeBaseLoader.loadAll();
        List<IndexedChunk> rawChunks = chunkBuilder.toChunks(documents);
        if (rawChunks.isEmpty()) {
            log.warn("RAG knowledge base is empty — no pedagogical documents loaded.");
            return;
        }

        String apiKey = aiProperties.getApiKey();
        boolean useGroq = apiKey != null && !apiKey.isBlank()
                && groqEmbeddingClient.isGroqEmbeddingsAvailable(apiKey.trim());

        List<IndexedChunk> indexed = new ArrayList<>(rawChunks.size());
        int batchSize = Math.max(1, ragProperties.getEmbeddingBatchSize());
        for (int i = 0; i < rawChunks.size(); i += batchSize) {
            int end = Math.min(rawChunks.size(), i + batchSize);
            List<IndexedChunk> batch = rawChunks.subList(i, end);
            List<String> texts = batch.stream().map(IndexedChunk::getSearchableText).toList();
            List<float[]> vectors;
            if (useGroq) {
                vectors = groqEmbeddingClient.embedTexts(apiKey.trim(), texts);
            } else {
                vectors = texts.stream().map(localHashEmbeddingService::embed).toList();
            }
            for (int j = 0; j < batch.size(); j++) {
                IndexedChunk c = batch.get(j);
                float[] emb = j < vectors.size() ? vectors.get(j) : localHashEmbeddingService.embed(c.getSearchableText());
                indexed.add(IndexedChunk.builder()
                        .chunkId(c.getChunkId())
                        .documentId(c.getDocumentId())
                        .ruleTitle(c.getRuleTitle())
                        .levelScope(c.getLevelScope())
                        .category(c.getCategory())
                        .searchableText(c.getSearchableText())
                        .passageText(c.getPassageText())
                        .sources(c.getSources())
                        .embedding(localHashEmbeddingService.copy(emb))
                        .build());
            }
        }

        ragVectorIndex.replaceAll(indexed);
        log.info("RAG index ready: {} chunks, embeddings via {}", indexed.size(),
                useGroq ? "Groq API" : "local hash vectors");
    }
}
