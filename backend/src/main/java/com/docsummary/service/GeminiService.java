package com.docsummary.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service that communicates with the OpenRouter API (model: z-ai/glm-5.2:free) to
 * generate smart document summaries, key points, and improvement suggestions.
 *
 * <p>OpenRouter exposes an OpenAI-compatible {@code /chat/completions} endpoint,
 * so the request body follows the standard chat format.
 *
 * <p>Automatically retries on HTTP 429 (rate-limit) with exponential back-off.
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** Maximum number of retry attempts on 429 responses. */
    private static final int MAX_RETRIES = 4;

    /** Base delay in milliseconds; doubles on each retry (1s, 2s, 4s, 8s). */
    private static final long BASE_DELAY_MS = 1_000;

    @Value("${openrouter.api.key:}")
    private String apiKey;

    @Value("${openrouter.model:z-ai/glm-5.2:free}")
    private String model;

    private final OkHttpClient httpClient;
    private final Gson gson;

    public GeminiService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    /**
     * Result record holding parsed AI output.
     */
    public record GeminiResult(
            String summary,
            List<String> keyPoints,
            List<String> suggestions
    ) {}

    /**
     * Calls the OpenRouter API to generate a summary of the provided text.
     * Retries automatically on 429 rate-limit responses (up to {@value MAX_RETRIES} times).
     *
     * @param text            the extracted document text
     * @param summaryLength   "short", "medium", or "long"
     * @param apiKeyOverride  optional API key passed from the request (overrides server config)
     * @return a {@link GeminiResult} with summary, key points, and suggestions
     * @throws IOException if the API call ultimately fails
     */
    public GeminiResult summarize(String text, String summaryLength, String apiKeyOverride) throws IOException {
        String effectiveApiKey = (apiKeyOverride != null && !apiKeyOverride.isBlank())
                ? apiKeyOverride
                : apiKey;

        if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
            throw new IllegalStateException("OpenRouter API key is not configured. " +
                    "Set OPENROUTER_API_KEY in the .env file or pass it in the X-Gemini-Key header.");
        }

        String wordTarget = switch (summaryLength.toLowerCase()) {
            case "short"  -> "approximately 80-120 words";
            case "long"   -> "approximately 400-500 words";
            default       -> "approximately 200-280 words"; // medium
        };

        String prompt      = buildPrompt(text, wordTarget);
        String requestBody = buildRequestBody(prompt);

        Request request = new Request.Builder()
                .url(OPENROUTER_API_URL)
                .post(RequestBody.create(requestBody, JSON))
                .addHeader("Authorization", "Bearer " + effectiveApiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://docsummary.ai")
                .addHeader("X-Title", "DocSummary AI")
                .build();

        log.info("Calling OpenRouter API (model: {}) with summary length: {}", model, summaryLength);

        IOException lastException = null;
        long delayMs = BASE_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {

                if (response.code() == 429) {
                    // Honour Retry-After header if provided, else use exponential back-off
                    String retryAfter = response.header("Retry-After");
                    long waitMs = (retryAfter != null) ? Long.parseLong(retryAfter) * 1_000L : delayMs;
                    log.warn("OpenRouter 429 rate-limit (attempt {}/{}). Waiting {}ms before retry…",
                            attempt, MAX_RETRIES, waitMs);

                    if (attempt == MAX_RETRIES) {
                        String errorBody = response.body() != null ? response.body().string() : "No response body";
                        throw new IOException("OpenRouter API returned error 429 after " +
                                MAX_RETRIES + " retries: " + errorBody);
                    }

                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while waiting to retry OpenRouter request", ie);
                    }

                    delayMs *= 2; // exponential back-off
                    continue;
                }

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No response body";
                    log.error("OpenRouter API error {}: {}", response.code(), errorBody);
                    throw new IOException("OpenRouter API returned error " + response.code() + ": " + errorBody);
                }

                // Success
                String responseBody = response.body().string();
                return parseOpenRouterResponse(responseBody);

            } catch (IOException e) {
                // Non-429 IOException — don't retry, rethrow immediately
                if (!e.getMessage().contains("429")) {
                    throw e;
                }
                lastException = e;
            }
        }

        throw (lastException != null) ? lastException
                : new IOException("OpenRouter API failed after " + MAX_RETRIES + " attempts.");
    }

    // ─── Private helpers ────────────────────────────────────────────────────────

    private String buildPrompt(String text, String wordTarget) {
        // Truncate very long documents to avoid token limits (keep first ~12000 chars)
        String truncatedText = text.length() > 12000
                ? text.substring(0, 12000) + "\n\n[... document truncated for processing ...]"
                : text;

        return """
                You are an expert document analyst. Analyze the following document text and respond ONLY with a valid JSON object (no markdown, no code fences, just raw JSON).
                
                The JSON must follow this exact structure:
                {
                  "summary": "<document summary in %s>",
                  "keyPoints": ["<point 1>", "<point 2>", "<point 3>", "<point 4>", "<point 5>"],
                  "suggestions": ["<suggestion 1>", "<suggestion 2>", "<suggestion 3>"]
                }
                
                Rules:
                - summary: A coherent, well-written summary of the document in %s.
                - keyPoints: Exactly 3–6 most important points or takeaways from the document.
                - suggestions: 2–4 constructive suggestions to improve the document's clarity, structure, or completeness.
                - Do NOT include any text outside the JSON object.
                - Do NOT wrap the JSON in markdown code blocks.
                
                Document text:
                ---
                %s
                ---
                """.formatted(wordTarget, wordTarget, truncatedText);
    }

    private String buildRequestBody(String prompt) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("temperature", 0.3);
        body.addProperty("max_tokens", 4096);

        return gson.toJson(body);
    }

    private GeminiResult parseOpenRouterResponse(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");

            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("No choices in OpenRouter response");
            }

            String rawText = choices.get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content")
                    .getAsString()
                    .trim();

            log.debug("Raw model output: {}", rawText);

            // Strip markdown code fences if the model adds them despite instructions
            if (rawText.startsWith("```")) {
                rawText = rawText.replaceAll("^```(?:json)?\\n?", "").replaceAll("```$", "").trim();
            }

            JsonObject parsed = JsonParser.parseString(rawText).getAsJsonObject();

            String summary = parsed.get("summary").getAsString();

            List<String> keyPoints = new ArrayList<>();
            parsed.getAsJsonArray("keyPoints")
                    .forEach(el -> keyPoints.add(el.getAsString()));

            List<String> suggestions = new ArrayList<>();
            parsed.getAsJsonArray("suggestions")
                    .forEach(el -> suggestions.add(el.getAsString()));

            return new GeminiResult(summary, keyPoints, suggestions);

        } catch (Exception e) {
            log.error("Failed to parse OpenRouter response. Raw body: {}", responseBody);
            log.error("Parse error: {}", e.getMessage());
            throw new RuntimeException("Failed to parse OpenRouter API response: " + e.getMessage(), e);
        }
    }
}
