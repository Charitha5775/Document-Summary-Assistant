package com.docsummary.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Service responsible for extracting text from PDF documents using Apache PDFBox.
 * Preserves paragraph structure and processes all pages.
 */
@Service
public class PdfExtractorService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractorService.class);

    /**
     * Extracts all text from the given PDF file.
     *
     * @param file the uploaded PDF MultipartFile
     * @return a {@link PdfExtractionResult} containing the full text and page count
     * @throws IOException if the PDF cannot be read or parsed
     */
    public PdfExtractionResult extractText(MultipartFile file) throws IOException {
        log.info("Starting PDF text extraction for file: {}", file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream();
             PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {

            int pageCount = document.getNumberOfPages();
            log.info("PDF has {} page(s)", pageCount);

            PDFTextStripper stripper = new PDFTextStripper();
            // Preserve reading order and paragraph structure
            stripper.setSortByPosition(true);
            stripper.setAddMoreFormatting(true);

            String text = stripper.getText(document);
            log.info("Extracted {} characters from PDF", text.length());

            return new PdfExtractionResult(text.trim(), pageCount);
        }
    }

    /**
     * Simple record holding the result of PDF extraction.
     */
    public record PdfExtractionResult(String text, int pageCount) {}
}
