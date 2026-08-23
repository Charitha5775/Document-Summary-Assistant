package com.docsummary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Document Summary Assistant Spring Boot application.
 * Provides REST API for PDF/image text extraction and AI-powered summarization.
 */
@SpringBootApplication
public class DocSummaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocSummaryApplication.class, args);
    }
}
