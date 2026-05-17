from fastapi import FastAPI
from contextlib import asynccontextmanager
from dotenv import load_dotenv

from routers import screening
from services.rag_service import RAGService
from kafka.consumer import start_consumer, stop_consumer

load_dotenv()


@asynccontextmanager
async def lifespan(app: FastAPI):
    rag_service = RAGService()
    app.state.rag_service = rag_service

    start_consumer(app.state.rag_service)

    yield

    stop_consumer()


app = FastAPI(
    title="SmartHire AI Service",
    version="1.0.0",
    lifespan=lifespan,
)

app.include_router(screening.router, prefix="/api/ai", tags=["AI Screening"])


@app.get("/health")
def health():
    return {"status": "healthy"}
