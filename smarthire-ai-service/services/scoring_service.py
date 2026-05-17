from langchain_core.prompts import ChatPromptTemplate
from pydantic import BaseModel
from services.rag_service import RAGService


SYSTEM_PROMPT = """You are an expert recruitment analyst. Evaluate the resume against the job description.
Return a JSON object with:
- score (0-100): Overall match score based on skills, experience, and requirements alignment
- strengths (list): Key strengths of the candidate relative to the job
- weaknesses (list): Areas where the candidate is lacking or unclear
- recommendation (string): One of "Strong Hire", "Hire", "Maybe", "No Hire"
"""


class ScreeningResult(BaseModel):
    score: int
    strengths: list[str]
    weaknesses: list[str]
    recommendation: str


class ScoringService:
    def __init__(self, rag_service: RAGService):
        self.rag_service = rag_service
        self.llm = rag_service.llm

    async def screen(self, resume_text: str, job_description: str) -> dict:
        similar_candidates = self.rag_service.find_similar(job_description, top_k=3)

        context = ""
        if similar_candidates:
            context = "Similar successful candidates for reference:\n" + "\n".join(
                f"- Candidate {c['candidate_id']}: {c['resume_text'][:300]}..."
                for c in similar_candidates
            )

        prompt = f"""Resume:
{resume_text}

Job Description:
{job_description}

{context}

Evaluate this candidate and return JSON with score (0-100), strengths[], weaknesses[], recommendation (Strong Hire/Hire/Maybe/No Hire)."""

        messages = [
            ("system", SYSTEM_PROMPT),
            ("human", prompt),
        ]
        prompt_template = ChatPromptTemplate.from_messages(messages)
        chain = prompt_template | self.llm

        response = await chain.ainvoke({})
        content = response.content.strip()

        if content.startswith("```json"):
            content = content[7:]
        if content.startswith("```"):
            content = content[3:]
        if content.endswith("```"):
            content = content[:-3]

        import json

        try:
            result = json.loads(content)
        except json.JSONDecodeError:
            import re

            json_match = re.search(r"\{.*\}", content, re.DOTALL)
            if json_match:
                result = json.loads(json_match.group())
            else:
                result = {
                    "score": 50,
                    "strengths": ["Could not parse full analysis"],
                    "weaknesses": ["Parse error"],
                    "recommendation": "Maybe",
                }

        return result
