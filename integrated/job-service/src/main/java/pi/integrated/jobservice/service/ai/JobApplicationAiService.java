package pi.integrated.jobservice.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pi.integrated.jobservice.dto.AiCandidateRankingDTO;
import pi.integrated.jobservice.dto.JobAiRankingResponseDTO;
import pi.integrated.jobservice.model.Application;
import pi.integrated.jobservice.model.Job;
import pi.integrated.jobservice.repository.ApplicationRepository;
import pi.integrated.jobservice.repository.JobRepository;
import pi.integrated.jobservice.service.rag.CvTextExtractorService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobApplicationAiService {

    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final CvTextExtractorService cvTextExtractorService;
    private final GroqClient groqClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobAiRankingResponseDTO rankApplicationsForJob(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Offre d'emploi introuvable avec l'ID: " + jobId));

        List<Application> applications = applicationRepository.findByJobId(jobId);

        if (applications.isEmpty()) {
            return JobAiRankingResponseDTO.builder()
                    .jobId(job.getId())
                    .jobTitle(job.getTitle())
                    .totalCandidates(0)
                    .globalAnalysis("Aucune candidature n'a encore été soumise pour ce poste.")
                    .modelUsed(groqClient.getActiveModel())
                    .rankings(List.of())
                    .build();
        }

        // Préparer les données pour chaque candidat
        StringBuilder candidatesContext = new StringBuilder();
        for (int i = 0; i < applications.size(); i++) {
            Application app = applications.get(i);
            String cvText = "";
            if (app.getCvPath() != null && !app.getCvPath().isBlank()) {
                cvText = cvTextExtractorService.extractCvText(app.getCvPath());
                if (cvText.length() > 2000) {
                    cvText = cvTextTextTrim(cvText, 2000);
                }
            }

            candidatesContext.append(String.format("""
                --- CANDIDAT #%d ---
                ApplicationId: %d
                TeacherId: %d
                Nom: %s
                Lettre de motivation: %s
                Contenu du CV: %s
                --------------------
                """,
                    i + 1,
                    app.getId(),
                    app.getTeacherId() != null ? app.getTeacherId() : 0,
                    app.getTeacherName() != null ? app.getTeacherName() : "Candidat",
                    app.getMotivation() != null && !app.getMotivation().isBlank() ? app.getMotivation() : "Aucune lettre fournie",
                    !cvText.isBlank() ? cvText : "Aucun fichier CV ou texte extrait"
            ));
        }

        String systemPrompt = """
            Tu es un expert RH et recruteur IA chevronné pour une plateforme éducative d'élite.
            Ton objectif est d'évaluer et de classer rigoureusement les candidats ayant postulé à une offre d'emploi donnée,
            en comparant précisément les compétences requises par le poste avec les informations fournies dans leur CV et leur lettre de motivation.

            Format de sortie impératif :
            Tu dois répondre STRICTEMENT en JSON valide (sans texte explicatif avant ou après).
            Structure attendue :
            {
              "globalAnalysis": "Synthèse globale de la sélection des candidats pour ce poste...",
              "rankings": [
                {
                  "applicationId": 123,
                  "teacherId": 456,
                  "teacherName": "Nom du candidat",
                  "rank": 1,
                  "aiScore": 92,
                  "recommendation": "Fortement recommandé",
                  "summary": "Résumé en 1-2 phrases du profil...",
                  "keyStrengths": ["Maîtrise de l'anglais C1", "Pédagogie active", "Expérience 3 ans"],
                  "missingSkills": ["Certificat IELTS manquant"],
                  "justification": "Explication détaillée de la note et adéquation par rapport aux exigences du poste..."
                }
              ]
            }
            Règles d'évaluation :
            1. Classe les candidats du meilleur au moins adapté (rank 1 = le meilleur).
            2. Attribue un aiScore cohérent entre 0 et 100.
            3. Les valeurs de recommendation autorisées sont : "Fortement recommandé", "Recommandé", "À évaluer", "Non adapté".
            4. Si un candidat a fourni une lettre de motivation pertinente ou un CV adapté à la matière requise, valorise-le.
            """;

        String userPrompt = String.format("""
            POSTE À POURVOIR :
            - Titre : %s
            - Matière / Domaine : %s
            - Lieu : %s
            - Description et exigences : %s
            - Rémunération : %s - %s TND

            LISTE DES CANDIDATURES REÇUES :
            %s

            Génère le classement JSON complet de tous les candidats listés ci-dessus.
            """,
                job.getTitle(),
                job.getSubject() != null ? job.getSubject() : "Non spécifié",
                job.getLocation() != null ? job.getLocation() : "Non spécifié",
                job.getDescription() != null ? job.getDescription() : "",
                job.getSalaryMin() != null ? job.getSalaryMin().toString() : "N/A",
                job.getSalaryMax() != null ? job.getSalaryMax().toString() : "N/A",
                candidatesContext.toString()
        );

        try {
            String rawResponse = groqClient.generateCompletion(systemPrompt, userPrompt);
            return parseGroqResponse(rawResponse, job, applications);
        } catch (Exception e) {
            log.error("[JobApplicationAiService] Échec de l'évaluation Groq: {}", e.getMessage(), e);
            // Repli automatique en cas de problème de parsing ou réseau
            return generateFallbackRanking(job, applications, e.getMessage());
        }
    }

    private JobAiRankingResponseDTO parseGroqResponse(String rawResponse, Job job, List<Application> applications) {
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
            String globalAnalysis = root.path("globalAnalysis").asText(
                    "Classement établi par l'intelligence artificielle Groq selon l'analyse comparative des profils."
            );

            List<AiCandidateRankingDTO> rankings = new ArrayList<>();
            JsonNode rankingsNode = root.path("rankings");

            if (rankingsNode.isArray()) {
                for (JsonNode item : rankingsNode) {
                    Long appId = item.path("applicationId").asLong();
                    // Retrouver l'application correspondante pour garantir l'exactitude des IDs
                    Application matchedApp = applications.stream()
                            .filter(a -> a.getId().equals(appId))
                            .findFirst()
                            .orElse(null);

                    List<String> strengths = new ArrayList<>();
                    item.path("keyStrengths").forEach(s -> strengths.add(s.asText()));

                    List<String> missing = new ArrayList<>();
                    item.path("missingSkills").forEach(m -> missing.add(m.asText()));

                    AiCandidateRankingDTO dto = AiCandidateRankingDTO.builder()
                            .applicationId(appId)
                            .teacherId(matchedApp != null ? matchedApp.getTeacherId() : item.path("teacherId").asLong())
                            .teacherName(matchedApp != null && matchedApp.getTeacherName() != null ? matchedApp.getTeacherName() : item.path("teacherName").asText("Candidat"))
                            .rank(item.path("rank").asInt(rankings.size() + 1))
                            .aiScore(item.path("aiScore").asInt(75))
                            .recommendation(item.path("recommendation").asText("Recommandé"))
                            .summary(item.path("summary").asText(""))
                            .keyStrengths(strengths)
                            .missingSkills(missing)
                            .justification(item.path("justification").asText(""))
                            .build();

                    rankings.add(dto);
                }
            }

            // Trier par rang
            rankings.sort(Comparator.comparingInt(AiCandidateRankingDTO::getRank));

            return JobAiRankingResponseDTO.builder()
                    .jobId(job.getId())
                    .jobTitle(job.getTitle())
                    .totalCandidates(applications.size())
                    .globalAnalysis(globalAnalysis)
                    .modelUsed(groqClient.getActiveModel())
                    .rankings(rankings)
                    .build();

        } catch (Exception e) {
            log.warn("[JobApplicationAiService] Impossible de parser le JSON Groq: {}. Réponse brute: {}", e.getMessage(), rawResponse);
            return generateFallbackRanking(job, applications, "Échec du décodage du format IA: " + e.getMessage());
        }
    }

    private JobAiRankingResponseDTO generateFallbackRanking(Job job, List<Application> applications, String reason) {
        List<AiCandidateRankingDTO> list = new ArrayList<>();
        int rank = 1;
        for (Application app : applications) {
            int score = (int) Math.round(app.getMatchScore() != null ? app.getMatchScore() : 60);
            list.add(AiCandidateRankingDTO.builder()
                    .applicationId(app.getId())
                    .teacherId(app.getTeacherId())
                    .teacherName(app.getTeacherName() != null ? app.getTeacherName() : "Candidat")
                    .rank(rank++)
                    .aiScore(Math.max(50, Math.min(95, score)))
                    .recommendation(score >= 75 ? "Recommandé" : "À évaluer")
                    .summary("Candidature avec lettre de motivation transmise.")
                    .keyStrengths(List.of("Motivation démontrée", "Profil disponible"))
                    .missingSkills(List.of())
                    .justification("Évaluation basée sur les informations disponibles du dossier.")
                    .build());
        }

        return JobAiRankingResponseDTO.builder()
                .jobId(job.getId())
                .jobTitle(job.getTitle())
                .totalCandidates(applications.size())
                .globalAnalysis("Analyse standard générée (Repli IA: " + reason + ")")
                .modelUsed("Mode Fallback")
                .rankings(list)
                .build();
    }

    private String cvTextTextTrim(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }
}
