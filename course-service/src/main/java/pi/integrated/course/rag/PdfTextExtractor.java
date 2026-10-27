package pi.integrated.course.rag;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Extracts text page by page so each chunk can cite its PDF page. */
public final class PdfTextExtractor {

    private PdfTextExtractor() {
    }

    public record PdfPage(int pageNumber, String text) {
    }

    public static List<PdfPage> extractPages(File pdf) throws IOException {
        List<PdfPage> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int p = 1; p <= document.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(document);
                if (text != null && !text.isBlank()) {
                    pages.add(new PdfPage(p, text));
                }
            }
        }
        return pages;
    }
}
