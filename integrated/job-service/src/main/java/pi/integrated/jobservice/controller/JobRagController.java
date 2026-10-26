package pi.integrated.jobservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pi.integrated.jobservice.dto.JobRagDTO;
import pi.integrated.jobservice.service.rag.JobRagService;

@RestController
@RequestMapping("/api/jobs/rag")
@CrossOrigin(origins = "*")
public class JobRagController {

    private final JobRagService jobRagService;
    private final pi.integrated.jobservice.service.rag.WhisperSpeechService whisperSpeechService;
    private final ObjectMapper objectMapper;

    public JobRagController(
            JobRagService jobRagService,
            pi.integrated.jobservice.service.rag.WhisperSpeechService whisperSpeechService,
            ObjectMapper objectMapper) {
        this.jobRagService = jobRagService;
        this.whisperSpeechService = whisperSpeechService;
        this.objectMapper = objectMapper;
    }

    /**
     * Endpoint pour transcrire un message audio en texte via Whisper local
     */
    @PostMapping(value = "/transcribe-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> transcribeAudio(@RequestParam("audio") MultipartFile audio) {
        String transcript = whisperSpeechService.transcribe(audio);
        return ResponseEntity.ok(java.util.Map.of("text", transcript));
    }

    /**
     * Endpoint standard JSON
     */
    @PostMapping("/chat")
    public ResponseEntity<JobRagDTO.ChatResponse> chatWithJobs(@RequestBody JobRagDTO.ChatRequest request) {
        JobRagDTO.ChatResponse response = jobRagService.ask(request.getQuestion(), request.getProfile(), null);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint Multipart pour accepter une pièce jointe (Image, CV, diplôme) avec analyse Tesseract OCR
     */
    @PostMapping(value = "/chat-with-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobRagDTO.ChatResponse> chatWithFile(
            @RequestParam(value = "question", required = false) String question,
            @RequestParam(value = "profile", required = false) String profileJson,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        JobRagDTO.CandidateProfile profile = null;
        if (profileJson != null && !profileJson.isBlank()) {
            try {
                profile = objectMapper.readValue(profileJson, JobRagDTO.CandidateProfile.class);
            } catch (Exception ignored) {}
        }

        JobRagDTO.ChatResponse response = jobRagService.ask(question, profile, file);
        return ResponseEntity.ok(response);
    }
}
