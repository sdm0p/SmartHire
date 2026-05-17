"""Test configuration and shared fixtures for SmartHire AI service tests."""
import pytest
from unittest.mock import MagicMock, AsyncMock, patch
from fastapi.testclient import TestClient
from fastapi import FastAPI
from services.rag_service import RAGService
from services.scoring_service import ScoringService


@pytest.fixture
def mock_rag_service():
    """Mock RAGService with ChromaDB and LLM mocked out."""
    mock = MagicMock(spec=RAGService)
    mock.store_candidate = MagicMock()
    mock.find_similar = MagicMock(return_value=[
        {
            "candidate_id": "prev-1",
            "resume_text": "Senior Java developer with 8 years experience",
            "score": 0.95,
        }
    ])
    return mock


@pytest.fixture
def mock_llm_response():
    """Returns a valid JSON LLM response string."""
    return """{
        "score": 82,
        "strengths": ["Strong Java background", "Spring Boot expertise", "Kafka experience"],
        "weaknesses": ["Limited Python exposure", "No AWS mentioned"],
        "recommendation": "Hire"
    }"""


@pytest.fixture
def mock_invalid_json_response():
    """Returns an LLM response that is not valid JSON."""
    return "The candidate looks good. I would rate them around 80 out of 100."


@pytest.fixture
def app(mock_rag_service):
    """Create a FastAPI app with mocked RAG service for testing."""
    from routers import screening
    from contextlib import asynccontextmanager

    @asynccontextmanager
    async def mock_lifespan(app):
        app.state.rag_service = mock_rag_service
        yield

    test_app = FastAPI(title="SmartHire AI Service Test", lifespan=mock_lifespan)
    test_app.include_router(screening.router, prefix="/api/ai")
    return test_app


@pytest.fixture
def client(app):
    """TestClient for FastAPI routes."""
    return TestClient(app)


@pytest.fixture
def sample_screen_request():
    """Valid screening request body."""
    return {
        "resume_text": "Senior Software Engineer with 7 years of Java experience, expert in Spring Boot 3.x, PostgreSQL, and Kafka. Led team of 5 engineers.",
        "job_description": "We need a Senior Backend Engineer with Java 17+, Spring Boot, Kafka, and 5+ years experience."
    }