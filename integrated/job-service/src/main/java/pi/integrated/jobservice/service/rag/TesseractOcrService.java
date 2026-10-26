package pi.integrated.jobservice.service.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
public class TesseractOcrService {

    @Value("${ocr.tesseract.path:tesseract}")
    private String tesseractPath;

    /**
     * Extrait le texte d'une image ou d'un fichier scanné (PNG, JPG, TIFF, etc.)
     * en utilisant le binaire natif Tesseract OCR.
     */
    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "";
        }

        Path tempInput = null;
        Path tempOutputBase = null;

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = ".png";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            tempInput = Files.createTempFile("ocr_input_" + UUID.randomUUID(), extension);
            Files.write(tempInput, file.getBytes());

            String outputBaseName = "ocr_out_" + UUID.randomUUID();
            tempOutputBase = Files.createTempFile(outputBaseName, "");
            // Supprimer le fichier vide créé pour que Tesseract puisse générer .txt
            Files.deleteIfExists(tempOutputBase);

            ProcessBuilder pb = new ProcessBuilder(
                    tesseractPath,
                    tempInput.toAbsolutePath().toString(),
                    tempOutputBase.toAbsolutePath().toString(),
                    "-l", "eng"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Consommer la sortie pour éviter le blocage du buffer
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[Tesseract] {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("Tesseract a terminé avec le code d'erreur: {}", exitCode);
            }

            // Tesseract génère automatiquement un fichier avec l'extension .txt
            Path resultTxtFile = Path.of(tempOutputBase.toAbsolutePath() + ".txt");
            if (Files.exists(resultTxtFile)) {
                String extracted = Files.readString(resultTxtFile, StandardCharsets.UTF_8).trim();
                Files.deleteIfExists(resultTxtFile);
                return extracted;
            }

        } catch (Exception e) {
            log.error("Erreur lors de l'exécution de Tesseract OCR: {}", e.getMessage(), e);
        } finally {
            try {
                if (tempInput != null) Files.deleteIfExists(tempInput);
                if (tempOutputBase != null) Files.deleteIfExists(tempOutputBase);
            } catch (Exception ignored) {}
        }

        return "";
    }
}
