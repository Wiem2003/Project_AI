package pi.integrated.jobservice.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
public class WhisperSpeechService {

    @Value("${whisper.python.path:python}")
    private String pythonPath;

    @Value("${whisper.model:base}")
    private String whisperModel;

    private final ObjectMapper objectMapper;

    public WhisperSpeechService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Transcrit un fichier audio (WAV, MP3, WEBM, OGG, M4A) en texte via Whisper local.
     */
    public String transcribe(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            return "";
        }

        Path tempAudio = null;
        try {
            String originalFilename = audioFile.getOriginalFilename();
            String ext = ".wav";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            tempAudio = Files.createTempFile("whisper_in_" + UUID.randomUUID(), ext);
            Files.write(tempAudio, audioFile.getBytes());

            // Recherche robuste de whisper_transcribe.py
            Path scriptPath = Path.of("whisper_transcribe.py").toAbsolutePath();
            if (!Files.exists(scriptPath)) {
                scriptPath = Path.of("job-service", "whisper_transcribe.py").toAbsolutePath();
            }
            if (!Files.exists(scriptPath)) {
                scriptPath = Path.of("integrated", "job-service", "whisper_transcribe.py").toAbsolutePath();
            }
            if (!Files.exists(scriptPath)) {
                scriptPath = Path.of("C:/Users/DELL/Desktop/asma_ai/merge_integ/merge_integ/integrated/job-service/whisper_transcribe.py").toAbsolutePath();
            }

            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath,
                    scriptPath.toString(),
                    tempAudio.toAbsolutePath().toString(),
                    whisperModel
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = process.waitFor();
            String rawJson = output.toString().trim();
            log.info("Sortie Whisper (exit={}): {}", exitCode, rawJson);

            if (rawJson.startsWith("{") && rawJson.endsWith("}")) {
                JsonNode root = objectMapper.readTree(rawJson);
                if (root.has("text")) {
                    return root.get("text").asText().trim();
                } else if (root.has("error")) {
                    log.error("Erreur Whisper: {}", root.get("error").asText());
                }
            } else {
                return rawJson;
            }

        } catch (Exception e) {
            log.error("Erreur lors de la transcription Whisper locale: {}", e.getMessage(), e);
        } finally {
            try {
                if (tempAudio != null) Files.deleteIfExists(tempAudio);
            } catch (Exception ignored) {}
        }

        return "";
    }
}
