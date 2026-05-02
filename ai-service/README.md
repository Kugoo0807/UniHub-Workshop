# UniHub Workshop - AI Service

This microservice is responsible for processing PDF documents and generating automated summaries for workshops using Google's Gemini LLM. It is built with **FastAPI** and is designed to run completely asynchronously.

## Features
- **PDF Text Extraction**: Extracts raw text content from uploaded PDF files.
- **Text Cleaning**: Cleans and normalizes text for better LLM processing.
- **AI Summarization**: Uses Google Gemini API to generate concise, professional workshop descriptions.

## Technology Stack
- **Framework**: FastAPI (Python)
- **PDF Processing**: PyPDF2
- **LLM Integration**: `google-genai` (Gemini)
- **Deployment**: Docker & Docker Compose

## Prerequisites
- Python 3.9+ (if running locally)
- Docker & Docker Compose (recommended)
- A valid Google Gemini API Key

## Environment Variables
Create a `.env` file in the root directory (or in this directory) with the following variables:

```env
LLM_PROVIDER=gemini
LLM_API_KEY=your_gemini_api_key_here
LLM_MODEL=gemini-3.1-flash-lite
# Use stable API to avoid v1beta model visibility issues
LLM_API_VERSION=v1
# Optional: fail fast if the selected model is not available (no fallback)
LLM_STRICT_MODEL=true
# Optional: explicit fallback models to try (comma-separated, in order)
# LLM_FALLBACK_MODELS=gemini-3.1-flash,gemini-2.5-flash
```

If you see a `404 ... model is not found` error, the model name is not available for your API key/version. You can set `LLM_MODEL` to a model returned by `genai.list_models()` that supports `generateContent`.

If you see unexpected fallbacks, set `LLM_STRICT_MODEL=true` to fail fast, or set `LLM_FALLBACK_MODELS` to an explicit ordered list.

## Running the Service

### Option 1: Using Docker (Recommended)
You can build and run this service along with the rest of the application using the root `docker-compose.yml` file:

```bash
cd ../ # Go to project root
docker-compose up --build ai-service
```
The service will be available at `http://localhost:8000`.

### Option 2: Running Locally (Without Docker)
Navigate to the `ai-service` directory and install the dependencies:

```bash
cd ai-service
pip install -r requirements.txt
```

Run the FastAPI server using Uvicorn:

```bash
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

## API Endpoints

### 1. `POST /summarize`
Receives a PDF file and a Workshop ID, processes it, and returns the generated summary.

**Request Form Data:**
- `workshop_id` (integer): The ID of the workshop.
- `file` (file): The PDF document to summarize.

**Success Response (200 OK):**
```json
{
  "workshop_id": 1,
  "summary": "This is the generated summary describing the workshop..."
}
```

**Error Responses:**
- `400 Bad Request`: If the PDF cannot be read or text extraction fails.
- `404 Not Found`: If the configured model is not available for your API key/API version.
- `429 Too Many Requests`: If your Gemini API quota is exhausted.
- `500 Internal Server Error`: For unexpected server errors.
