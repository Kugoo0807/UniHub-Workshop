import re

def clean_text(text: str) -> str:
    text = re.sub(r'\s+', ' ', text)
    text = text.replace('"', "'")
    
    max_chars = 15000
    if len(text) > max_chars:
        text = text[:max_chars]
        
    return text.strip()
