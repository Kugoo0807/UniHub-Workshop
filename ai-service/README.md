# UniHub Workshop - AI Service

This microservice is responsible for processing PDF documents and generating automated summaries for workshops using Google's Gemini LLM. It is built with **FastAPI** and is designed to run completely asynchronously.

## Features
- **PDF Text Extraction**: Extracts raw text content from uploaded PDF files.
- **Text Cleaning**: Cleans and normalizes text for better LLM processing.
- **AI Summarization**: Uses Google Gemini API to generate concise, professional workshop descriptions.

## Technology Stack
- **Framework**: FastAPI (Python)
- **PDF Processing**: PyPDF2
- **LLM Integration**: `google-generativeai` (Gemini)
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
LLM_MODEL=gemini-flash-latest
```

If you see a `404 ... model is not found` error, the model name is not available for your API key/version. You can set `LLM_MODEL` to a model returned by `genai.list_models()` that supports `generateContent`.

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
- `500 Internal Server Error`: If there is an issue with the AI generation process.
