# DocSummary AI 📄✨

> An AI-powered document summarization assistant that extracts text from PDFs and scanned images, then generates smart summaries, key points, and structural improvement suggestions using OpenRouter.

![Tech Stack](https://img.shields.io/badge/Backend-Java%2017%20%2B%20Spring%20Boot%203-brightgreen)
![PDF](https://img.shields.io/badge/PDF-Apache%20PDFBox-red)
![OCR](https://img.shields.io/badge/OCR-Tesseract%20%2F%20Tess4J-blue)
![AI](https://img.shields.io/badge/AI-OpenRouter%20%28stealth%2Fox--alpha%29-emerald)

---

## Features

| Feature | Description |
|---|---|
| 📄 **PDF Upload** | Full text extraction preserving document structure |
| 🖼️ **Image OCR** | Tesseract-powered OCR for scanned documents (JPEG, PNG, TIFF, WEBP) |
| 🤖 **AI Summary** | Powered by OpenRouter using `stealth/ox-alpha` for key insights |
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
│               │ OpenRouter   │          │
│               │ API          │          │
│               └──────────────┘          │
└─────────────────────────────────────────┘
```

---

## Prerequisites

| Tool | Version | Download / Source |
|---|---|---|
| Java | 17+ | [adoptium.net](https://adoptium.net) |
| Maven | 3.8+ | [maven.apache.org](https://maven.apache.org) |
| Tesseract | 5.x | [github.com/tesseract-ocr](https://github.com/tesseract-ocr/tesseract/releases) |
| OpenRouter Key | Free / Paid | [openrouter.ai](https://openrouter.ai/keys) |

---

## Setup & Running

### 1. Install Tesseract OCR (required for image files)

**Windows:**
1. Download and run installer: [UB-Mannheim Tesseract Wiki](https://github.com/UB-Mannheim/tesseract/wiki)
2. Default install path: `C:\Program Files\Tesseract-OCR`
3. Ensure the English language data is checked during installation.

**macOS:**
```bash
brew install tesseract
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt install tesseract-ocr
```

### 2. Configure Environment Variables

Create a `.env` file in the `backend/` directory of the project (copy `.env.example` as a starting point) and update the credentials:

```ini
# OpenRouter Credentials
OPENROUTER_API_KEY=your_openrouter_api_key_here
OPENROUTER_MODEL=stealth/ox-alpha

# Tesseract Data path (Windows default shown)
TESSDATA_PREFIX=C:\Program Files\Tesseract-OCR\tessdata
```

### 3. Run the Backend

```bash
cd backend
mvn spring-boot:run -DskipTests
```

The backend starts at **http://localhost:8080**

### 4. Open the Frontend

Simply serve the `frontend/` directory using any local static web server, or open `frontend/index.html` directly in your browser:

```bash
# Using Python to host locally
cd frontend
python -m http.server 3000
# Open http://localhost:3000
```

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

This application utilizes a decoupled, modern architecture: a Java Spring Boot REST API handles document parsers and OCR, while a lightweight glassmorphic dashboard provides the frontend.

### Document Parsing & OCR
* **PDF processing**: **Apache PDFBox** is used to extract textual content while preserving the structure and page breaks.
* **Scanned Images**: **Tess4J** (the JNA wrapper for Tesseract OCR) is configured to handle images (JPEG, PNG, TIFF, WEBP), parsing scanned text in real-time.

### OpenRouter Migration & Large Language Model
* Migrated from Google Gemini to the **OpenRouter API** using the `stealth/ox-alpha` model to run smart summaries.
* Interfaced using an OpenAI-compatible request structure, sending prompts designed to enforce structured JSON output.
* Optimized response handling with a high `max_tokens` limit of `4096` to ensure summaries are not cut off mid-response.

### Robust Rate-Limit Handling (429 Mitigation)
* Implemented automatic retry handling with exponential backoff for `429 Too Many Requests` responses.
* The system respects OpenRouter's `Retry-After` header when provided, automatically pausing execution before retrying the call (up to 4 times) for high reliability.

### UI Overhaul ("Verdant Night" Theme)
* Replaced the standard blue/violet interface with an **Emerald Green, Teal, and Amber** dark dashboard layout.
* Incorporated a high-fidelity **3D glassmorphic document icon** in the drag-and-drop zone with animated float transitions and green neon drop-shadow filters on hover.
* The **Extracted Text** section is permanently visible directly below the summary results rather than hidden in a collapsible container, ensuring immediate readability.

---

## Deployment

### Backend (Render.com)
1. Create a new **Web Service** on [render.com](https://render.com)
2. Set **Build Command**: `cd backend && mvn package -DskipTests`
3. Set **Start Command**: `java -jar backend/target/doc-summary-assistant-1.0.0.jar`
4. Add environment variables in Render's dashboard:
   - `OPENROUTER_API_KEY` = `<your_key>`
   - `OPENROUTER_MODEL` = `stealth/ox-alpha`

### Frontend (Netlify / Vercel)
1. Deploy the `frontend/` folder to your static provider of choice.
2. Update the `API_BASE_URL` in `frontend/app.js` to point to your live backend endpoint.

---

## Project Structure

```
Document Summary Assistant/
├── frontend/
│   ├── index.html          # HTML shell
│   ├── style.css           # Custom design styles
│   ├── app.js              # State machine and REST connections
│   └── drag_drop_icon.jpg  # 3D glassmorphic upload graphic
├── backend/
│   ├── pom.xml
│   ├── .env.example
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
