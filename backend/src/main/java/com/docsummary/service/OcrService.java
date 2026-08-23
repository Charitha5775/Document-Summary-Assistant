package com.docsummary.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Service responsible for extracting text from image files using Tess4J (Tesseract OCR).
 * Supports JPEG, PNG, TIFF, BMP, GIF, and WEBP formats.
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    /**
     * Path to the Tesseract tessdata directory.
     * Defaults to the bundled tessdata folder; override via TESSDATA_PREFIX env var
     * or the application.properties key below.
     */
    @Value("${tesseract.datapath:tessdata}")
    private String tessdataPath;

    /**
     * Performs OCR on the given image file and returns the extracted text.
     *
     * @param file the uploaded image MultipartFile
     * @return the text extracted via OCR
     * @throws IOException        if the image cannot be read
     * @throws TesseractException if OCR processing fails
     */
    public String extractText(MultipartFile file) throws IOException, TesseractException {
        log.info("Starting OCR for image file: {}", file.getOriginalFilename());

        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage("eng");
        // Page segmentation mode 3: fully automatic page segmentation
        tesseract.setPageSegMode(3);
        // OCR engine mode 3: default, based on what is available
        tesseract.setOcrEngineMode(3);

        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IOException("Could not read image. Ensure the file is a valid image format (JPEG, PNG, TIFF, etc.)");
            }

            String result = tesseract.doOCR(image);
            log.info("OCR extracted {} characters", result.length());
            return result.trim();
        }
    }
}
