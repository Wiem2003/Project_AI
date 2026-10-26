package pi.integrated.jobservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class JobRagDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CandidateProfile {
        private String skills;         // Ex: "Anglais B2, IELTS, Enseignement enfants, Grammaire"
        private String experience;     // Ex: "3 ans d'expérience en lycée"
        private String city;           // Ex: "Tunis"
        private String targetRole;     // Ex: "Professeur d'Anglais"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatRequest {
        private String question;
        private CandidateProfile profile; // Optionnel : profil candidat pour personnalisation
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChatResponse {
        private String answer;
        private List<MatchedJobSummary> matchedJobs;
        private String modelUsed;
        private String ocrExtractedText; // Texte extrait par OCR si pièce jointe
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchedJobSummary {
        private Long id;
        private String title;
        private String subject;
        private String location;
        private Double salaryMin;
        private Double salaryMax;
        private double relevanceScore;
    }
}
