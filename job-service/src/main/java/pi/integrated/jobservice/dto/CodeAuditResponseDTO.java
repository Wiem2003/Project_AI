package pi.integrated.jobservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeAuditResponseDTO {
    private String fileName;
    private Integer overallScore;          // 0 à 100
    private String qualityGrade;           // "A", "B", "C", "D", "F"
    private String summary;                // Synthèse générale de l'audit
    private Integer criticalCount;
    private Integer warningCount;
    private List<VulnerabilityReportDTO> vulnerabilities;
    private String generatedTests;         // Code de test unitaire JUnit 5 / Mockito complet
    private String refactoredCode;         // Code source refactorisé (Clean Code)
    private String refactoringExplanation; // Explication des gains apportés par le refactoring
    private String modelUsed;              // Modèle IA utilisé (Groq)

    // Métriques d'optimisation (Coût, Latence, Tokens, Cache, Hyperparamètres)
    private Long executionTimeMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Double estimatedCostUsd;
    private Boolean cached;
    private Double temperature;
    private List<String> ragRulesApplied;
}
