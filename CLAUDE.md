# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# SmartHire — AI-Powered Recruitment Platform

AI-driven resume screening platform using a RAG pipeline. A Spring Boot REST API ingests candidates and jobs, publishes events to Apache Kafka, and a Python FastAPI microservice handles AI-powered scoring with LangChain + Groq (Llama3).

## Tech Stack

| Component | Technology | Port |
|-----------|-------------|------|
| Backend API | Spring Boot 3.2, Java 17, Maven | 8080 |
| AI Service | Python 3.11, FastAPI, LangChain, ChromaDB | 8000 |
| Database | PostgreSQL 15 | 5432 |
| Message Broker | Apache Kafka (confluentinc/cp-kafka:7.4.0) | 9092 |
| LLM Provider | Groq API (llama3-8b-8192, free tier) | — |
| Vector Store | ChromaDB (persistent) | — |

## Common Commands

### Docker (full stack)
```bash
docker-compose up -d              # Start all services in dependency order
docker-compose up -d --build      # Rebuild images then start
docker-compose down -v            # Stop and remove volumes
docker ps                         # Check running containers
```

### Backend (Java/Spring Boot)
```bash
cd smarthire-backend
mvn clean package -DskipTests     # Build JAR without running tests
mvn test                         # Run all tests (requires Docker for testcontainers)
mvn test -Dtest=AuthServiceTest  # Run single test class
mvn spring-boot:run              # Run locally (needs postgres/kafka running)
./mvnw spring-boot:run           # Alternative using Maven wrapper
```

### AI Service (Python)
```bash
cd smarthire-ai-service
pip install -r requirements.txt
uvicorn main:app --reload --port 8000   # Run locally (needs Kafka running)
pytest tests/ -v                        # Run tests
pytest tests/ -v --cov=.                 # Run with coverage
```

### System Health Check
```bash
bash system-health-check.sh      # Full 10-step verification (all services must be running)
```

## Architecture

```
Client → REST API → Kafka (resume-screening topic) → AI Service → ChromaDB
         ↕ (sync via WebClient)
      Groq Llama3
```

**Screening flow:**
1. `POST /api/candidates/{id}/screen/{jobId}` triggers evaluation
2. Backend simultaneously calls Groq LLM via WebClient (sync path)
3. Backend publishes to Kafka `resume-screening` topic (async path)
4. AI service (FastAPI) consumes Kafka events, stores embedding in ChromaDB, returns scores
5. Frontend polls `/api/candidates/{id}` or waits for async update

## Project Structure

```
smarthire/
├── docker-compose.yml         # 5 services: postgres, zookeeper, kafka, ai-service, backend
├── .env                      # Root env (GROQ_API_KEY, JWT_SECRET)
├── system-health-check.sh    # 10-step live verification script
│
├── smarthire-backend/        # Spring Boot
│   ├── pom.xml               # Dependencies: spring-boot-starter-*, kafka, jjwt, testcontainers, jaCoCo
│   └── src/
│       ├── main/java/com/smarthire/
│       │   ├── SmarthireApplication.java
│       │   ├── config/       # SecurityConfig (JWT, CORS, permitAll paths), KafkaConfig, WebClientConfig
│       │   ├── controller/  # AuthController, CandidateController, JobController
│       │   ├── service/     # CandidateService, JobService, AuthService, AIScreeningService
│       │   ├── repository/  # Spring Data JPA (CandidateRepository, JobRepository, UserRepository)
│       │   ├── model/        # JPA entities: Candidate (status=PENDING/SCREENED, aiScore, recommendation), Job, User
│       │   ├── dto/          # LoginRequest, RegisterRequest, AuthResponse (uses 'username' field, not 'email')
│       │   ├── security/     # JwtService, JwtAuthenticationFilter, CustomUserDetailsService
│       │   └── kafka/        # ResumeEventProducer (publishes to 'resume-screening')
│       └── test/            # Unit + integration tests (testcontainers for postgres/kafka)
│
└── smarthire-ai-service/     # Python FastAPI
    ├── main.py               # Lifespan starts Kafka consumer thread + RAGService init
    ├── requirements.txt      # langchain-groq, langchain-chroma, langchain-huggingface, sentence-transformers
    ├── .env                  # GROQ_API_KEY, KAFKA_BOOTSTRAP_SERVERS=kafka:9092 (not localhost!)
    ├── routers/
    │   └── screening.py      # POST /api/ai/screen, /api/ai/candidates/store, GET /api/ai/candidates/similar
    ├── services/
    │   ├── rag_service.py   # ChromaDB + HuggingFaceEmbeddings + ChatGroq
    │   └── scoring_service.py  # LangChain chain, JSON parsing with fallback
    └── kafka/
        └── consumer.py      # Confluent Kafka consumer, exponential backoff retry (5s→60s)
```

## Key Implementation Notes

### Authentication
- Login uses `username` field (not `email`). Request body: `{"username":"...", "password":"..."}`
- Register uses both `username` and `email` fields
- JWT token must be passed as `Authorization: Bearer <token>` for all `/api/**` endpoints
- `/actuator/health` and `/swagger-ui/**` are publicly accessible (permitAll)
- All other `/api/**` endpoints require `RECRUITER` role

### API Contracts
- Backend → Python: `{"resume_text": "...", "job_description": "..."}` (snake_case)
- Python returns: `{"score": int(0-100), "strengths": [], "weaknesses": [], "recommendation": "Strong Hire|Hire|Maybe|No Hire"}`
- ChromaDB similarity_search returns score in function return value, NOT in doc.metadata

### Kafka
- Topic: `resume-screening`
- AI service `.env` must use `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` (inside Docker network)
- Backend uses `kafka:9092` internally; external port is `9092`
- Backend depends on Kafka being healthy; AI service depends on Kafka being started

### Docker Healthchecks
- postgres: `pg_isready -U smarthire -d smarthire`
- kafka: `kafka-topics --bootstrap-server localhost:9092 --list`
- ai-service: `curl -f http://localhost:8000/health`
- backend: Spring Boot actuator `/actuator/health`

### Known Issues & Fixes (already applied)
- `spring-boot-starter-actuator` must be in pom.xml
- `/actuator/**` must be in SecurityConfig permitAll
- Python Dockerfile must have `curl` installed (python:slim doesn't include it)
- `sentence-transformers` must be in requirements.txt (not bundled with langchain-huggingface)
- `application.yml` must not have duplicate `management:` blocks
- Test files use `org.testcontainers.*` imports (not `org.springframework.testcontainers.*`)
- `DynamicPropertySource` parameter type must be `DynamicPropertyRegistry` (not `DynamicPropertySource`)
- WebClient mocking in tests: `bodyValue()` returns `RequestHeadersSpec`, not `RequestBodySpec`
- `HttpEntity<>` with no type parameter causes inference issues — use explicit `HttpEntity<Void>` or `HttpEntity<Map<String,Object>>`