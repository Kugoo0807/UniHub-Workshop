import logging
import os
import re
import google.generativeai as genai
from google.api_core.exceptions import GoogleAPICallError, NotFound

logger = logging.getLogger(__name__)

# --- Constants ---
# Max attempts for network issues.
RETRY_STOP_AFTER_ATTEMPT = int(os.getenv("LLM_RETRY_ATTEMPTS", "3"))

class LLMClientError(Exception):
    def __init__(self, status_code: int, detail: str):
        super().__init__(detail)
        self.status_code = status_code
        self.detail = detail


def _strip_summary_preamble(summary: str) -> str:
    if not summary:
        return summary

    summary = summary.strip()

    # Remove common Vietnamese preambles that LLMs may add.
    summary = re.sub(
        r"^\s*(?:Dưới đây là|Duoi day la|Sau đây là|Sau day la)\s+(?:bản\s+)?tóm tắt[^\n:]*workshop\s*[:：.\-]\s*",
        "",
        summary,
        flags=re.IGNORECASE,
    )

    lines = summary.splitlines()
    while lines and not lines[0].strip():
        lines.pop(0)

    if lines:
        first = lines[0].strip()
        if re.match(
            r"^(?:Dưới đây là|Duoi day la|Sau đây là|Sau day la)\s+(?:bản\s+)?tóm tắt[^\n]*workshop\s*[:：.]?\s*$",
            first,
            flags=re.IGNORECASE,
        ) or re.match(
            r"^(?:Bản\s+)?tóm tắt\s+(?:nội dung\s+)?workshop\s*[:：.]?\s*$",
            first,
            flags=re.IGNORECASE,
        ):
            lines.pop(0)

    return "\n".join(lines).strip()


def _normalize_model_name(model_name: str) -> str:
    model_name = (model_name or "").strip()
    if model_name.startswith("models/"):
        return model_name[len("models/") :]
    return model_name


def _pick_fallback_model(preferred_model: str) -> str:
    preferred_model = _normalize_model_name(preferred_model)
    
    try:
        models = list(genai.list_models())
        supported = []
        for m in models:
            if "generateContent" in m.supported_generation_methods:
                supported.append(_normalize_model_name(m.name))
        
        if not supported:
            return preferred_model
        if preferred_model in supported:
            return preferred_model
        
        # Priority fallback
        flash_models = [m for m in supported if "flash" in m.lower()]
        if flash_models:
            return flash_models[0]
        return supported[0]
    except Exception as exc:
        logger.warning("Could not list models for fallback: %s", exc)
        return preferred_model


def generate_summary(text: str) -> str:
    provider = os.getenv("LLM_PROVIDER", "gemini").lower()
    if provider != "gemini":
        return f"{text[:100]}..."

    api_key = os.getenv("LLM_API_KEY") or os.getenv("GEMINI_API_KEY")
    if not api_key:
        logger.warning("LLM_API_KEY not set. Using mock summary.")
        return f"{text[:100]}..."

    genai.configure(api_key=api_key)

    configured_model = os.getenv("LLM_MODEL", "gemini-1.5-flash")
    model_name = _normalize_model_name(configured_model)
    
    # Prompt chuyên nghiệp từ bản trên
    prompt = (
        "Bạn là trợ lý viết tóm tắt. Hãy tóm tắt nội dung workshop sau đây trong 3-5 câu bằng tiếng Việt. "
        "Chỉ trả về phần tóm tắt, không thêm câu mở đầu, không tiêu đề.\n\n"
        f"{text}"
    )

    def _call_model(name: str) -> str:
        logger.info("Generating summary using model: %s", name)
        model = genai.GenerativeModel(name)
        response = model.generate_content(prompt)
        return _strip_summary_preamble(getattr(response, "text", "") or "")

    try:
        return _call_model(model_name)
    except NotFound as exc:
        fallback = _pick_fallback_model(model_name)
        if fallback and fallback != model_name:
            logger.warning("Model '%s' not found, falling back to '%s'", model_name, fallback)
            return _call_model(fallback)
        raise LLMClientError(404, f"Model not found: {str(exc)}")
    except GoogleAPICallError as exc:
        logger.error("Gemini API call failed: %s", exc)
        raise LLMClientError(500, f"AI service error: {str(exc)}")
    except Exception as exc:
        logger.error("Unexpected error in AI service: %s", exc)
        raise LLMClientError(500, str(exc))