from fastapi import FastAPI
from routers import summarize

app = FastAPI(title="AI Summary Service")

app.include_router(summarize.router)

@app.get("/health")
def health_check():
    return {"status": "ok"}
