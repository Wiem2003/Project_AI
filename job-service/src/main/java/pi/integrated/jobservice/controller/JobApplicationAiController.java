package pi.integrated.jobservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pi.integrated.jobservice.dto.JobAiRankingResponseDTO;
import pi.integrated.jobservice.service.ai.JobApplicationAiService;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class JobApplicationAiController {

    private final JobApplicationAiService jobApplicationAiService;

    @GetMapping("/job/{jobId}/ai-ranking")
    public ResponseEntity<JobAiRankingResponseDTO> getAiRankingForJob(@PathVariable Long jobId) {
        JobAiRankingResponseDTO response = jobApplicationAiService.rankApplicationsForJob(jobId);
        return ResponseEntity.ok(response);
    }
}
