import os
import google.generativeai as genai

def generate_summary(text: str) -> str:
    provider = os.getenv("LLM_PROVIDER", "gemini").lower()
    
    if provider == "gemini":
        api_key = os.getenv("LLM_API_KEY")
        if not api_key:
            print("WARNING: LLM_API_KEY not set. Using mock summary.")
            return f"Đây là bản tóm tắt mẫu (Mock). Nội dung trích xuất: {text[:100]}..."
            
        genai.configure(api_key=api_key)
        model = genai.GenerativeModel('gemini-pro')
        prompt = f"Hãy tóm tắt nội dung workshop sau đây trong 3-5 câu bằng tiếng Việt:\n\n{text}"
        response = model.generate_content(prompt)
        return response.text
    else:
        return f"Đây là bản tóm tắt mẫu (Mock). Nội dung trích xuất: {text[:100]}..."
