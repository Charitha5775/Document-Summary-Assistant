package com.docsummary.controller;

import com.docsummary.model.SummaryResponse;
import com.docsummary.service.GeminiService;
import com.docsummary.service.OcrService;
import com.docsummary.service.PdfExtractorService;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST controller exposing the document processing API.
 *
 * <p>Endpoint: {@code POST /api/process}
 * Accepts a multipart file (PDF or image) plus optional parameters,
 * orchestrates extraction and summarization, and returns a {@link SummaryResponse}.
 */
@RestController
@RequestMapping("/api")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    // Max file size: 20 MB (also enforced in application.properties)
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    private final PdfExtractorService pdfExtractorService;
    private final OcrService ocrService;
    private final GeminiService geminiService;

    public DocumentController(PdfExtractorService pdfExtractorService,
                               OcrService ocrService,
                               GeminiService geminiService) {
        this.pdfExtractorService = pdfExtractorService;
        this.ocrService = ocrService;
        this.geminiService = geminiService;
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "Document Summary Assistant"));
    }

    /**
     * Main document processing endpoint.
     *
     * @param file           the uploaded PDF or image file
     * @param summaryLength  "short", "medium", or "long" (default: "medium")
     * @param geminiApiKey   optional OpenRouter API key passed as a header
     * @return {@link SummaryResponse} with extracted text, summary, key points, and suggestions
     */
    @PostMapping("/process")
    public ResponseEntity<?> processDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "length", defaultValue = "medium") String summaryLength,
            @RequestHeader(value = "X-Gemini-Key", required = false) String geminiApiKey) {

        // ── Validation ─────────────────────────────────────────────────────────
        if (file == null || file.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "No file uploaded. Please select a PDF or image file.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return error(HttpStatus.BAD_REQUEST,
                    "File size exceeds the 20 MB limit. Please upload a smaller file.");
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            return error(HttpStatus.BAD_REQUEST, "Unable to determine file type.");
        }

        boolean isPdf   = contentType.equalsIgnoreCase("application/pdf");
        boolean isImage = contentType.startsWith("image/");

        if (!isPdf && !isImage) {
            return error(HttpStatus.BAD_REQUEST,
                    "Unsupported file type: " + contentType +
                    ". Please upload a PDF or an image (JPEG, PNG, TIFF, etc.).");
        }

        if (!List.of("short", "medium", "long").contains(summaryLength.toLowerCase())) {
            summaryLength = "medium";
        }

        // ── Text Extraction ────────────────────────────────────────────────────
        String extractedText;
        int pageCount;
        String fileType;

        try {
            if (isPdf) {
                log.info("Processing PDF: {}", file.getOriginalFilename());
                PdfExtractorService.PdfExtractionResult result = pdfExtractorService.extractText(file);
                extractedText = result.text();
                pageCount     = result.pageCount();
                fileType      = "pdf";
            } else {
                log.info("Processing image via OCR: {}", file.getOriginalFilename());
                extractedText = ocrService.extractText(file);
                pageCount     = 1;
                fileType      = "image";
            }
        } catch (TesseractException e) {
            log.error("OCR failed: {}", e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "OCR processing failed: " + e.getMessage() +
                    ". Ensure Tesseract is installed and tessdata is present.");
        } catch (IOException e) {
            log.error("File extraction failed: {}", e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to extract text from file: " + e.getMessage());
        }

        if (extractedText == null || extractedText.isBlank()) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No text could be extracted from the document. " +
                    "The file may be empty, corrupted, or contain only non-textual content.");
        }

        // ── AI Summarization ───────────────────────────────────────────────────
        GeminiService.GeminiResult geminiResult;
        try {
            geminiResult = geminiService.summarize(extractedText, summaryLength, geminiApiKey);
        } catch (IllegalStateException e) {
            return error(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (IOException e) {
            log.error("OpenRouter API call failed: {}", e.getMessage(), e);
            return error(HttpStatus.BAD_GATEWAY,
                    "AI summarization service is unavailable: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected error during summarization: {}", e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Summarization failed: " + e.getMessage());
        }

        // ── Build Response ─────────────────────────────────────────────────────
        int wordCount   = countWords(extractedText);
        String readTime = estimateReadingTime(wordCount);

        SummaryResponse response = new SummaryResponse(
                extractedText,
                geminiResult.summary(),
                geminiResult.keyPoints(),
                geminiResult.suggestions(),
                wordCount,
                readTime,
                pageCount,
                fileType
        );

        log.info("Successfully processed {} — {} words, {} pages", fileType, wordCount, pageCount);
        return ResponseEntity.ok(response);
    }

    // ─── Utilities ──────────────────────────────────────────────────────────────

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    private String estimateReadingTime(int wordCount) {
        // Average adult reading speed: 200 words per minute
        int minutes = (int) Math.ceil(wordCount / 200.0);
        return minutes <= 1 ? "1 min read" : minutes + " min read";
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
