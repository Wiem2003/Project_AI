package pi.integrated.jobservice.service.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pi.integrated.jobservice.dto.JobRagDTO;
import pi.integrated.jobservice.model.Job;
import pi.integrated.jobservice.service.CvTextExtractionService;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JobRagService {

    private final JobRagRetriever jobRagRetriever;
    private final JobRagPromptBuilder promptBuilder;
    private final OllamaClient ollamaClient;
    private final TesseractOcrService tesseractOcrService;
    private final CvTextExtractionService cvTextExtractionService;

    public JobRagService(
            JobRagRetriever jobRagRetriever,
            JobRagPromptBuilder promptBuilder,
            OllamaClient ollamaClient,
            TesseractOcrService tesseractOcrService,
            CvTextExtractionService cvTextExtractionService) {
        this.jobRagRetriever = jobRagRetriever;
        this.promptBuilder = promptBuilder;
        this.ollamaClient = ollamaClient;
        this.tesseractOcrService = tesseractOcrService;
        this.cvTextExtractionService = cvTextExtractionService;
    }

    public JobRagDTO.ChatResponse ask(String question, JobRagDTO.CandidateProfile profile, MultipartFile attachment) {
        String ocrText = "";

        // 1. Analyse OCR si une pièce jointe est fournie
        if (attachment != null && !attachment.isEmpty()) {
            String filename = attachment.getOriginalFilename() != null ? attachment.getOriginalFilename().toLowerCase() : "";
            try {
                if (filename.endsWith(".pdf")) {
                    ocrText = cvTextExtractionService.extractTextFromPdf(attachment);
                    // Si le PDF est un scan (texte vide), tentative Tesseract
                    if (ocrText == null || ocrText.isBlank()) {
                        ocrText = tesseractOcrService.extractText(attachment);
                    }
                } else {
                    // Images (PNG, JPG, JPEG, etc.) via Tesseract OCR
                    ocrText = tesseractOcrService.extractText(attachment);
                }
                log.info("OCR extrait avec succès (taille: {} caractères)", ocrText.length());
            } catch (Exception e) {
                log.error("Erreur d'extraction OCR: {}", e.getMessage());
            }
        }

        boolean hasQuestion = question != null && !question.trim().isEmpty();
        boolean hasProfile = profile != null && (profile.getSkills() != null || profile.getCity() != null || profile.getTargetRole() != null || profile.getExperience() != null);
        boolean hasOcr = !ocrText.isBlank();

        if (!hasQuestion && !hasProfile && !hasOcr) {
            return JobRagDTO.ChatResponse.builder()
                    .answer("Bonjour ! Posez-moi une question, configurez votre profil ou joignez votre CV/diplôme pour des recommandations personnalisées.")
                    .matchedJobs(List.of())
                    .modelUsed(ollamaClient.getModel())
                    .build();
        }

        // 2. Retrieval : Recherche et classement des offres selon le texte combiné (Question + Profil + OCR)
        String combinedQuery = (question != null ? question : "") + " " + ocrText;
        List<JobRagRetriever.ScoredJob> relevantJobs = jobRagRetriever.retrieveRelevantJobs(combinedQuery, profile, 5);

        // 3. Construction du prompt enrichi avec le contexte OCR et le profil
        String prompt = promptBuilder.buildPrompt(question, profile, ocrText, relevantJobs);

        // 4. Génération par le LLM local (Ollama)
        String llmAnswer = ollamaClient.generateResponse(prompt);

        // 5. Préparation de la réponse
        List<JobRagDTO.MatchedJobSummary> matchedSummaries = relevantJobs.stream().map(sj -> {
            Job j = sj.getJob();
            return JobRagDTO.MatchedJobSummary.builder()
                    .id(j.getId())
                    .title(j.getTitle())
                    .subject(j.getSubject())
                    .location(j.getLocation())
                    .salaryMin(j.getSalaryMin())
                    .salaryMax(j.getSalaryMax())
                    .relevanceScore(sj.getScore())
                    .build();
        }).collect(Collectors.toList());

        return JobRagDTO.ChatResponse.builder()
                .answer(llmAnswer)
                .matchedJobs(matchedSummaries)
                .modelUsed(ollamaClient.getModel())
                .ocrExtractedText(ocrText.isBlank() ? null : (ocrText.length() > 200 ? ocrText.substring(0, 200) + "..." : ocrText))
                .build();
    }
}
