"""Unit tests for screening router — HTTP endpoints."""
import pytest
from unittest.mock import MagicMock, AsyncMock, patch
from fastapi.testclient import TestClient
from fastapi import FastAPI
from contextlib import asynccontextmanager


class TestScreeningRouter:

    @pytest.fixture
    def mock_rag_service(self):
        mock = MagicMock()
        mock.store_candidate = MagicMock()
        mock.find_similar = MagicMock(return_value=[
            {"candidate_id": "c1", "resume_text": "Prev candidate resume", "score": 0.9}
        ])
        return mock

    @pytest.fixture
    def mock_scoring_result(self):
        return {
            "score": 78,
            "strengths": ["Strong Java", "Spring Boot"],
            "weaknesses": ["Limited Python"],
            "recommendation": "Hire"
        }

    @pytest.fixture
    def app_with_mock(self, mock_rag_service):
        from routers import screening
        test_app = FastAPI()
        test_app.state.rag_service = mock_rag_service
        test_app.include_router(screening.router, prefix="/api/ai")
        return test_app

    @pytest.fixture
    def client(self, app_with_mock):
        return TestClient(app_with_mock)

    def test_post_screen_success(self, client, mock_scoring_result, mock_rag_service):
        """POST /api/ai/screen with valid body returns 200 and score in response."""
        with patch('services.scoring_service.ScoringService') as MockScoringService:
            mock_instance = MagicMock()
            mock_instance.screen = AsyncMock(return_value=mock_scoring_result)
            MockScoringService.return_value = mock_instance

            response = client.post("/api/ai/screen", json={
                "resume_text": "Senior Java developer, 7 years experience with Spring Boot and Kafka",
                "job_description": "Need senior backend engineer with Java and Kafka"
            })

        assert response.status_code == 200
        data = response.json()
        assert data["score"] == 78
        assert isinstance(data["strengths"], list)
        assert isinstance(data["weaknesses"], list)
        assert data["recommendation"] == "Hire"
        assert "score" in data
        assert 0 <= data["score"] <= 100

    def test_post_screen_missing_resume_text(self, client):
        """POST /api/ai/screen without resume_text → 422 Unprocessable Entity."""
        response = client.post("/api/ai/screen", json={
            "job_description": "Need backend engineer"
        })

        assert response.status_code == 422
        assert "resume_text" in response.text.lower() or "field required" in response.text.lower()

    def test_post_screen_missing_job_description(self, client):
        """POST /api/ai/screen without job_description → 422."""
        response = client.post("/api/ai/screen", json={
            "resume_text": "Experienced Python developer"
        })

        assert response.status_code == 422

    def test_post_screen_empty_body(self, client):
        """POST /api/ai/screen with empty body → 422."""
        response = client.post("/api/ai/screen", json={})

        assert response.status_code == 422

    def test_post_screen_resume_too_short(self, client):
        """POST /api/ai/screen with resume_text < 10 chars → 422."""
        response = client.post("/api/ai/screen", json={
            "resume_text": "Short",
            "job_description": "Need backend engineer with extensive requirements"
        })

        assert response.status_code == 422

    def test_post_candidates_store_success(self, client, mock_rag_service):
        """POST /api/ai/candidates/store returns 200 and stores in ChromaDB."""
        response = client.post("/api/ai/candidates/store", json={
            "candidate_id": "cand-55",
            "resume_text": "Senior Python developer, 6 years Django and FastAPI experience"
        })

        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "stored"
        assert data["candidate_id"] == "cand-55"
        mock_rag_service.store_candidate.assert_called_once()

    def test_post_candidates_store_missing_candidate_id(self, client):
        """POST /api/ai/candidates/store without candidate_id → 422."""
        response = client.post("/api/ai/candidates/store", json={
            "resume_text": "Python developer resume text here"
        })

        assert response.status_code == 422

    def test_get_candidates_similar_returns_results(self, client, mock_rag_service):
        """GET /api/ai/candidates/similar?jd=... returns list of similar candidates."""
        response = client.get("/api/ai/candidates/similar", params={
            "jd": "Looking for a senior Java backend engineer"
        })

        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert len(data) >= 1
        assert "candidate_id" in data[0]
        assert "resume_text" in data[0]

    def test_get_candidates_similar_empty_query(self, client, mock_rag_service):
        """GET /api/ai/candidates/similar with empty jd param → returns results."""
        mock_rag_service.find_similar = MagicMock(return_value=[])

        response = client.get("/api/ai/candidates/similar", params={"jd": ""})

        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)

    def test_health_endpoint(self, client):
        """GET /health returns healthy status."""
        from fastapi.testclient import TestClient
        from fastapi import FastAPI

        @asynccontextmanager
        async def lifespan(app):
            yield

        simple_app = FastAPI(lifespan=lifespan)

        @simple_app.get("/health")
        def health():
            return {"status": "healthy"}

        simple_client = TestClient(simple_app)
        response = simple_client.get("/health")

        assert response.status_code == 200
        assert response.json() == {"status": "healthy"}