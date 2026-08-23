# DocSummary AI 📄✨

> An AI-powered document summarization assistant that extracts text from PDFs and scanned images, then generates smart summaries, key points, and improvement suggestions.

![Tech Stack](https://img.shields.io/badge/Backend-Java%2017%20%2B%20Spring%20Boot%203-brightgreen)
![PDF](https://img.shields.io/badge/PDF-Apache%20PDFBox-red)
![OCR](https://img.shields.io/badge/OCR-Tesseract%20%2F%20Tess4J-blue)
![AI](https://img.shields.io/badge/AI-Google%20Gemini%201.5%20Flash-orange)

---

## Features

| Feature | Description |
|---|---|
| 📄 **PDF Upload** | Full text extraction preserving document structure |
| 🖼️ **Image OCR** | Tesseract-powered OCR for scanned documents (JPEG, PNG, TIFF, WEBP) |
| 🤖 **AI Summary** | Google Gemini 1.5 Flash generates concise summaries |
| 📏 **Length Control** | Choose Short (~100w), Medium (~250w), or Long (~500w) summaries |
| 🎯 **Key Points** | Automatically extracted 3–6 most important ideas |
| 💡 **Suggestions** | AI-generated improvement suggestions for the document |
| 📋 **Copy & Export** | One-click copy of summary to clipboard |
| 📱 **Responsive** | Mobile-first, works on all screen sizes |

---

## Architecture

```
┌─────────────────────────────────────────┐
│  Frontend (HTML/CSS/JS)                 │
│  - Drag-and-drop upload                 │
│  - Calls POST /api/process              │
│  - Renders summary, key points          │
└────────────────┬────────────────────────┘
                 │ REST (multipart/form-data)
┌────────────────▼────────────────────────┐
│  Spring Boot Backend (Java 17)          │
│  ┌──────────────┐  ┌─────────────────┐  │
│  │ PDFBox       │  │ Tess4J (OCR)    │  │
│  │ (PDF text)   │  │ (image text)    │  │
│  └──────┬───────┘  └────────┬────────┘  │
│         └─────────┬─────────┘           │
│               ┌───▼──────────┐          │
│               │ Gemini API   │          │
│               │ (summarize)  │          │
│               └──────────────┘          │
└─────────────────────────────────────────┘
```

---

## Prerequisites

| Tool | Version | Download |
|---|---|---|
| Java | 17+ | [adoptium.net](https://adoptium.net) |
| Maven | 3.8+ | [maven.apache.org](https://maven.apache.org) |
| Tesseract | 5.x | [github.com/tesseract-ocr](https://github.com/tesseract-ocr/tesseract/releases) |
| Gemini API Key | Free | [aistudio.google.com](https://aistudio.google.com/app/apikey) |

---

## Setup & Running

### 1. Install Tesseract OCR (required for image files)

**Windows:**
```
Download and run: https://github.com/UB-Mannheim/tesseract/wiki
Default install path: C:\Program Files\Tesseract-OCR
```

**macOS:**
```bash
brew install tesseract
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt install tesseract-ocr
```

### 2. Get a Gemini API Key (Free)

1. Go to [Google AI Studio](https://aistudio.google.com/app/apikey)
2. Sign in with your Google account
3. Click **"Create API Key"**
4. Copy the key (starts with `AIza...`)

### 3. Run the Backend

```bash
cd backend

# Option A: Set API key as environment variable
set GEMINI_API_KEY=AIza...your-key-here     # Windows
export GEMINI_API_KEY=AIza...your-key-here  # macOS/Linux

# For Windows Tesseract (if not in PATH):
set TESSDATA_PREFIX=C:\Program Files\Tesseract-OCR\tessdata

# Build and run
mvn spring-boot:run
```

The backend starts at **http://localhost:8080**

### 4. Open the Frontend

Simply open `frontend/index.html` in your browser, **or** serve it with any static server:

```bash
# Using Python
cd frontend
python -m http.server 3000
# Open http://localhost:3000
```

### 5. Enter Your API Key in the UI

Click the **"API Key"** button in the top-right corner and paste your Gemini API key. It's stored in your browser's localStorage and sent securely to the backend with each request.

---

## API Reference

### `GET /api/health`
Returns service status.

```json
{ "status": "ok", "service": "Document Summary Assistant" }
```

### `POST /api/process`

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file` | multipart | ✅ | PDF or image file (max 20 MB) |
| `length` | string | ❌ | `short`, `medium`, `long` (default: `medium`) |
| `X-Gemini-Key` | header | ❌ | Gemini API key (overrides server env var) |

**Response:**
```json
{
  "extractedText": "Full text from the document...",
  "summary": "AI-generated summary...",
  "keyPoints": ["Point 1", "Point 2", "Point 3"],
  "suggestions": ["Suggestion 1", "Suggestion 2"],
  "wordCount": 842,
  "readingTime": "4 min read",
  "pageCount": 3,
  "fileType": "pdf"
}
```

---

## My Approach

This application uses a **fully decoupled architecture**: a Java Spring Boot REST API handles all heavy computation, while a lightweight vanilla JS frontend provides the user interface. 

For PDF parsing, **Apache PDFBox** extracts text with paragraph-level formatting preserved. For scanned images, **Tess4J** (Java wrapper for Tesseract) performs OCR with automatic page segmentation. Both run server-side, keeping the frontend simple.

Summaries are generated by **Google Gemini 1.5 Flash** via a structured JSON-schema prompt that requests a summary, key points, and improvement suggestions in a single API call. The prompt is tuned with temperature 0.3 for consistent, deterministic output. Summary length is controlled by injecting word-count targets into the prompt.

The API key can be configured server-side via an environment variable (for production deployment) or passed per-request via an HTTP header (for the demo flow where users enter their own key in the UI). This makes the GitHub repository safe to publish without exposing credentials.

---

## Deployment

### Backend (Render.com — Free Tier)
1. Push to GitHub
2. Create new **Web Service** on [render.com](https://render.com)
3. Set **Build Command**: `cd backend && mvn package -DskipTests`
4. Set **Start Command**: `java -jar backend/target/doc-summary-assistant-1.0.0.jar`
5. Add environment variable: `GEMINI_API_KEY=<your-key>`

### Frontend (Netlify — Free Tier)
1. Drag the `frontend/` folder to [netlify.com/drop](https://app.netlify.com/drop)
2. Update `API_BASE_URL` in `app.js` to your Render backend URL

---

## Project Structure

```
Document Summary Assistant/
├── frontend/
│   ├── index.html          # UI shell
│   ├── style.css           # Design system
│   └── app.js              # Application logic
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/docsummary/
│       ├── DocSummaryApplication.java
│       ├── config/AppConfig.java
│       ├── controller/DocumentController.java
│       ├── model/SummaryResponse.java
│       └── service/
│           ├── PdfExtractorService.java
│           ├── OcrService.java
│           └── GeminiService.java
└── README.md
```

---

## License

MIT — free to use, modify, and distribute.
