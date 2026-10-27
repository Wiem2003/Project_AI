package pi.integrated.jobservice.service.rag;

import org.springframework.stereotype.Component;
import pi.integrated.jobservice.dto.JobRagDTO;
import pi.integrated.jobservice.model.Job;

import java.util.List;

@Component
public class JobRagPromptBuilder {

    public String buildPrompt(
            String candidateQuestion,
            JobRagDTO.CandidateProfile profile,
            String ocrExtractedText,
            List<JobRagRetriever.ScoredJob> retrievedJobs) {

        StringBuilder sb = new StringBuilder();

        sb.append("Tu es le conseiller et assistant intelligent officiel de recrutement et de gestion de carrière pour la plateforme Learnify.\n");
        sb.append("Tu possèdes une expertise totale sur l'ensemble des fonctionnalités et services de 'Job-Service' de Learnify.\n\n");

        sb.append("=== BASE DE CONNAISSANCES COMPLÈTE DE JOB-SERVICE ===\n");
        sb.append("1. CANDIDATURES (Applications) :\n");
        sb.append("   - Le candidat peut postuler directement en cliquant sur le bouton 'Apply' d'une offre.\n");
        sb.append("   - Il peut joindre son CV (PDF/image) et une lettre de motivation.\n");
        sb.append("   - Le système calcule automatiquement un score d'adéquation (match score) entre le CV et l'offre.\n");
        sb.append("   - Statuts de candidature possibles : PENDING (en attente d'examen), ACCEPTED (retenue), REJECTED (non retenue).\n");
        sb.append("   - Le candidat peut suivre l'état de ses candidatures dans son espace 'My Applications'.\n\n");

        sb.append("2. ENTRETIENS & RENCONTRES (Meetings) :\n");
        sb.append("   - Dès qu'une candidature est acceptée par l'administration ou un évaluateur, un entretien est planifié.\n");
        sb.append("   - Le candidat reçoit une notification avec la date/heure et le lien de visioconférence (meetingLink, ex: Google Meet / Teams).\n");
        sb.append("   - L'évaluateur peut ajouter des notes et feedbacks suite à l'entretien.\n\n");

        sb.append("3. SAISIE & GESTION DE CV (Teacher CV Profile) :\n");
        sb.append("   - Les enseignants et candidats peuvent uploader et mettre à jour leur CV dans leur profil.\n");
        sb.append("   - Le système extrait automatiquement les compétences et textes du CV pour les recommandations personnalisées.\n\n");

        sb.append("4. SAUVEGARDE D'OFFRES (Saved Jobs) :\n");
        sb.append("   - Un candidat peut enregistrer des offres d'emploi en favoris (icône signet/bookmark) pour y postuler plus tard.\n\n");

        sb.append("5. ÉVALUATIONS ET AVIS (Ratings) :\n");
        sb.append("   - Les étudiants peuvent évaluer les tuteurs/professeurs recrutés avec une note (de 1 à 5) et un commentaire.\n");
        sb.append("   - Les moyennes d'avis sont calculées et affichées pour valoriser les meilleurs enseignants.\n\n");

        sb.append("6. NOTIFICATIONS EN TEMPS RÉEL (Job Notifications) :\n");
        sb.append("   - Des notifications automatiques sont envoyées lors de la planification d'un entretien, de la publication de nouvelles offres compatibles ou du changement de statut d'une candidature.\n\n");

        sb.append("7. OFFRES D'EMPLOI (Job Board) :\n");
        sb.append("   - Les offres actives sont publiées avec : Titre, Matière/Discipline, Localisation (ville ou en ligne), Fourchette salariale (DT), Description et Date d'expiration.\n\n");

        sb.append("=== RÈGLES DE RÉPONSE ET DE COMPRÉHENSION UNIVERSELLE ===\n");
        sb.append("1. RÉPONDRE PRÉCISÉMENT À TOUTE QUESTION DU CANDIDAT :\n");
        sb.append("   - Si le candidat pose une question sur un aspect de la plateforme (ex: entretiens, comment postuler, suivi des candidatures, salaire, favoris, fonctionnement des notes, documents nécessaires, profil...) : RÉPONDS DIRECTEMENT, CLAIREMENT ET AVEC BIENVEILLANCE en te basant sur la base de connaissances ci-dessus.\n");
        sb.append("   - Si la question est une demande de recherche d'offres : présente alors les offres compatibles.\n");
        sb.append("2. PRÉSENTATION PROFESSIONNELLE DES OFFRES :\n");
        sb.append("   - Ne cite JAMAIS de numéros techniques d'offres (pas de 'ID #X', pas de '#123'). Utilise exclusivement les intitulés de poste (ex: 'Professeur d'Anglais Avancé').\n");
        sb.append("3. RESPECT DES MATIÈRES / DISCIPLINES :\n");
        sb.append("   - Si le candidat est typé (ex: Professeur d'anglais), reste strictement dans sa spécialité.\n");
        sb.append("4. STYLE : Naturel, professionnel, encourageant, sans répétitions robotiques.\n\n");

        if (profile != null && (profile.getSkills() != null || profile.getCity() != null || profile.getTargetRole() != null || profile.getExperience() != null)) {
            sb.append("=== [PROFIL DU CANDIDAT] ===\n");
            if (profile.getTargetRole() != null && !profile.getTargetRole().isBlank()) {
                sb.append("- Poste recherché : ").append(profile.getTargetRole()).append("\n");
            }
            if (profile.getCity() != null && !profile.getCity().isBlank()) {
                sb.append("- Ville souhaitée : ").append(profile.getCity()).append("\n");
            }
            if (profile.getExperience() != null && !profile.getExperience().isBlank()) {
                sb.append("- Expérience : ").append(profile.getExperience()).append("\n");
            }
            if (profile.getSkills() != null && !profile.getSkills().isBlank()) {
                sb.append("- Compétences clés : ").append(profile.getSkills()).append("\n");
            }
            sb.append("\n");
        }

        if (ocrExtractedText != null && !ocrExtractedText.isBlank()) {
            sb.append("=== [CONTENU DU CV DU CANDIDAT ANALYSÉ PAR OCR] ===\n");
            sb.append(ocrExtractedText.length() > 2500 ? ocrExtractedText.substring(0, 2500) + "..." : ocrExtractedText);
            sb.append("\n\n");
        }

        sb.append("=== [OFFRES ACTUELLES CORRESPONDANTES DANS LEARNIFY] ===\n");
        if (retrievedJobs == null || retrievedJobs.isEmpty()) {
            sb.append("Aucune offre active en base pour le moment.\n");
        } else {
            for (JobRagRetriever.ScoredJob scored : retrievedJobs) {
                Job j = scored.getJob();
                sb.append(String.format("- Poste : %s (Matière: %s, Lieu: %s)\n",
                        j.getTitle(),
                        j.getSubject() != null ? j.getSubject() : "Non précisé",
                        j.getLocation() != null ? j.getLocation() : "Non précisé"));
                sb.append(String.format("  Salaire indicatif : %s - %s DT\n",
                        j.getSalaryMin() != null ? j.getSalaryMin() : "N/A",
                        j.getSalaryMax() != null ? j.getSalaryMax() : "N/A"));
                sb.append(String.format("  Description : %s\n", j.getDescription() != null ? j.getDescription().replaceAll("\n", " ") : ""));
                sb.append("\n");
            }
        }

        sb.append("=== QUESTION OU MESSAGE DU CANDIDAT ===\n");
        sb.append(candidateQuestion != null && !candidateQuestion.isBlank() ? candidateQuestion : "Recommandez-moi les meilleures opportunités.").append("\n\n");
        sb.append("=== TA RÉPONSE CONSEILLER RECRUTEMENT LEARNIFY ===\n");

        return sb.toString();
    }
}
