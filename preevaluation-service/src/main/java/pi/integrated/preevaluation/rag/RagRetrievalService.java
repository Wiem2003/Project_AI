package pi.integrated.preevaluation.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pi.integrated.preevaluation.config.PreevaluationRagProperties;
import pi.integrated.preevaluation.rag.model.IndexedChunk;
import pi.integrated.preevaluation.rag.model.RetrievedPassage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final PreevaluationRagProperties ragProperties;
    private final RagVectorIndex ragVectorIndex;
    private final GroqEmbeddingClient groqEmbeddingClient;
    private final LocalHashEmbeddingService localHashEmbeddingService;

    public List<RetrievedPassage> retrieve(String apiKey, RagQueryContext context) {
        if (!ragProperties.isEnabled() || !ragVectorIndex.isReady()) {
            return List.of();
        }

        float[] queryVector = buildQueryVector(apiKey, context);
        String level = context.studentLevel() == null ? "" : context.studentLevel().trim().toUpperCase(Locale.ROOT);
        String category = context.category() == null ? "" : context.category().trim().toUpperCase(Locale.ROOT);

        List<Scored> scored = rankChunks(queryVector, context, level, category, true);
        if (scored.isEmpty() && !category.isBlank()) {
            scored = rankChunks(queryVector, context, level, category, false);
        }

        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        int topK = Math.max(1, ragProperties.getTopK());
        List<RetrievedPassage> out = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            IndexedChunk c = scored.get(i).chunk();
            out.add(RetrievedPassage.builder()
                    .documentId(c.getDocumentId())
                    .ruleTitle(c.getRuleTitle())
                    .passageText(c.getPassageText())
                    .score(scored.get(i).score())
                    .sources(c.getSources())
                    .build());
        }
        return out;
    }

    private float[] buildQueryVector(String apiKey, RagQueryContext context) {
        String query = context.combinedQueryText();
        if (apiKey != null && !apiKey.isBlank()) {
            List<float[]> vectors = groqEmbeddingClient.embedTexts(apiKey.trim(), List.of(query));
            if (!vectors.isEmpty()) {
                return localHashEmbeddingService.copy(vectors.get(0));
            }
        }
        return localHashEmbeddingService.embed(query);
    }

    private List<Scored> rankChunks(float[] queryVector, RagQueryContext context, String level,
                                    String category, boolean enforceCategory) {
        List<Scored> scored = new ArrayList<>();
        for (IndexedChunk chunk : ragVectorIndex.snapshot()) {
            if (!levelMatches(level, chunk.getLevelScope())) {
                continue;
            }
            if (enforceCategory && !category.isBlank() && chunk.getCategory() != null
                    && !category.equalsIgnoreCase(chunk.getCategory())) {
                continue;
            }
            double sim = LocalHashEmbeddingService.cosineSimilarity(queryVector, chunk.getEmbedding());
            sim += keywordBoost(context.combinedQueryText(), chunk.getSearchableText());
            if (sim >= ragProperties.getMinScore()) {
                scored.add(new Scored(chunk, sim));
            }
        }
        return scored;
    }

    private static double keywordBoost(String query, String searchable) {
        if (query == null || searchable == null) {
            return 0.0;
        }
        String q = query.toLowerCase(Locale.ROOT);
        String s = searchable.toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String token : q.split("\\s+")) {
            if (token.length() < 3) {
                continue;
            }
            if (s.contains(token)) {
                hits++;
            }
        }
        return Math.min(0.15, hits * 0.03);
    }

    private static boolean levelMatches(String studentLevel, String levelScope) {
        if (studentLevel == null || studentLevel.isBlank()) {
            return true;
        }
        if (levelScope == null || levelScope.isBlank()) {
            return true;
        }
        for (String part : levelScope.split(",")) {
            if (studentLevel.equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private record Scored(IndexedChunk chunk, double score) {
    }
}
