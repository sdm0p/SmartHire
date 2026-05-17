# SmartHire — AI-Powered Recruitment Automation Platform

<p align="center">
  <img src="https://img.shields.io/badge/Java-17+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java">
  <img src="https://img.shields.io/badge/Python-3.11+-3776AB?style=for-the-badge&logo=python&logoColor=white" alt="Python">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/FastAPI-009688?style=for-the-badge&logo=fastapi&logoColor=white" alt="FastAPI">
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker">
  <img src="https://img.shields.io/badge/Apache%20Kafka-231F20?style=for-the-badge&logo=apachekafka&logoColor=white" alt="Kafka">
</p>

<p align="center">
  <a href="https://github.com/smarthire/smarthire/stargazers"><img src="https://img.shields.io/github/stars/smarthire/smarthire?style=social" alt="Stars"></a>
  <a href="https://github.com/smarthire/smarthire/network/members"><img src="https://img.shields.io/github/forks/smarthire/smarthire?style=social" alt="Forks"></a>
  <a href="https://github.com/smarthire/smarthire/issues"><img src="https://img.shields.io/github/issues/smarthire/smarthire" alt="Issues"></a>
  <a href="https://github.com/smarthire/smarthire/blob/main/LICENSE"><img src="https://img.shields.io/github/license/smarthire/smarthire" alt="License"></a>
</p>

---

SmartHire automates resume screening at scale using a Retrieval-Augmented Generation (RAG) pipeline powered by large language models. A Spring Boot REST API ingests candidates and jobs, publishes events to Apache Kafka, and a Python FastAPI microservice handles AI-powered scoring — enabling recruiters to focus on hiring decisions instead of manual resume review.

## Architecture

```
┌──────────┐     ┌──────────────────┐     ┌──────────────────┐
│  React   │────▶│   Spring Boot    │────▶│    PostgreSQL    │
│    UI    │     │   REST API :8080 │     │                  │
└──────────┘     └────────┬─────────┘     └──────────────────┘
                          │
                          │ 1. POST /screen
                          ▼
              ┌───────────────────────────┐
              │     Apache Kafka          │
              │   (resume-screening)     │
              └───────────┬───────────────┘
                          │ 2. Consume event
                          ▼
              ┌───────────────────────────┐     ┌──────────────────┐
              │   FastAPI AI Service     │────▶│    ChromaDB      │
              │       :8000              │     │  (Vector Store)  │
              │  LangChain + Groq LLM   │     └──────────────────┘
              └───────────────────────────┘
```

## Tech Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Backend API** | Java 17 / Spring Boot 3.2 | REST API, business logic, Kafka producer |
| **AI Microservice** | Python 3.11 / FastAPI | LLM screening, RAG retrieval |
| **AI Framework** | LangChain | Chain composition, prompt management |
| **LLM Provider** | Groq (Llama3 8B) | Free-tier inference for resume scoring |
| **Vector Store** | ChromaDB | Persistent embeddings for RAG matching |
| **Message Broker** | Apache Kafka | Async event-driven screening pipeline |
| **Database** | PostgreSQL 15 | Relational data: candidates, jobs, users |
| **Authentication** | JWT (jjwt) | Stateless auth with RECRUITER role |
| **Containerization** | Docker Compose | Full-stack local & production deploy |
| **Documentation** | SpringDoc OpenAPI | Interactive Swagger UI |

## Features

- **AI Resume Scoring** — LLM-powered evaluation against job descriptions, returning a 0–100 score with strengths, weaknesses, and a hiring recommendation
- **RAG-Based Matching** — Semantic similarity search across past candidate embeddings to inform screening decisions
- **Async Kafka Pipeline** — Non-blocking resume processing; events flow from Spring Boot to the AI service via Kafka
- **JWT Authentication** — Role-based access control (RECRUITER) protecting all `/api/**` endpoints
- **Full REST APIs** — Candidate management, job posting, and screening orchestration
- **Swagger Documentation** — Interactive API docs at `/swagger-ui.html`
- **Docker Compose** — One-command local environment with healthchecks and dependency ordering

## Getting Started

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) 20.10+
- [Docker Compose](https://docs.docker.com/compose/install/) v2.0+
- Groq API key — [get one free at console.groq.com](https://console.groq.com)

### Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/smarthire/smarthire.git
cd smarthire

# 2. Copy environment variables and fill in your keys
cp .env.example .env
# Edit .env and set GROQ_API_KEY and JWT_SECRET

# 3. Start all services
docker compose up --build

# 4. Access the APIs
# Backend (Spring Boot):  http://localhost:8080
# API Docs (Swagger):      http://localhost:8080/swagger-ui.html
# AI Service (FastAPI):   http://localhost:8000
# AI Docs:                http://localhost:8000/docs
```

Services start in dependency order: PostgreSQL → Zookeeper → Kafka → AI Service → Backend. All healthchecks are included so dependent services wait for their dependencies to be ready.

### Manual Development

**Backend:**
```bash
cd smarthire-backend
./mvnw spring-boot:run
```

**AI Service:**
```bash
cd smarthire-ai-service
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

---

## API Reference

### Backend — Spring Boot REST API

Base URL: `http://localhost:8080/api`

#### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/auth/register` | Register a new recruiter account |
| `POST` | `/auth/login` | Login and receive a JWT token |

> All other endpoints require `Authorization: Bearer <token>` header with a valid JWT.

#### Candidates

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/candidates` | Create a new candidate |
| `GET` | `/candidates` | List all candidates (paginated) |
| `GET` | `/candidates/{id}` | Get a candidate by ID |
| `POST` | `/candidates/{id}/screen/{jobId}` | Trigger AI screening |

#### Jobs

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/jobs` | Create a new job posting |
| `GET` | `/jobs` | List all job postings |

### AI Service — FastAPI

Base URL: `http://localhost:8000`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/ai/screen` | Score a resume against a job description |
| `POST` | `/api/ai/candidates/store` | Store a candidate's embedding in ChromaDB |
| `GET` | `/api/ai/candidates/similar` | Find top 3 similar past candidates |
| `GET` | `/health` | Service health check |

---

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `GROQ_API_KEY` | API key for Groq LLM (Llama3 8B) | Yes |
| `JWT_SECRET` | Secret key for signing JWT tokens (min 256 bits) | Yes |
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL | Set by compose |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username | Set by compose |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password | Set by compose |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address | Set by compose |
| `APP_AI_SERVICE_URL` | AI service base URL for WebClient | Set by compose |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address for AI service | Set by compose |

---

## Project Structure

```
smarthire/
├── docker-compose.yml          # Full-stack orchestration
├── .env.example               # Environment variable template
├── README.md
│
├── smarthire-backend/         # Spring Boot 3.2 — Java 17
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/smarthire/
│       ├── SmarthireApplication.java
│       ├── config/            # Security, Kafka, WebClient, OpenAPI
│       ├── controller/        # Candidate, Job, Auth REST controllers
│       ├── service/           # Business logic + AI screening orchestration
│       ├── repository/        # Spring Data JPA repositories
│       ├── model/             # JPA entities (Candidate, Job, User)
│       ├── dto/               # Request/response DTOs
│       ├── security/          # JWT filter, service, UserDetailsService
│       └── kafka/             # Kafka producer (resume-screening topic)
│
└── smarthire-ai-service/     # Python FastAPI
    ├── main.py                # App entry, lifespan (Kafka consumer)
    ├── requirements.txt
    ├── Dockerfile
    ├── routers/               # /api/ai/* endpoints
    ├── services/
    │   ├── rag_service.py     # ChromaDB + embedding management
    │   └── scoring_service.py # LangChain chain, Groq LLM calls
    └── kafka/
        └── consumer.py        # Kafka consumer on resume-screening topic
```

---

## Screening Flow

```
1. POST /api/candidates/{id}/screen/{jobId}
       │
2. Backend calls AI service (WebClient) ──────▶ Groq Llama3 ──▶ Screen & score
       │                                              │
3. Backend publishes to Kafka ──▶ topic: resume-screening
       │
4. AI service consumes event
       │
5. Stores embedding in ChromaDB (RAG corpus)
       │
6. Returns: { score, strengths[], weaknesses[], recommendation }
       │
7. Backend updates candidate record → status=SCREENED, saves scores
```

---

## Contributing

Contributions are welcome. Please open an issue first to discuss significant changes.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Built with Java · Python · LangChain · Groq · Kafka
</p>
