from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from fastapi.responses import JSONResponse
from services.pdf_extractor import extract_text_from_pdf
from services.text_cleaner import clean_text
from services.llm_client import LLMClientError, generate_summary
import io

router = APIRouter()

@router.post("/summarize")
async def summarize_pdf(
    workshop_id: int = Form(...),
    file: UploadFile = File(...)
):
    try:
        contents = await file.read()
        pdf_file = io.BytesIO(contents)
        
        raw_text = extract_text_from_pdf(pdf_file)
        if not raw_text or len(raw_text.strip()) == 0:
            return JSONResponse(
                status_code=400,
                content={"error": "Could not extract text from PDF file."}
            )
            
        cleaned_text = clean_text(raw_text)
        summary = generate_summary(cleaned_text)
        
        return {
            "workshop_id": workshop_id,
            "summary": summary
        }
    except LLMClientError as e:
        raise HTTPException(status_code=e.status_code, detail=e.detail)
    except Exception as e:
        print(f"Error processing PDF for workshop {workshop_id}: {str(e)}")
        raise HTTPException(status_code=500, detail="Internal server error")
