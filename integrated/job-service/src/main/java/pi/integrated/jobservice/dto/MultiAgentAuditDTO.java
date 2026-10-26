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
public class MultiAgentAuditDTO {
    private String fileName;
    private String language;

    // Agent 1: Threat Modeler / Auditeur Offensif Conceptuel
    private ThreatModelAgentDTO threatAssessment;

    // Agent 2: Defense Architect / Architecte de Durcissement
    private DefenseAgentDTO remediationDefense;

    // Agent 3: QA & Compliance Verifier / Validateur de Robustesse
    private QaVerifierAgentDTO qaVerification;

    // Synthèse du consensus multi-agents
    private String consensusSummary;
    private Integer finalSecurityScore; // 0 à 100
    private String certificationStatus; // "CERTIFIÉ CONFORME", "NON CONFORME"

    // Métriques d'exécution du système multi-agents
    private Long executionTimeMs;
    private Integer totalTokens;
    private Double estimatedCostUsd;
    private String modelUsed;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreatModelAgentDTO {
        private String threatLevel;              // "CRITICAL", "HIGH", "MEDIUM", "LOW"
        private String strideCategory;           // "Injection", "Broken Access", "Information Disclosure"
        private List<String> threatVectors;
        private String theoreticalImpact;
        private String agentRationale;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DefenseAgentDTO {
        private String defenseStrategy;
        private String patchedCode;
        private List<String> hardeningMeasures;
        private List<String> solidPrinciples;
        private String agentRationale;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QaVerifierAgentDTO {
        private String verificationVerdict;      // "CERTIFIED ROBUST", "REQUIRES HARDENING"
        private Integer robustnessScore;         // 0 à 100
        private String verificationTests;        // JUnit 5 / Mockito tests
        private String residualRiskAssessment;
        private String agentRationale;
    }
}
