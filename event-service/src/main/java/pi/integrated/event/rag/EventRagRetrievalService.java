package pi.integrated.event.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pi.integrated.event.rag.model.EventIndexedChunk;
import pi.integrated.event.rag.model.EventRetrievedPassage;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EventRagRetrievalService {

    private final EventVectorIndex vectorIndex;
    private final LocalHashEmbeddingService embeddingService;

    @Value("${rag.top-k:5}")
    private int topK;

    @Value("${rag.min-score:0.08}")
    private double minScore;

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}']+");

    /*
     * Mots courants à ignorer pendant la recherche.
     * Chaque élément doit être unique, car Set.of() refuse les doublons.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "i", "me", "my", "the", "a", "an", "and", "or", "to",
            "for", "of", "in", "on", "with", "is", "are", "am",
            "be", "it", "this", "that", "do", "does", "can",
            "could", "would", "should", "want", "like", "need",
            "please", "how", "what", "which", "who", "you",
            "your", "we", "they", "their", "he", "she",
            "je", "j", "veux", "voudrais", "mon", "ma", "mes",
            "le", "la", "les", "un", "une", "des", "de", "du",
            "et", "pour", "avec", "dans", "sur", "est", "sont",
            "comment", "quel", "quelle", "quels", "quelles",
            "moi", "qui", "quoi", "au", "aux", "en",
            "d", "l", "que", "pas", "plus", "faire", "apprendre"
    );

    /*
     * Groupes de synonymes pour rapprocher les formulations
     * qui expriment un objectif similaire.
     */
    private static final Map<String, Set<String>> SYNONYMS = Map.of(
            "speak", Set.of(
                    "speak", "speaking", "spoken", "talk", "talking",
                    "conversation", "oral", "fluency", "fluent",
                    "fluently", "pronunciation", "debate", "discussion"
            ),
            "grammar", Set.of(
                    "grammar", "grammatical", "grammaire",
                    "tense", "tenses", "verb", "verbs",
                    "conjugation", "syntax", "sentence"
            ),
            "writing", Set.of(
                    "writing", "write", "written", "essay",
                    "essays", "composition", "story", "stories"
            ),
            "listening", Set.of(
                    "listening", "listen", "audio", "hearing",
                    "understanding", "comprehension"
            ),
            "vocabulary", Set.of(
                    "vocabulary", "vocab", "words", "word",
                    "phrases", "expressions", "lexicon", "vocabulaire"
            ),
            "interview", Set.of(
                    "interview", "interviews", "recruiter",
                    "career", "job", "employment", "professional"
            ),
            "ielts", Set.of(
                    "ielts", "toefl", "exam", "examination",
                    "academic", "band", "test"
            ),
            "business", Set.of(
                    "business", "negotiation", "negotiating",
                    "meeting", "pitch", "pitching", "startup"
            )
    );

    /**
     * Recherche les passages les plus pertinents
     * pour une requête et une catégorie données.
     */
    public List<EventRetrievedPassage> retrieve(
            String query,
            String category) {

        if (!vectorIndex.isReady()
                || query == null
                || query.isBlank()) {
            return List.of();
        }

        List<EventIndexedChunk> chunks = vectorIndex.snapshot();

        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        float[] queryVector = embeddingService.embed(query);

        Set<String> queryTokens = tokenize(query);

        if (queryTokens.isEmpty()) {
            return List.of();
        }

        Set<String> expandedQueryTokens = expandTokens(queryTokens);

        String normalizedCategory = category == null
                ? ""
                : category.trim().toUpperCase(Locale.ROOT);

        List<ScoredChunk> scored = new ArrayList<>();

        for (EventIndexedChunk chunk : chunks) {

            if (chunk == null) {
                continue;
            }

            // Respecter le filtre de catégorie si nécessaire.
            if (!normalizedCategory.isBlank()
                    && !normalizedCategory.equalsIgnoreCase(
                    nullToEmpty(chunk.getCategory()).trim())) {
                continue;
            }

            String title = nullToEmpty(chunk.getTitle());

            String searchableText =
                    nullToEmpty(chunk.getSearchableText());

            String searchable = title + " " + searchableText;

            Set<String> documentTokens = tokenize(searchable);

            if (documentTokens.isEmpty()) {
                continue;
            }

            Set<String> titleTokens = tokenize(title);

            // Similarité entre les embeddings de la requête et du document.
            double cosineScore =
                    LocalHashEmbeddingService.cosineSimilarity(
                            queryVector,
                            chunk.getEmbedding()
                    );

            // Correspondance des mots importants.
            double lexicalScore = lexicalScore(
                    expandedQueryTokens,
                    documentTokens
            );

            // Donner plus d'importance aux mots présents dans le titre.
            double titleScore = lexicalScore(
                    expandedQueryTokens,
                    titleTokens
            );

            // Correspondance des groupes de synonymes.
            double intentScore = intentScore(
                    expandedQueryTokens,
                    documentTokens
            );

            // Correspondance de la requête complète.
            double phraseScore = exactPhraseScore(
                    query,
                    title,
                    searchableText
            );

            // Correspondance avec les compétences du document.
            double skillScore = skillScore(
                    expandedQueryTokens,
                    chunk.getSkills()
            );

            /*
             * Score hybride :
             * 20 % similarité cosinus
             * 35 % correspondance lexicale
             * 20 % correspondance du titre
             * 15 % intention et synonymes
             *  5 % phrase exacte
             *  5 % compétences
             */
            double score =
                    (0.20 * normalizeCosine(cosineScore))
                            + (0.35 * lexicalScore)
                            + (0.20 * titleScore)
                            + (0.15 * intentScore)
                            + (0.05 * phraseScore)
                            + (0.05 * skillScore);

            // Garantir un score compris entre 0 et 1.
            score = Math.max(0.0, Math.min(1.0, score));

            // Ne conserver que les résultats suffisamment pertinents.
            if (score >= minScore) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }

        // Trier les résultats du score le plus élevé au plus faible.
        scored.sort(
                Comparator.comparingDouble(ScoredChunk::score)
                        .reversed()
        );

        // Éviter de retourner plusieurs passages du même document.
        Set<String> seenDocumentIds = new HashSet<>();

        List<EventRetrievedPassage> results = new ArrayList<>();

        int limit = Math.max(1, topK);

        for (ScoredChunk item : scored) {

            EventIndexedChunk chunk = item.chunk();

            String documentId = nullToEmpty(chunk.getDocumentId());

            if (!seenDocumentIds.add(documentId)) {
                continue;
            }

            EventRetrievedPassage passage =
                    EventRetrievedPassage.builder()
                            .documentId(documentId)
                            .title(chunk.getTitle())
                            .category(chunk.getCategory())
                            .passageText(chunk.getPassageText())
                            .score(item.score())
                            .build();

            results.add(passage);

            if (results.size() >= limit) {
                break;
            }
        }

        return results;
    }

    /**
     * Normalise la similarité cosinus entre 0 et 1.
     */
    private static double normalizeCosine(double cosine) {
        return Math.max(0.0, Math.min(1.0, cosine));
    }

    /**
     * Calcule la proportion des tokens de la requête
     * présents dans le document.
     */
    private static double lexicalScore(
            Set<String> queryTokens,
            Set<String> documentTokens) {

        if (queryTokens.isEmpty() || documentTokens.isEmpty()) {
            return 0.0;
        }

        long matches = queryTokens.stream()
                .filter(documentTokens::contains)
                .count();

        return Math.min(
                1.0,
                (double) matches / queryTokens.size()
        );
    }

    /**
     * Calcule la correspondance entre les groupes
     * de synonymes de la requête et ceux du document.
     */
    private static double intentScore(
            Set<String> queryTokens,
            Set<String> documentTokens) {

        if (queryTokens.isEmpty() || documentTokens.isEmpty()) {
            return 0.0;
        }

        int matchedGroups = 0;
        int relevantGroups = 0;

        for (Set<String> synonyms : SYNONYMS.values()) {

            boolean queryMatchesGroup = queryTokens.stream()
                    .anyMatch(synonyms::contains);

            if (!queryMatchesGroup) {
                continue;
            }

            relevantGroups++;

            boolean documentMatchesGroup = documentTokens.stream()
                    .anyMatch(synonyms::contains);

            if (documentMatchesGroup) {
                matchedGroups++;
            }
        }

        if (relevantGroups == 0) {
            return 0.0;
        }

        return (double) matchedGroups / relevantGroups;
    }

    /**
     * Vérifie si la requête complète apparaît dans le titre
     * ou dans le texte indexé.
     */
    private static double exactPhraseScore(
            String query,
            String title,
            String searchableText) {

        Set<String> queryTokens = tokenize(query);

        if (queryTokens.size() < 2) {
            return 0.0;
        }

        String normalizedQuery = normalizeText(query);
        String normalizedTitle = normalizeText(title);
        String normalizedSearchable = normalizeText(searchableText);

        if (normalizedTitle.contains(normalizedQuery)) {
            return 1.0;
        }

        if (normalizedSearchable.contains(normalizedQuery)) {
            return 0.8;
        }

        return 0.0;
    }

    /**
     * Calcule la correspondance entre la requête
     * et les compétences associées au document.
     */
    private static double skillScore(
            Set<String> queryTokens,
            List<String> skills) {

        if (queryTokens.isEmpty()
                || skills == null
                || skills.isEmpty()) {
            return 0.0;
        }

        Set<String> skillTokens = new HashSet<>();

        for (String skill : skills) {
            skillTokens.addAll(tokenize(skill));
        }

        return lexicalScore(queryTokens, skillTokens);
    }

    /**
     * Étend les tokens avec les synonymes connus.
     */
    private static Set<String> expandTokens(Set<String> tokens) {

        Set<String> expanded = new HashSet<>(tokens);

        for (String token : tokens) {

            for (Set<String> synonyms : SYNONYMS.values()) {

                if (synonyms.contains(token)) {
                    expanded.addAll(synonyms);
                }
            }
        }

        return expanded;
    }

    /**
     * Découpe le texte en tokens, normalise les caractères
     * et supprime les mots courants.
     */
    private static Set<String> tokenize(String text) {

        if (text == null || text.isBlank()) {
            return Set.of();
        }

        String normalized = normalizeText(text);

        Set<String> tokens = new HashSet<>();

        Matcher matcher = TOKEN_PATTERN.matcher(normalized);

        while (matcher.find()) {

            String token = matcher.group();

            if (token.length() >= 2
                    && !STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }

        return tokens;
    }

    /**
     * Convertit le texte en minuscules, supprime les accents
     * et normalise les espaces.
     */
    private static String normalizeText(String text) {

        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(
                text.toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD
        );

        return normalized
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Évite les valeurs null dans les traitements textuels.
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Associe un passage indexé à son score de pertinence.
     */
    private record ScoredChunk(
            EventIndexedChunk chunk,
            double score) {
    }
}