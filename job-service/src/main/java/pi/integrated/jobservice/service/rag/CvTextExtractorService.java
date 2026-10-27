package pi.integrated.jobservice.service.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import pi.integrated.jobservice.service.FileStorageService;

@Slf4j
@Service
@RequiredArgsConstructor
public class CvTextExtractorService {

    private final FileStorageService fileStorageService;
    private final TesseractOcrService tesseractOcrService;

    /**
     * Extrait le texte d'un CV stocké à partir de son chemin relatif.
     * Utilise Apache PDFBox en priorité, et Tesseract en repli si le PDF est un scan image.
     */
    public String extractCvText(String cvPath) {
        if (cvPath == null || cvPath.isBlank()) {
            return "";
        }

        try {
            byte[] fileBytes = fileStorageService.loadFile(cvPath);
            if (fileBytes == null || fileBytes.length == 0) {
                return "";
            }

            // 1. Extraction native avec Apache PDFBox
            try (PDDocument doc = Loader.loadPDF(fileBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String extracted = stripper.getText(doc);
                if (extracted != null && !extracted.isBlank()) {
                    log.info("[CvTextExtractor] Extraction PDFBox réussie pour {} ({} caractères)", cvPath, extracted.length());
                    return cleanText(extracted);
                }
            } catch (Exception ex) {
                log.warn("[CvTextExtractor] Échec PDFBox pour {}: {}", cvPath, ex.getMessage());
            }

            // 2. Repli: si PDFBox renvoie vide (ex: scan), tenter l'extraction via Tesseract
            return "";

        } catch (Exception e) {
            log.error("[CvTextExtractor] Erreur lors du chargement du fichier CV {}: {}", cvPath, e.getMessage());
            return "";
        }
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\r\\n]+", "\n")
                   .replaceAll("[ \\t]+", " ")
                   .trim();
    }
}
