package pi.integrated.jobservice.service.rag;

import org.springframework.stereotype.Service;
import pi.integrated.jobservice.dto.JobRagDTO;
import pi.integrated.jobservice.model.Job;
import pi.integrated.jobservice.model.JobStatus;
import pi.integrated.jobservice.repository.JobRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobRagRetriever {

    private final JobRepository jobRepository;

    // Stopwords fréquents à ignorer pour éviter de fausser le scoring
    private static final Set<String> STOPWORDS = Set.of(
            "les", "des", "pour", "avec", "dans", "sur", "par", "une", "est", "sont", "mon", "mes",
            "selon", "offres", "offre", "recommande", "cherche", "travail", "poste", "emploi",
            "the", "and", "for", "with", "from", "that", "this", "have", "been", "teacher", "teaching", "years"
    );

    // Mots-clés de disciplines / matières
    private static final List<String> LANGUAGES = List.of(
            "anglais", "english", "francais", "français", "french", "allemand", "german", "espagnol", "spanish", "italien", "italian", "arabe", "arabic"
    );

    public JobRagRetriever(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public static class ScoredJob {
        private final Job job;
        private final double score;

        public ScoredJob(Job job, double score) {
            this.job = job;
            this.score = score;
        }

        public Job getJob() {
            return job;
        }

        public double getScore() {
            return score;
        }
    }

    public List<ScoredJob> retrieveRelevantJobs(String userQuery, JobRagDTO.CandidateProfile profile, int maxResults) {
        LocalDateTime now = LocalDateTime.now();
        List<Job> activeJobs = jobRepository.findByStatus(JobStatus.OPEN).stream()
                .filter(j -> j.getExpiresAt() == null || j.getExpiresAt().isAfter(now))
                .collect(Collectors.toList());

        if (activeJobs.isEmpty()) {
            return Collections.emptyList();
        }

        // Concaténation de la question et du profil pour le calcul de pertinence
        StringBuilder queryText = new StringBuilder();
        if (userQuery != null) queryText.append(userQuery).append(" ");
        if (profile != null) {
            if (profile.getTargetRole() != null) queryText.append(profile.getTargetRole()).append(" ");
            if (profile.getSkills() != null) queryText.append(profile.getSkills()).append(" ");
            if (profile.getCity() != null) queryText.append(profile.getCity()).append(" ");
            if (profile.getExperience() != null) queryText.append(profile.getExperience()).append(" ");
        }

        String combinedText = queryText.toString().trim().toLowerCase();
        Set<String> queryTokens = tokenize(combinedText);

        // Détection de la langue / matière principale du CV ou de la requête
        Set<String> detectedDisciplines = new HashSet<>();
        for (String lang : LANGUAGES) {
            if (combinedText.contains(lang)) {
                if (lang.equals("english")) detectedDisciplines.add("anglais");
                else if (lang.equals("french")) detectedDisciplines.add("francais");
                else if (lang.equals("german")) detectedDisciplines.add("allemand");
                else if (lang.equals("spanish")) detectedDisciplines.add("espagnol");
                else if (lang.equals("italian")) detectedDisciplines.add("italien");
                else if (lang.equals("arabic")) detectedDisciplines.add("arabe");
                else detectedDisciplines.add(lang.replace("ç", "c"));
            }
        }

        List<ScoredJob> scoredJobs = new ArrayList<>();
        for (Job job : activeJobs) {
            double score = computeRelevanceScore(job, queryTokens, combinedText, profile, detectedDisciplines);
            if (score > 0) {
                scoredJobs.add(new ScoredJob(job, score));
            }
        }

        // Si des correspondances existent, tri par score décroissant
        if (!scoredJobs.isEmpty()) {
            scoredJobs.sort(Comparator.comparingDouble(ScoredJob::getScore).reversed());
            return scoredJobs.stream().limit(maxResults).collect(Collectors.toList());
        }

        // Fallback si aucune correspondance spécifique
        return activeJobs.stream()
                .sorted(Comparator.comparing(Job::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(maxResults)
                .map(j -> new ScoredJob(j, 0.5))
                .collect(Collectors.toList());
    }

    private double computeRelevanceScore(
            Job job,
            Set<String> queryTokens,
            String rawQueryLower,
            JobRagDTO.CandidateProfile profile,
            Set<String> detectedDisciplines) {

        double score = 0.0;

        String title = Optional.ofNullable(job.getTitle()).orElse("").toLowerCase().replace("ç", "c");
        String subject = Optional.ofNullable(job.getSubject()).orElse("").toLowerCase().replace("ç", "c");
        String location = Optional.ofNullable(job.getLocation()).orElse("").toLowerCase();
        String description = Optional.ofNullable(job.getDescription()).orElse("").toLowerCase().replace("ç", "c");

        // 1. Vérification cruciale de la matière / discipline (ex: Anglais vs Allemand)
        if (!detectedDisciplines.isEmpty()) {
            boolean subjectMatches = false;
            for (String disc : detectedDisciplines) {
                if (subject.contains(disc) || title.contains(disc) || description.contains(disc)) {
                    score += 25.0; // Énorme boost pour la bonne matière
                    subjectMatches = true;
                }
            }

            // Si le candidat est typé (ex: Prof d'Anglais) et que l'offre est une autre langue (ex: Allemand) sans correspondance
            if (!subjectMatches) {
                for (String otherLang : List.of("allemand", "francais", "espagnol", "italien")) {
                    if (!detectedDisciplines.contains(otherLang) && (subject.contains(otherLang) || title.contains(otherLang))) {
                        return 0.0; // Élimine les fausses offres d'autres langues !
                    }
                }
            }
        }

        // 2. Scoring par tokens filtrés
        for (String token : queryTokens) {
            if (token.length() < 3 || STOPWORDS.contains(token)) continue;

            if (title.contains(token)) score += 4.0;
            if (subject.contains(token)) score += 3.5;
            if (location.contains(token)) score += 2.0;
            if (description.contains(token)) score += 1.0;
        }

        // 3. Bonus spécifique au profil candidat
        if (profile != null) {
            if (profile.getCity() != null && !profile.getCity().isBlank()) {
                String city = profile.getCity().trim().toLowerCase();
                if (location.contains(city) || city.contains(location)) {
                    score += 5.0;
                }
            }

            if (profile.getTargetRole() != null && !profile.getTargetRole().isBlank()) {
                String target = profile.getTargetRole().trim().toLowerCase();
                if (title.contains(target) || target.contains(title)) {
                    score += 6.0;
                }
            }

            if (profile.getSkills() != null && !profile.getSkills().isBlank()) {
                Set<String> skillTokens = tokenize(profile.getSkills());
                for (String sk : skillTokens) {
                    if (sk.length() < 3 || STOPWORDS.contains(sk)) continue;
                    if (description.contains(sk) || title.contains(sk) || subject.contains(sk)) {
                        score += 3.0;
                    }
                }
            }
        }

        return score;
    }

    private Set<String> tokenize(String text) {
        if (text == null) return Collections.emptySet();
        return Arrays.stream(text.toLowerCase().split("[^\\p{L}\\p{Nd}]+"))
                .filter(s -> !s.isBlank() && !STOPWORDS.contains(s))
                .collect(Collectors.toSet());
    }
}
