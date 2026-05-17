from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel, Field
from typing import Optional

router = APIRouter()


class ScreenRequest(BaseModel):
    resume_text: str = Field(..., min_length=10)
    job_description: str = Field(..., min_length=10)


class ScreenResponse(BaseModel):
    score: int = Field(..., ge=0, le=100)
    strengths: list[str]
    weaknesses: list[str]
    recommendation: str


class StoreCandidateRequest(BaseModel):
    candidate_id: str
    resume_text: str = Field(..., min_length=10)


class SimilarCandidateResponse(BaseModel):
    candidate_id: Optional[str]
    resume_text: str
    score: Optional[float]


@router.post("/screen", response_model=ScreenResponse)
async def screen_resume(request: Request, body: ScreenRequest):
    rag_service = request.app.state.rag_service
    from services.scoring_service import ScoringService

    scoring_service = ScoringService(rag_service)
    result = await scoring_service.screen(body.resume_text, body.job_description)

    return ScreenResponse(
        score=int(result.get("score", 50)),
        strengths=result.get("strengths", []),
        weaknesses=result.get("weaknesses", []),
        recommendation=result.get("recommendation", "Maybe"),
    )


@router.post("/candidates/store")
async def store_candidate(request: Request, body: StoreCandidateRequest):
    rag_service = request.app.state.rag_service
    rag_service.store_candidate(body.candidate_id, body.resume_text)
    return {"status": "stored", "candidate_id": body.candidate_id}


@router.get("/candidates/similar", response_model=list[SimilarCandidateResponse])
async def find_similar(request: Request, jd: str):
    rag_service = request.app.state.rag_service
    results = rag_service.find_similar(jd, top_k=3)
    return [
        SimilarCandidateResponse(
            candidate_id=r.get("candidate_id"),
            resume_text=r.get("resume_text", ""),
            score=r.get("score"),
        )
        for r in results
    ]
