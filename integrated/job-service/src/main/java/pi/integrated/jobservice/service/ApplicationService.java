package pi.integrated.jobservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pi.integrated.jobservice.dto.ApplicationDTO;
import pi.integrated.jobservice.model.Application;
import pi.integrated.jobservice.model.ApplicationStatus;
import pi.integrated.jobservice.model.Job;
import pi.integrated.jobservice.repository.ApplicationRepository;
import pi.integrated.jobservice.repository.JobRepository;
import pi.integrated.jobservice.repository.TeacherCvProfileRepository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final TeacherCvProfileRepository cvProfileRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationMatchScoreService matchScoreService;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;

    public ApplicationDTO createApplication(Long jobId, Long teacherId, String teacherName,
                                            String candidateEmail, String motivation, MultipartFile cv, MultipartFile certificat) {
        // Resolve teacherId and teacherName from candidateEmail if missing
        if (teacherId == null && candidateEmail != null && !candidateEmail.isBlank()) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT id, first_name, last_name FROM user_db.users WHERE LOWER(email) = LOWER(?) LIMIT 1",
                        candidateEmail.trim()
                );
                if (!rows.isEmpty()) {
                    Map<String, Object> row = rows.get(0);
                    if (row.get("id") != null) {
                        teacherId = ((Number) row.get("id")).longValue();
                    }
                    if (teacherName == null || teacherName.isBlank()) {
                        String fn = (String) row.get("first_name");
                        String ln = (String) row.get("last_name");
                        teacherName = ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim();
                    }
                }
            } catch (Exception ignored) {}
        }

        // Final fallback if still null
        if (teacherId == null) {
            teacherId = candidateEmail != null ? (Math.abs((long) candidateEmail.hashCode()) % 1000000L + 1L) : 16L;
        }
        if (teacherName == null || teacherName.isBlank()) {
            teacherName = candidateEmail != null ? candidateEmail.split("@")[0] : "Candidat";
        }

        if (teacherId != null && applicationRepository.findByJobIdAndTeacherId(jobId, teacherId).isPresent()) {
            throw new RuntimeException("Vous avez déjà postulé à cette offre d'emploi.");
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        String cvPath = cv != null && !cv.isEmpty() ? fileStorageService.storeFile(cv, "applications/cv") : null;
        String certPath = certificat != null && !certificat.isEmpty()
                ? fileStorageService.storeFile(certificat, "applications/certificat") : null;

        // Compute match score from stored CV profile text if available
        String cvText = "";
        if (teacherId != null) {
            cvText = cvProfileRepository.findByUserId(teacherId)
                    .map(p -> p.getExtractedText()).orElse("");
        }
        double score = matchScoreService.computeScore(cvText, job);

        Application app = Application.builder()
                .job(job)
                .teacherId(teacherId)
                .teacherName(teacherName != null && !teacherName.isBlank() ? teacherName : "Candidat")
                .motivation(motivation)
                .cvPath(cvPath)
                .certificatPath(certPath)
                .status(ApplicationStatus.PENDING)
                .appliedAt(LocalDateTime.now())
                .matchScore(score)
                .build();

        app = applicationRepository.save(app);

        if (teacherId != null) {
            notificationService.createNotification(teacherId,
                    "Votre candidature pour l'offre \"" + job.getTitle() + "\" a été enregistrée avec succès.");
        }

        return mapToDTO(app);
    }

    public List<ApplicationDTO> getAllApplications() {
        return applicationRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (a.getAppliedAt() == null) return 1;
                    if (b.getAppliedAt() == null) return -1;
                    return b.getAppliedAt().compareTo(a.getAppliedAt());
                })
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public ApplicationDTO updateStatus(Long applicationId, ApplicationStatus newStatus) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));
        app.setStatus(newStatus);
        app = applicationRepository.save(app);
        notificationService.notifyApplicationStatusChange(
                app.getTeacherId(), app.getJob().getTitle(), newStatus.name());
        return mapToDTO(app);
    }

    public List<ApplicationDTO> getApplicationsByJob(Long jobId) {
        return applicationRepository.findByJobId(jobId).stream()
                .map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<ApplicationDTO> getApplicationsByTeacher(Long teacherId) {
        return applicationRepository.findByTeacherId(teacherId).stream()
                .map(this::mapToDTO).collect(Collectors.toList());
    }

    public ApplicationDTO getApplication(Long id) {
        return applicationRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new RuntimeException("Application not found: " + id));
    }

    private ApplicationDTO mapToDTO(Application app) {
        return ApplicationDTO.builder()
                .id(app.getId())
                .jobId(app.getJob().getId())
                .jobTitle(app.getJob().getTitle())
                .teacherId(app.getTeacherId())
                .teacherName(app.getTeacherName())
                .motivation(app.getMotivation())
                .cvPath(app.getCvPath())
                .certificatPath(app.getCertificatPath())
                .status(app.getStatus())
                .appliedAt(app.getAppliedAt())
                .matchScore(app.getMatchScore())
                .build();
    }
}
