import logging
import os

import google.generativeai as genai
from google.api_core.exceptions import GoogleAPICallError, NotFound

logger = logging.getLogger(__name__)


def _normalize_model_name(model_name: str) -> str:
    model_name = (model_name or "").strip()
    if model_name.startswith("models/"):
        return model_name[len("models/") :]
    return model_name


def _pick_fallback_model(preferred_model: str) -> str:
    preferred_model = _normalize_model_name(preferred_model)

    try:
        models = list(genai.list_models())
    except Exception as exc:
        logger.warning("Could not list Gemini models for fallback selection: %s", exc)
        return preferred_model

    supported = []
    for m in models:
        supported_methods = getattr(m, "supported_generation_methods", None) or []
        if "generateContent" not in supported_methods:
            continue

        name = getattr(m, "name", "")
        normalized = _normalize_model_name(name)
        if normalized:
            supported.append(normalized)

    if not supported:
        return preferred_model

    if preferred_model in supported:
        return preferred_model

    flash = [m for m in supported if "flash" in m.lower()]
    if flash:
        return flash[0]

    return supported[0]


def generate_summary(text: str) -> str:
    provider = os.getenv("LLM_PROVIDER", "gemini").lower()

    if provider != "gemini":
        return f"{text[:100]}..."

    api_key = os.getenv("LLM_API_KEY")
    if not api_key:
        logger.warning("LLM_API_KEY not set. Using mock summary.")
        return f"{text[:100]}..."

    genai.configure(api_key=api_key)

    configured_model = os.getenv("LLM_MODEL", "gemini-flash-latest")
    model_name = _normalize_model_name(configured_model)
    prompt = f"Hãy tóm tắt nội dung workshop sau đây trong 3-5 câu bằng tiếng Việt:\n\n{text}"

    def _call_model(name: str) -> str:
        model = genai.GenerativeModel(_normalize_model_name(name))
        response = model.generate_content(prompt)
        return getattr(response, "text", "") or ""

    try:
        return _call_model(model_name)
    except NotFound as exc:
        fallback = _pick_fallback_model(model_name)
        if fallback and fallback != model_name:
            logger.warning(
                "Gemini model '%s' not found; retrying with '%s'. Original error: %s",
                model_name,
                fallback,
                exc,
            )
            return _call_model(fallback)

        logger.error("Gemini model '%s' not found and no fallback available: %s", model_name, exc)
        raise
    except GoogleAPICallError as exc:
        logger.error("Gemini API call failed: %s", exc)
        raise
