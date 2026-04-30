import PyPDF2
import io

def extract_text_from_pdf(pdf_file: io.BytesIO) -> str:
    try:
        reader = PyPDF2.PdfReader(pdf_file)
        text = ""
        for page in reader.pages:
            page_text = page.extract_text()
            if page_text:
                text += page_text + "\n"
        return text
    except Exception as e:
        print(f"Error extracting text: {e}")
        return ""
