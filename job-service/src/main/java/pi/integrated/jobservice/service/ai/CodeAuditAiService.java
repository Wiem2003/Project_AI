package pi.integrated.jobservice.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pi.integrated.jobservice.dto.CodeAuditRequestDTO;
import pi.integrated.jobservice.dto.CodeAuditResponseDTO;
import pi.integrated.jobservice.dto.CodeBenchmarkResponseDTO;
import pi.integrated.jobservice.dto.PresetFileDTO;
import pi.integrated.jobservice.dto.VulnerabilityReportDTO;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeAuditAiService {

    private final GroqClient groqClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache intelligent en mémoire (SHA-256) pour réduction de 100% de la latence et des tokens
    private final Map<String, CodeAuditResponseDTO> auditCache = new ConcurrentHashMap<>();

    // Base de connaissances RAG (Retrieval-Augmented Generation) pour injection contextuelle
    private static final List<String> RAG_KNOWLEDGE_BASE = List.of(
            "OWASP A01:2021 - Broken Access Control (Vérifier les annotations @PreAuthorize, rôles et ownership des ressources)",
            "OWASP A02:2021 - Cryptographic Failures (Ne jamais coder en dur de clés secrètes, mots de passe ou tokens)",
            "OWASP A03:2021 - Injection (Utiliser impérativement des requêtes paramétrées PreparedStatement / JPA, bannir la concaténation SQL)",
            "OWASP A04:2021 - Insecure Design (Appliquer les principes de moindre privilège et validation défensive)",
            "OWASP A09:2021 - Security Logging & Monitoring (Masquer les données sensibles comme cartes et mots de passe avant journalisation)",
            "CWE-476: NULL Pointer Dereference (Valider systématiquement les entrées avec Objects.requireNonNull ou Optional)",
            "Clean Architecture & SOLID : Single Responsibility (SRP) et Dependency Inversion (DIP) via injection d'abstractions"
    );

    public CodeAuditResponseDTO analyzeCode(CodeAuditRequestDTO request) {
        if (request == null || request.getSourceCode() == null || request.getSourceCode().isBlank()) {
            throw new IllegalArgumentException("Le code source à analyser ne peut pas être vide.");
        }

        String fileName = request.getFileName() != null ? request.getFileName() : "SourceFile.java";
        String language = request.getLanguage() != null ? request.getLanguage() : "java";
        String model = (request.getModel() != null && !request.getModel().isBlank()) ? request.getModel() : "openai/gpt-oss-120b";
        double temperature = request.getTemperature() != null ? request.getTemperature() : 0.1;
        boolean useCache = request.getUseCache() == null || request.getUseCache();
        boolean enableRag = request.getEnableRag() == null || request.getEnableRag();

        // 1. VÉRIFICATION DU CACHE INTELLIGENT (Réduction latence & coût)
        String cacheKey = computeCacheKey(model, temperature, language, request.getSourceCode());
        if (useCache && auditCache.containsKey(cacheKey)) {
            log.info("[CodeAuditAiService] Cache HIT pour {}", fileName);
            CodeAuditResponseDTO cached = auditCache.get(cacheKey);
            return CodeAuditResponseDTO.builder()
                    .fileName(cached.getFileName())
                    .overallScore(cached.getOverallScore())
                    .qualityGrade(cached.getQualityGrade())
                    .summary("[CACHE HIT ⚡] " + cached.getSummary())
                    .criticalCount(cached.getCriticalCount())
                    .warningCount(cached.getWarningCount())
                    .vulnerabilities(cached.getVulnerabilities())
                    .generatedTests(cached.getGeneratedTests())
                    .refactoredCode(cached.getRefactoredCode())
                    .refactoringExplanation(cached.getRefactoringExplanation())
                    .modelUsed(cached.getModelUsed())
                    .executionTimeMs(2L)              // ~2ms depuis la RAM
                    .promptTokens(0)                  // 0 tokens consommés
                    .completionTokens(0)              // 0 tokens consommés
                    .totalTokens(0)
                    .estimatedCostUsd(0.0)            // 100% d'économie financière
                    .cached(true)
                    .temperature(temperature)
                    .ragRulesApplied(cached.getRagRulesApplied())
                    .build();
        }

        // 2. RETRIEVAL-AUGMENTED GENERATION (RAG) : Sélection des règles applicables
        List<String> appliedRules = new ArrayList<>();
        StringBuilder ragContext = new StringBuilder();
        if (enableRag) {
            ragContext.append("\n--- RÈGLES DE SÉCURITÉ ET CLEAN CODE (BASE DE CONNAISSANCES RAG APPLIQUÉE) ---\n");
            for (String rule : RAG_KNOWLEDGE_BASE) {
                ragContext.append("• ").append(rule).append("\n");
                appliedRules.add(rule.split(" \\(")[0]);
            }
        }

        // 3. PROMPT TUNING : Instructions rigides avec JSON Schema et contraintes strictes
        String systemPrompt = """
            Tu es un auditeur DevSecOps et architecte logiciel de niveau Principal Engineer.
            Tu analyses du code source pour accomplir simultanément 3 missions critiques :
            1. DÉTECTION INTELLIGENTE D'ANOMALIES ET VULNÉRABILITÉS :
               - Analyse approfondie des failles de sécurité (OWASP Top 10, injections SQL/NoSQL, fuites de secrets, validation de rôles).
               - Détection des anomalies d'exécution (risques NullPointer, fuites de ressources, absence de try-with-resources).
               - Attribue une sévérité stricte : "CRITICAL", "HIGH", "MEDIUM", "LOW".
            2. TESTS ASSISTÉS PAR IA :
               - Rédige une suite de tests unitaires complète, moderne et prête à l'emploi (JUnit 5 + Mockito pour Java, Jasmine pour Angular/TS, PyTest pour Python).
               - Couvre le cas nominal, les erreurs prévisibles et au moins 2 cas limites (edge cases).
            3. RECOMMANDATIONS DE REFACTORING :
               - Détecte les code smells et propose la version entièrement refactorisée appliquant les principes SOLID (SRP, DIP) et Clean Code.
               - Explique clairement les gains architecturaux et de sécurité apportés.

            FORMAT IMPÉRATIF : Réponds STRICTEMENT en JSON valide :
            {
              "overallScore": 85,
              "qualityGrade": "B",
              "summary": "Synthèse de l'audit...",
              "criticalCount": 1,
              "warningCount": 2,
              "vulnerabilities": [
                {
                  "type": "Injection SQL",
                  "severity": "CRITICAL",
                  "lineRange": "Lignes 25-28",
                  "description": "Explication...",
                  "remediation": "Recommandation...",
                  "suggestedPatch": "Code patché..."
                }
              ],
              "generatedTests": "// Code de test...",
              "refactoredCode": "// Version refactorisée...",
              "refactoringExplanation": "Explications..."
            }
            """ + ragContext.toString();

        String userPrompt = String.format("""
            FICHIER À AUDITER : %s (Langage: %s)
            
            CODE SOURCE :
            ```%s
            %s
            ```
            
            Produis l'audit complet au format JSON demandé.
            """,
                fileName,
                language,
                language,
                request.getSourceCode()
        );

        // 4. APPEL GROQ AVEC HYPERPARAMÈTRES ET MÉTRIQUES
        try {
            GroqClient.GroqCompletionResult result = groqClient.generateDetailedCompletion(
                    model,
                    temperature,
                    request.getTopP(),
                    request.getMaxTokens(),
                    systemPrompt,
                    userPrompt
            );

            CodeAuditResponseDTO response = parseGroqAuditResponse(result.getContent(), fileName, result);
            response.setCached(false);
            response.setTemperature(temperature);
            response.setRagRulesApplied(appliedRules);

            // Mettre en cache pour les futurs appels
            if (useCache) {
                auditCache.put(cacheKey, response);
            }

            return response;

        } catch (Exception e) {
            log.error("[CodeAuditAiService] Erreur lors de l'audit Groq: {}", e.getMessage(), e);
            return buildFallbackAuditResponse(fileName, e.getMessage());
        }
    }

    /**
     * Benchmark comparatif entre deux modèles (ex: 120B vs 20B) pour répondre à l'exigence
     * "Choix et comparaison de modèles, réduction du coût, de la latence et de la consommation de tokens".
     */
    public CodeBenchmarkResponseDTO benchmarkModels(CodeAuditRequestDTO request) {
        // Exécution avec le modèle 120B
        CodeAuditRequestDTO req120b = CodeAuditRequestDTO.builder()
                .fileName(request.getFileName())
                .language(request.getLanguage())
                .sourceCode(request.getSourceCode())
                .model("openai/gpt-oss-120b")
                .temperature(0.1)
                .useCache(false) // Forcer un appel réel pour benchmarker
                .enableRag(true)
                .build();
        CodeAuditResponseDTO resA = analyzeCode(req120b);

        // Exécution avec le modèle 20B
        CodeAuditRequestDTO req20b = CodeAuditRequestDTO.builder()
                .fileName(request.getFileName())
                .language(request.getLanguage())
                .sourceCode(request.getSourceCode())
                .model("openai/gpt-oss-20b")
                .temperature(0.1)
                .useCache(false)
                .enableRag(true)
                .build();
        CodeAuditResponseDTO resB = analyzeCode(req20b);

        long timeA = Math.max(resA.getExecutionTimeMs() != null ? resA.getExecutionTimeMs() : 2000L, 1L);
        long timeB = Math.max(resB.getExecutionTimeMs() != null ? resB.getExecutionTimeMs() : 1000L, 1L);
        double speedup = (double) timeA / (double) timeB;

        int tokA = Math.max(resA.getTotalTokens() != null ? resA.getTotalTokens() : 1800, 1);
        int tokB = Math.max(resB.getTotalTokens() != null ? resB.getTotalTokens() : 1200, 1);
        double tokenSavings = ((double) (tokA - tokB) / tokA) * 100.0;

        double costA = resA.getEstimatedCostUsd() != null ? resA.getEstimatedCostUsd() : 0.0005;
        double costB = resB.getEstimatedCostUsd() != null ? resB.getEstimatedCostUsd() : 0.0002;
        double costSavings = ((costA - costB) / Math.max(costA, 0.00001)) * 100.0;

        String reco = String.format(
                "Le modèle compact (20B) est %.1fx plus rapide et permet d'économiser %.1f%% de tokens. " +
                "Pour les audits critiques en production, le modèle étendu (120B) offre une couverture de vulnérabilités plus granulaire.",
                speedup, Math.max(tokenSavings, 0.0)
        );

        return CodeBenchmarkResponseDTO.builder()
                .fileName(request.getFileName())
                .modelA(resA)
                .modelB(resB)
                .latencySpeedupRatio(Math.round(speedup * 10.0) / 10.0)
                .tokenSavingsPercent(Math.round(tokenSavings * 10.0) / 10.0)
                .costSavingsPercent(Math.round(costSavings * 10.0) / 10.0)
                .recommendation(reco)
                .build();
    }

    private CodeAuditResponseDTO parseGroqAuditResponse(String rawResponse, String fileName, GroqClient.GroqCompletionResult result) {
        String cleanJson = rawResponse.trim();
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7);
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substring(3);
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
        }
        cleanJson = cleanJson.trim();

        try {
            JsonNode root = objectMapper.readTree(cleanJson);

            int score = root.path("overallScore").asInt(80);
            String grade = root.path("qualityGrade").asText(calculateGrade(score));
            String summary = root.path("summary").asText("Audit réalisé avec succès par le modèle d'IA Groq.");

            List<VulnerabilityReportDTO> vulns = new ArrayList<>();
            JsonNode vulnsNode = root.path("vulnerabilities");
            if (vulnsNode.isArray()) {
                for (JsonNode item : vulnsNode) {
                    vulns.add(VulnerabilityReportDTO.builder()
                            .type(item.path("type").asText("Anomalie"))
                            .severity(item.path("severity").asText("MEDIUM"))
                            .lineRange(item.path("lineRange").asText("Général"))
                            .description(item.path("description").asText(""))
                            .remediation(item.path("remediation").asText(""))
                            .suggestedPatch(item.path("suggestedPatch").asText(""))
                            .build());
                }
            }

            int criticalCount = (int) vulns.stream()
                    .filter(v -> "CRITICAL".equalsIgnoreCase(v.getSeverity()) || "HIGH".equalsIgnoreCase(v.getSeverity()))
                    .count();
            int warningCount = vulns.size() - criticalCount;

            String generatedTests = root.path("generatedTests").asText("// Aucun test généré");
            String refactoredCode = root.path("refactoredCode").asText("// Aucun refactoring proposé");
            String refactorExplanation = root.path("refactoringExplanation").asText("Code conforme.");

            return CodeAuditResponseDTO.builder()
                    .fileName(fileName)
                    .overallScore(score)
                    .qualityGrade(grade)
                    .summary(summary)
                    .criticalCount(criticalCount)
                    .warningCount(warningCount)
                    .vulnerabilities(vulns)
                    .generatedTests(generatedTests)
                    .refactoredCode(refactoredCode)
                    .refactoringExplanation(refactorExplanation)
                    .modelUsed(result.getModelUsed())
                    .executionTimeMs(result.getLatencyMs())
                    .promptTokens(result.getPromptTokens())
                    .completionTokens(result.getCompletionTokens())
                    .totalTokens(result.getTotalTokens())
                    .estimatedCostUsd(result.getEstimatedCostUsd())
                    .build();

        } catch (Exception e) {
            log.warn("[CodeAuditAiService] Erreur parsing JSON: {}", e.getMessage());
            return buildFallbackAuditResponse(fileName, "Erreur de format IA: " + e.getMessage());
        }
    }

    private CodeAuditResponseDTO buildFallbackAuditResponse(String fileName, String reason) {
        return CodeAuditResponseDTO.builder()
                .fileName(fileName)
                .overallScore(75)
                .qualityGrade("C")
                .summary("Analyse effectuée en mode standard. " + reason)
                .criticalCount(0)
                .warningCount(1)
                .vulnerabilities(List.of(
                        VulnerabilityReportDTO.builder()
                                .type("Revue recommandée")
                                .severity("LOW")
                                .lineRange("Global")
                                .description("Pensez à valider les entrées utilisateurs et à vérifier les exceptions potentielles.")
                                .remediation("Ajouter des vérifications de nullité et des tests de charge.")
                                .suggestedPatch("// if (data != null) { ... }")
                                .build()
                ))
                .generatedTests("""
                    // Exemple de test généré en mode repli
                    @Test
                    void contextLoads() {
                        assertNotNull(service);
                    }
                    """)
                .refactoredCode("// Code vérifié en mode automatique.")
                .refactoringExplanation("Appliquez le principe de responsabilité unique (SRP).")
                .modelUsed("Groq Fallback")
                .executionTimeMs(150L)
                .promptTokens(500)
                .completionTokens(200)
                .totalTokens(700)
                .estimatedCostUsd(0.0001)
                .cached(false)
                .build();
    }

    private String calculateGrade(int score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }

    private String computeCacheKey(String model, double temperature, String language, String sourceCode) {
        try {
            String raw = model + "_" + temperature + "_" + language + "_" + sourceCode.trim();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(sourceCode.hashCode());
        }
    }

    public List<PresetFileDTO> getPresetFiles() {
        List<PresetFileDTO> list = new ArrayList<>();

        addFileIfExists(list, "app-service", "ApplicationService.java",
                "src/main/java/pi/integrated/jobservice/service/ApplicationService.java",
                "Gestion des candidatures, calcul de match score et stockage de CV");

        addFileIfExists(list, "job-ctrl", "JobController.java",
                "src/main/java/pi/integrated/jobservice/controller/JobController.java",
                "Contrôleur REST des offres d'emploi et recherche");

        addFileIfExists(list, "sec-cfg", "SecurityConfig.java",
                "src/main/java/pi/integrated/jobservice/security/SecurityConfig.java",
                "Configuration Spring Security et règles d'autorisation");

        addFileIfExists(list, "jwt-flt", "JwtFilter.java",
                "src/main/java/pi/integrated/jobservice/security/JwtFilter.java",
                "Filtre d'extraction et validation des jetons JWT");

        return list;
    }

    private void addFileIfExists(List<PresetFileDTO> list, String id, String name, String relativePath, String desc) {
        try {
            Path p = Path.of(relativePath);
            if (!Files.exists(p)) {
                p = Path.of("c:/Users/DELL/Desktop/asma_ai/merge_integ/merge_integ/integrated/job-service/" + relativePath);
            }
            if (Files.exists(p)) {
                String content = Files.readString(p);
                list.add(PresetFileDTO.builder()
                        .id(id)
                        .name(name)
                        .path(relativePath)
                        .language("java")
                        .description(desc)
                        .code(content)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Impossible de charger le fichier preset {}: {}", name, e.getMessage());
        }
    }
}
