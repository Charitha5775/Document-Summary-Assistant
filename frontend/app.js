/**
 * DocSummary AI — Frontend Application
 *
 * Handles:
 *  - Drag-and-drop + file picker upload
 *  - REST calls to the Spring Boot backend (/api/process)
 *  - UI state machine: idle → processing → results | error
 *  - Copy to clipboard, re-summarize, new document
 */

'use strict';

// ── Configuration ─────────────────────────────────────────────────────────────
const API_BASE_URL = 'http://localhost:8080';

// ── DOM References ────────────────────────────────────────────────────────────
const $ = id => document.getElementById(id);

const dropZone          = $('drop-zone');
const fileInput         = $('file-input');
const filePreview       = $('file-preview');
const previewIcon       = $('preview-icon');
const previewName       = $('preview-name');
const previewMeta       = $('preview-meta');
const removeFileBtn     = $('remove-file');
const processBtn        = $('process-btn');

const uploadSection     = $('upload-section');
const processingSection = $('processing-section');
const processingTitle   = $('processing-title');
const processingSub     = $('processing-sub');
const progressFill      = $('progress-fill');
const resultsSection    = $('results-section');
const errorBanner       = $('error-banner');
const errorMsg          = $('error-msg');
const errorDismiss      = $('error-dismiss');


const summaryText       = $('summary-text');
const keyPointsList     = $('key-points-list');
const suggestionsList   = $('suggestions-list');
const extractedText     = $('extracted-text');
const copySummaryBtn    = $('copy-summary-btn');
const resummaryBtn      = $('resummary-btn');
const newDocBtn         = $('new-doc-btn');

const metaType    = $('meta-type');
const metaPages   = $('meta-pages');
const metaWords   = $('meta-words');
const metaReading = $('meta-reading');

// ── State ─────────────────────────────────────────────────────────────────────
let selectedFile      = null;
let lastExtractedText = '';
let dragCounter       = 0;  // tracks nested dragenter/dragleave to avoid flicker

// ── Initialisation ────────────────────────────────────────────────────────────
(function init() {
  bindEvents();
})();

// ── Event Binding ─────────────────────────────────────────────────────────────
function bindEvents() {
  // Drop zone interactions — ignore clicks that originate from the Remove button
  dropZone.addEventListener('click', e => {
    if (e.target.closest('#remove-file')) return;
    fileInput.click();
  });
  dropZone.addEventListener('keydown',   e => { if (e.key === 'Enter' || e.key === ' ') fileInput.click(); });
  dropZone.addEventListener('dragenter', handleDragEnter);
  dropZone.addEventListener('dragleave', handleDragLeave);
  dropZone.addEventListener('dragover',  handleDragOver);
  dropZone.addEventListener('drop',      handleDrop);
  fileInput.addEventListener('change',   e => setFile(e.target.files[0]));

  // Remove file
  removeFileBtn.addEventListener('click', e => { e.stopPropagation(); clearFile(); });

  // Process button
  processBtn.addEventListener('click', startProcessing);

  // Summary length radio — sync labels active state
  document.querySelectorAll('input[name="summary-length"]').forEach(radio => {
    radio.addEventListener('change', syncLengthLabels);
  });
  document.querySelectorAll('input[name="rs-length"]').forEach(radio => {
    radio.addEventListener('change', syncResummaryLabels);
  });

  // Re-summarize
  resummaryBtn.addEventListener('click', reSummarize);

  // Copy summary
  copySummaryBtn.addEventListener('click', copySummary);

  // New document
  newDocBtn.addEventListener('click', resetToIdle);

  // Error banner
  errorDismiss.addEventListener('click', hideError);
}

// ── Drag & Drop ───────────────────────────────────────────────────────────────
function handleDragEnter(e) {
  e.preventDefault();
  dragCounter++;
  dropZone.classList.add('drag-over');
}
function handleDragOver(e) {
  e.preventDefault();
  e.dataTransfer.dropEffect = 'copy';
}
function handleDragLeave(e) {
  dragCounter--;
  if (dragCounter === 0) {
    dropZone.classList.remove('drag-over');
  }
}
function handleDrop(e) {
  e.preventDefault();
  dragCounter = 0;
  dropZone.classList.remove('drag-over');
  const file = e.dataTransfer.files[0];
  if (file) setFile(file);
}

// ── File Management ───────────────────────────────────────────────────────────
function setFile(file) {
  if (!file) return;

  const allowed = ['application/pdf', 'image/jpeg', 'image/png',
                   'image/tiff', 'image/webp', 'image/bmp', 'image/gif'];
  if (!allowed.includes(file.type)) {
    showError(`Unsupported file type: "${file.type}". Please upload a PDF or image.`);
    return;
  }
  if (file.size > 20 * 1024 * 1024) {
    showError('File is too large. Maximum size is 20 MB.');
    return;
  }

  selectedFile = file;
  hideError();

  // Show preview
  previewIcon.textContent = file.type === 'application/pdf' ? '📄' : '🖼️';
  previewName.textContent = file.name;
  previewMeta.textContent = `${formatFileSize(file.size)} · ${file.type}`;
  filePreview.hidden = false;
  processBtn.disabled = false;

  // Animate drop zone out slightly
  dropZone.style.opacity = '0.55';
}

function clearFile() {
  selectedFile = null;
  fileInput.value = '';
  filePreview.hidden = true;
  processBtn.disabled = true;
  dropZone.style.opacity = '';
}

// ── Processing ────────────────────────────────────────────────────────────────
async function startProcessing() {
  if (!selectedFile) return;

  const length = getSelectedLength('summary-length');

  // Switch to processing view
  showSection('processing');
  setProgress(10, 'Uploading document…', 'Sending file to the backend…');

  try {
    const formData = new FormData();
    formData.append('file', selectedFile);
    formData.append('length', length);

    setProgress(30, 'Extracting text…',
      selectedFile.type === 'application/pdf'
        ? 'Reading PDF pages with Apache PDFBox…'
        : 'Running OCR on image with Tesseract…');

    const response = await fetch(`${API_BASE_URL}/api/process`, {
      method:  'POST',
      body:    formData,
    });

    setProgress(75, 'Generating summary…', 'Calling OpenRouter AI…');

    const data = await response.json();

    if (!response.ok) {
      throw new Error(data.error || `Server error: ${response.status}`);
    }

    setProgress(100, 'Done!', '');
    await delay(400); // brief pause so users see 100%

    renderResults(data);
    showSection('results');

  } catch (err) {
    console.error('Processing failed:', err);
    showSection('upload');
    showError(err.message || 'An unexpected error occurred. Please try again.');
  }
}

async function reSummarize() {
  if (!lastExtractedText) return;
  const length = getSelectedLength('rs-length');

  resummaryBtn.disabled = true;
  resummaryBtn.textContent = '↻ Working…';

  try {
    // Instead, call with original file if still available
    if (!selectedFile) throw new Error('Original file no longer available. Please re-upload.');

    const formData = new FormData();
    formData.append('file', selectedFile);
    formData.append('length', length);

    const response = await fetch(`${API_BASE_URL}/api/process`, {
      method: 'POST', body: formData,
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || `Server error: ${response.status}`);

    renderResults(data);
  } catch (err) {
    showError(err.message);
  } finally {
    resummaryBtn.disabled = false;
    resummaryBtn.textContent = '↻ Re-summarise';
  }
}

// ── Rendering ─────────────────────────────────────────────────────────────────
function renderResults(data) {
  lastExtractedText = data.extractedText || '';

  // Meta chips
  metaType.textContent    = data.fileType === 'pdf' ? '📄 PDF' : '🖼️ Image';
  metaPages.textContent   = `${data.pageCount} page${data.pageCount !== 1 ? 's' : ''}`;
  metaWords.textContent   = `${data.wordCount.toLocaleString()} words`;
  metaReading.textContent = data.readingTime;

  // Summary
  summaryText.textContent = data.summary || 'No summary available.';

  // Key points
  keyPointsList.innerHTML = '';
  (data.keyPoints || []).forEach((point, i) => {
    const li = document.createElement('li');
    li.textContent = point;
    li.style.animationDelay = `${i * 80}ms`;
    keyPointsList.appendChild(li);
  });

  // Suggestions
  suggestionsList.innerHTML = '';
  (data.suggestions || []).forEach((sug, i) => {
    const li = document.createElement('li');
    li.textContent = sug;
    li.style.animationDelay = `${i * 80}ms`;
    suggestionsList.appendChild(li);
  });

  // Extracted text
  extractedText.textContent = data.extractedText || 'No text extracted.';
}

// ── UI State Machine ──────────────────────────────────────────────────────────
function showSection(name) {
  uploadSection.hidden     = name !== 'upload';
  processingSection.hidden = name !== 'processing';
  resultsSection.hidden    = name !== 'results';
}

function setProgress(pct, title, sub) {
  progressFill.style.width = `${pct}%`;
  processingTitle.textContent = title;
  processingSub.textContent   = sub;
}

function resetToIdle() {
  clearFile();
  showSection('upload');
  hideError();
  lastExtractedText = '';
  summaryText.textContent = '';
  keyPointsList.innerHTML = '';
  suggestionsList.innerHTML = '';
  extractedText.textContent = '';
  setProgress(0, '', '');
}

// ── Error Handling ────────────────────────────────────────────────────────────
function showError(msg) {
  errorMsg.textContent = msg;
  errorBanner.hidden = false;
  errorBanner.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}
function hideError() { errorBanner.hidden = true; }

// ── Helpers ────────────────────────────────────────────────────────────────────
function getSelectedLength(groupName) {
  const checked = document.querySelector(`input[name="${groupName}"]:checked`);
  return checked ? checked.value : 'medium';
}

function syncLengthLabels() {
  document.querySelectorAll('input[name="summary-length"]').forEach(radio => {
    const label = document.querySelector(`label[for="${radio.id}"]`);
    if (label) label.classList.toggle('length-btn--active', radio.checked);
  });
}
function syncResummaryLabels() {
  document.querySelectorAll('input[name="rs-length"]').forEach(radio => {
    const label = document.querySelector(`label[for="${radio.id}"]`);
    if (label) label.classList.toggle('length-btn--active', radio.checked);
  });
}

async function copySummary() {
  const text = summaryText.textContent;
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    const orig = copySummaryBtn.textContent;
    copySummaryBtn.textContent = 'Copied ✓';
    setTimeout(() => { copySummaryBtn.textContent = orig; }, 1800);
  } catch {
    showError('Clipboard access denied. Please copy manually.');
  }
}

function formatFileSize(bytes) {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function delay(ms) { return new Promise(r => setTimeout(r, ms)); }

function showToast(msg) {
  const toast = document.createElement('div');
  toast.textContent = msg;
  Object.assign(toast.style, {
    position: 'fixed', bottom: '24px', left: '50%',
    transform: 'translateX(-50%)',
    background: 'rgba(12,17,30,0.95)',
    border: '1px solid rgba(99,102,241,0.4)',
    color: '#f0f2ff',
    padding: '10px 20px',
    borderRadius: '100px',
    fontFamily: 'Inter, sans-serif',
    fontSize: '0.875rem',
    fontWeight: '600',
    zIndex: '9999',
    boxShadow: '0 4px 24px rgba(0,0,0,0.4)',
    animation: 'fadeIn 0.3s ease',
    pointerEvents: 'none',
  });
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 2000);
}
