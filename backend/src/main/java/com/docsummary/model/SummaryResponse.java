package com.docsummary.model;

import java.util.List;

/**
 * Response DTO returned by the /api/process endpoint.
 * Contains the extracted text, AI-generated summary, key points,
 * improvement suggestions, and document metadata.
 */
public class SummaryResponse {

    private String extractedText;
    private String summary;
    private List<String> keyPoints;
    private List<String> suggestions;
    private int wordCount;
    private String readingTime;
    private int pageCount;
    private String fileType;

    public SummaryResponse() {}

    public SummaryResponse(String extractedText, String summary, List<String> keyPoints,
                           List<String> suggestions, int wordCount, String readingTime,
                           int pageCount, String fileType) {
        this.extractedText = extractedText;
        this.summary       = summary;
        this.keyPoints     = keyPoints;
        this.suggestions   = suggestions;
        this.wordCount     = wordCount;
        this.readingTime   = readingTime;
        this.pageCount     = pageCount;
        this.fileType      = fileType;
    }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String v) { this.extractedText = v; }

    public String getSummary() { return summary; }
    public void setSummary(String v) { this.summary = v; }

    public List<String> getKeyPoints() { return keyPoints; }
    public void setKeyPoints(List<String> v) { this.keyPoints = v; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> v) { this.suggestions = v; }

    public int getWordCount() { return wordCount; }
    public void setWordCount(int v) { this.wordCount = v; }

    public String getReadingTime() { return readingTime; }
    public void setReadingTime(String v) { this.readingTime = v; }

    public int getPageCount() { return pageCount; }
    public void setPageCount(int v) { this.pageCount = v; }

    public String getFileType() { return fileType; }
    public void setFileType(String v) { this.fileType = v; }
}
