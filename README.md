<div align="center">
  <h1>🤖 SmartHire</h1>
  <h3>AI-Powered Recruitment Automation Platform</h3>
  <p>
    <strong>Spring Boot · FastAPI · LangChain · Groq LLM · ChromaDB · Kafka</strong>
  </p>
  <p>
    <img src="https://img.shields.io/badge/Java-17+-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java">
    <img src="https://img.shields.io/badge/Python-3.11+-3776AB?style=flat-square&logo=python&logoColor=white" alt="Python">
    <img src="https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot">
    <img src="https://img.shields.io/badge/FastAPI-0.115-009688?style=flat-square&logo=fastapi&logoColor=white" alt="FastAPI">
    <img src="https://img.shields.io/badge/Kafka-7.4-231F20?style=flat-square&logo=apachekafka&logoColor=white" alt="Kafka">
    <img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker">
  </p>
</div>

---

SmartHire automates resume screening at scale using a **Retrieval-Augmented Generation (RAG)** pipeline powered by Groq's Llama3 LLM. A **Spring Boot 3.2** backend orchestrates candidates, jobs, and authentication while a **Python FastAPI** microservice handles AI-powered scoring via LangChain — enabling recruiters to focus on hiring decisions instead of manual resume review.

The system processes screening requests both **synchronously** (WebClient → Groq LLM for immediate scoring) and **asynchronously** (Kafka → AI Service → ChromaDB for persistent RAG embeddings), giving you low-latency responses alongside a growing semantic candidate database.

---

## Architecture

```
┌──────────────┐       ┌──────────────────────┐       ┌──────────────────┐
│              │       │                      │       │                  │
│  React UI    │──────▶│   Spring Boot API    │──────▶│   PostgreSQL 15  │
│  (Frontend)  │       │      :8080           │       │   (Relational)   │
│              │       │                      │       │                  │
└──────────────┘       └──────────┬───────────┘       └──────────────────┘
                                  │
                    ┌─────────────┴──────────────┐
                    │                            │
                    ▼ Sync (WebClient)           ▼ Async (Kafka)
                    │                            │
          ┌──────────────────────┐    ┌──────────────────────────┐
          │                      │    │                          │
          │    Groq Llama3       │    │   Apache Kafka           │
          │    (LLM Inference)   │    │   resume-screening topic │
          │                      │    │                          │
          └──────────────────────┘    └────────────┬─────────────┘
                                                    │
                                                    ▼ Consumer
                                          ┌──────────────────────────┐
                                          │                          │
                                          │   FastAPI AI Service     │
                                          │      :8000               │
                                          │   LangChain + Groq      │
                                          │                          │
                                          └────────────┬─────────────┘
                                                        │
                                                        ▼
                                          ┌──────────────────────────┐
                                          │                          │
                                          │   ChromaDB              │
                                          │   (Vector Store — RAG)  │
                                          │                          │
                                          └──────────────────────────┘
```

### Screening Flow

```
1. POST /api/candidates/{id}/screen/{jobId}
       │
2. Backend calls AI service via WebClient ──────▶ Groq Llama3 ──▶ Return score
       │
3. Backend publishes CandidateEvent to Kafka ──▶ topic: resume-screening
       │
4. AI service consumes event (background thread)
       │
5. Computes embedding via HuggingFace (sentence-transformers)
       │
6. Stores embedding + metadata in ChromaDB (RAG corpus)
       │
7. Candidate record updated: status=SCREENED, aiScore, recommendation
```

---

## Tech Stack

| Component | Technology | Role |
|-----------|------------|------|
| **Backend** | Java 17, Spring Boot 3.2, Maven | REST API, JPA, Kafka producer, JWT auth |
| **AI Service** | Python 3.11, FastAPI, Uvicorn | LLM orchestration, embedding management |
| **AI Framework** | LangChain | Chain composition, prompt templates, structured output |
| **LLM** | Groq API (llama3-8b-8192 — free tier) | Resume scoring inference |
| **Vector Store** | ChromaDB (persistent, disk-based) | Semantic candidate embeddings for RAG |
| **Embedding Model** | sentence-transformers (HuggingFace) | Text-to-vector embedding generation |
| **Message Broker** | Apache Kafka 7.4 (Confluent) | Async event pipeline (resume-screening topic) |
| **Database** | PostgreSQL 15 | Relational store: candidates, jobs, users |
| **Auth** | JWT (jjwt 0.12, HS256) | Stateless authentication, RECRUITER role |
| **Containerization** | Docker, Docker Compose | Multi-service orchestration with healthchecks |
| **API Docs** | SpringDoc OpenAPI (Swagger UI), FastAPI /docs | Interactive API exploration |

---

## Features

- **AI Resume Scoring** — LLM-powered evaluation against job descriptions returning a 0–100 score, strengths, weaknesses, and a `Strong Hire / Hire / Maybe / No Hire` recommendation.
- **RAG-Based Candidate Matching** — Semantic similarity search across previously screened candidates to surface relevant past evaluations and inform hiring decisions.
- **Dual Sync + Async Pipeline** — Synchronous WebClient call for immediate scoring, plus async Kafka event for non-blocking embedding persistence in ChromaDB.
- **JWT Authentication** — Role-based access control (`RECRUITER`) protecting all `/api/**` endpoints with HS256-signed tokens.
- **Full CRUD REST APIs** — Candidate management, job posting, paginated listing, and screening orchestration.
- **Interactive API Docs** — Swagger UI at `/swagger-ui.html` (backend) and `/docs` (AI service).
- **Docker Compose** — One-command local environment with dependency ordering, healthchecks, and persistent volumes.
- **Extensible Architecture** — Decoupled services communicating over Kafka; swap in any LLM provider or vector store with minimal changes.

---

## Getting Started

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) 20.10+ and [Docker Compose](https://docs.docker.com/compose/install/) v2.0+
- A Groq API key — [sign up free at console.groq.com](https://console.groq.com) (the `llama3-8b-8192` model has a generous free tier)

### Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/your-org/smarthire.git
cd smarthire

# 2. Configure environment variables
cp .env.example .env
# Edit .env and set:
#   GROQ_API_KEY=gsk_your_key_here
#   JWT_SECRET=your-256-bit-secret-key

# 3. Launch everything
docker compose up --build
```

That's it. Docker Compose starts the five services in dependency order with built-in healthchecks:

| Service | Port | Healthcheck | Depends On |
|---------|------|-------------|------------|
| PostgreSQL | 5432 | `pg_isready` | — |
| Zookeeper | 2181 | — | — |
| Kafka | 9092 | `kafka-topics --list` | Zookeeper |
| AI Service | 8000 | `curl /health` | Kafka (healthy) |
| Backend | 8080 | Actuator `/actuator/health` | PostgreSQL + Kafka + AI Service |

### Verify It Works

```bash
# Check all services are healthy
curl http://localhost:8080/actuator/health
curl http://localhost:8000/health

# Explore the APIs
open http://localhost:8080/swagger-ui.html   # Backend Swagger UI
open http://localhost:8000/docs              # AI Service docs
```

### Local Development (without Docker)

**Backend:**
```bash
cd smarthire-backend
./mvnw spring-boot:run
# Requires PostgreSQL and Kafka running locally
```

**AI Service:**
```bash
cd smarthire-ai-service
pip install -r requirements.txt
cp .env.example .env   # set GROQ_API_KEY and KAFKA_BOOTSTRAP_SERVERS
uvicorn main:app --reload --port 8000
```

---

## API Reference

### Backend — Spring Boot REST API

Base URL: `http://localhost:8080/api`

#### Authentication (`/api/auth`)

| Method | Endpoint | Request Body | Description |
|--------|----------|-------------|-------------|
| `POST` | `/auth/register` | `{ "username", "email", "password" }` | Register a new recruiter |
| `POST` | `/auth/login` | `{ "username", "password" }` | Login, returns JWT token |

> All other endpoints require the header `Authorization: Bearer <token>` with a valid JWT carrying the `RECRUITER` role.

#### Candidates (`/api/candidates`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/candidates` | Create a new candidate (`name`, `email`, `resumeText`, `phone`) |
| `GET` | `/candidates` | List all candidates (paginated: `?page=0&size=10&sortBy=createdAt&sortDir=desc`) |
| `GET` | `/candidates/{id}` | Get a single candidate by ID |
| `POST` | `/candidates/{id}/screen/{jobId}` | Trigger AI screening — evaluates candidate vs. job |

#### Jobs (`/api/jobs`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/jobs` | Create a new job (`title`, `description`, `location`, `salary`) |
| `GET` | `/jobs` | List all job postings |

### AI Service — FastAPI

Base URL: `http://localhost:8000`

| Method | Endpoint | Request / Params | Description |
|--------|----------|-----------------|-------------|
| `POST` | `/api/ai/screen` | `{ "resume_text", "job_description" }` | Score a resume against a job description |
| `POST` | `/api/ai/candidates/store` | `{ "candidate_id", "resume_text" }` | Store candidate embedding in ChromaDB |
| `GET` | `/api/ai/candidates/similar` | `?jd=<job_description>` | Find top 3 similar candidates by semantic search |
| `GET` | `/health` | — | Service health check |

### Example: Screen a Candidate

```bash
# 1. Register and login
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"recruiter1","email":"r1@example.com","password":"pass123"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"recruiter1","password":"pass123"}' | jq -r '.token')

# 2. Create a job
JOB_ID=$(curl -s -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"title":"Software Engineer","description":"Java, Spring Boot, microservices...","location":"Remote","salary":120000}' | jq -r '.id')

# 3. Create a candidate
CAND_ID=$(curl -s -X POST http://localhost:8080/api/candidates \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Jane Doe","email":"jane@example.com","resumeText":"5 years Java, Spring Boot, Kafka experience...","phone":"555-0100"}' | jq -r '.id')

# 4. Screen
curl -s -X POST "http://localhost:8080/api/candidates/$CAND_ID/screen/$JOB_ID" \
  -H "Authorization: Bearer $TOKEN" | jq
```

---

## Environment Variables

### Root `.env` (used by Docker Compose)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GROQ_API_KEY` | **Yes** | — | Groq API key for Llama3 inference |
| `JWT_SECRET` | **Yes** | — | HS256 signing key (min 256 bits) |

### Backend (`application.yml` — set by Compose)

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/smarthire` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `smarthire` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `smarthire` | DB password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Kafka broker |
| `APP_AI_SERVICE_URL` | `http://ai-service:8000` | AI service base URL for sync scoring |

### AI Service (`.env`)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GROQ_API_KEY` | **Yes** | — | Groq API key |
| `KAFKA_BOOTSTRAP_SERVERS` | **Yes** | `kafka:9092` | Kafka broker address |

---

## Project Structure

```
smarthire/
├── docker-compose.yml                  # 5-service orchestration
├── .env.example                        # Environment template
├── system-health-check.sh             # Full 10-step verification script
├── SmartHire_Postman_Collection.json  # Ready-to-use Postman collection
│
├── smarthire-backend/                  # Spring Boot 3.2 — Java 17
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/smarthire/
│       ├── SmarthireApplication.java
│       ├── config/         # SecurityConfig, KafkaConfig, WebClientConfig
│       ├── controller/     # AuthController, CandidateController, JobController
│       ├── service/        # CandidateService, JobService, AuthService, AIScreeningService
│       ├── repository/     # Spring Data JPA (Candidate, Job, User)
│       ├── model/          # JPA entities (Candidate, Job, User)
│       ├── dto/            # Request/response DTOs
│       ├── security/       # JwtService, JwtAuthenticationFilter, CustomUserDetailsService
│       └── kafka/          # ResumeEventProducer (topic: resume-screening)
│
├── smarthire-ai-service/              # Python FastAPI
    ├── main.py                        # App entry, lifespan (Kafka consumer thread)
    ├── requirements.txt
    ├── Dockerfile
    ├── routers/
    │   └── screening.py               # /api/ai/* endpoints
    ├── services/
    │   ├── rag_service.py             # ChromaDB + HuggingFace embeddings
    │   └── scoring_service.py         # LangChain chain + Groq LLM
    └── kafka/
        └── consumer.py                # Kafka consumer (exponential backoff retry)
```

---

## Testing

```bash
# Backend tests (requires Docker for Testcontainers)
cd smarthire-backend
./mvnw test

# AI service tests
cd smarthire-ai-service
pytest tests/ -v

# Full system health check (all services must be running)
bash system-health-check.sh
```

---

## ⚠️ Known Limitations & Roadmap

SmartHire is under active development. Here are features still being built:

### Not Yet Implemented

| Feature | Status | Description |
|---------|--------|-------------|
| **Resume Parser** | ❌ Not implemented | Currently expects raw `resumeText` in the API. A future version will parse PDF, DOCX, and HTML resumes automatically using libraries like PyMuPDF, python-docx, or a dedicated parsing service. |
| **Frontend UI** | ⚠️ Placeholder | The architecture diagram shows a React UI layer, but no frontend client is included yet. All interactions are via REST API or Swagger UI. PRs welcome! |
| **Rate Limiting** | ❌ Not implemented | No request throttling on the API layer yet. |
| **CI/CD Pipeline** | ❌ Not implemented | No GitHub Actions or similar CI workflows configured yet. |
| **Admin Dashboard** | ❌ Not implemented | No analytics or dashboard for screening metrics. |

### Planned Improvements

- **Resume parsing** — Auto-extract text from PDF/DOCX uploads
- **Bulk screening** — Screen multiple candidates against a single job in one request
- **Screening history** — Track score changes over time for the same candidate
- **Custom scoring rubrics** — Allow recruiters to define weighted evaluation criteria
- **Multi-model support** — Configurable LLM provider (OpenAI, Anthropic, local Ollama)
- **Email notifications** — Send screening results to candidates or recruiters
- **Audit logging** — Track all screening requests and results for compliance
- **Kubernetes manifests** — Helm charts / Kustomize for production deployments

---

## Contributing

Contributions are welcome! Please open an issue first to discuss significant changes.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

<div align="center">
  <sub>Built with ☕ Java · 🐍 Python · 🦜️🔗 LangChain · ⚡ Groq · 📬 Kafka</sub>
</div>
