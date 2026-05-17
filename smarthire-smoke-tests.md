# SmartHire Smoke Tests

All commands assume services are running: `docker compose up --build`

---

## Step 1 — Register a Recruiter

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "sarah.chen",
    "email": "sarah@techcorp.io",
    "password": "SecurePass123!"
  }'
```

**Expected response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "sarah.chen",
  "role": "RECRUITER"
}
```

---

## Step 2 — Login

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "sarah.chen",
    "password": "SecurePass123!"
  }'
```

**Expected response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "sarah.chen",
  "role": "RECRUITER"
}
```

> Copy the token value — replace `<TOKEN>` in all following requests.

---

## Step 3 — Create a Job Posting

```bash
curl -s -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{
    "title": "Senior Backend Engineer",
    "description": "We are looking for a Senior Backend Engineer to build and scale our microservices platform using Java and Spring Boot. You will own the design and implementation of high-throughput APIs serving millions of requests per day.",
    "requirements": "Java 17+, Spring Boot 3.x, PostgreSQL, Kafka, microservices, REST API design, 5+ years experience"
  }'
```

**Expected response:**
```json
{
  "id": 1,
  "title": "Senior Backend Engineer",
  "description": "We are looking for a Senior Backend Engineer...",
  "requirements": "Java 17+, Spring Boot 3.x, PostgreSQL, Kafka, microservices..."
}
```

> Copy the job `id` — replace `<JOB_ID>` in following requests.

---

## Step 4 — Submit a Candidate

```bash
curl -s -X POST http://localhost:8080/api/candidates \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{
    "name": "Alex Johnson",
    "email": "alex.johnson@devmail.com",
    "resumeText": "Senior Software Engineer with 7 years of experience building distributed systems. Expert in Java 17, Spring Boot 3.2, PostgreSQL, and Apache Kafka. Led a team of 5 engineers to rebuild a payment processing pipeline achieving 99.99% uptime. Strong advocate for clean architecture and test-driven development.",
    "appliedJobId": <JOB_ID>
  }'
```

**Expected response:**
```json
{
  "id": 1,
  "name": "Alex Johnson",
  "email": "alex.johnson@devmail.com",
  "status": "PENDING"
}
```

> Copy the candidate `id` — replace `<CANDIDATE_ID>` in following requests.

---

## Step 5 — Trigger AI Screening

```bash
curl -s -X POST http://localhost:8080/api/candidates/<CANDIDATE_ID>/screen/<JOB_ID> \
  -H "Authorization: Bearer <TOKEN>"
```

**Expected response (synchronous Groq call succeeds immediately):**
```json
{
  "score": 78,
  "strengths": ["7 years Java experience exceeds requirements", "Spring Boot expertise directly applicable", "Kafka experience matches our stack", "Leadership experience with team of 5"],
  "weaknesses": ["No explicit microservices architecture mention", "TDD mentioned but no test coverage metrics"],
  "recommendation": "Hire"
}
```

**Expected response (Groq fails — falls back to async Kafka pipeline):**
```json
{
  "score": 0,
  "strengths": [],
  "weaknesses": ["AI service unavailable"],
  "recommendation": "Pending",
  "status": "async"
}
```

---

## Step 6 — Verify Candidate is Updated

```bash
# If Groq succeeded (sync path):
curl -s http://localhost:8080/api/candidates/<CANDIDATE_ID> \
  -H "Authorization: Bearer <TOKEN>"

# If async path (check after a few seconds):
sleep 5 && curl -s http://localhost:8080/api/candidates/<CANDIDATE_ID> \
  -H "Authorization: Bearer <TOKEN>"
```

**Expected response (after successful screening):**
```json
{
  "id": 1,
  "name": "Alex Johnson",
  "email": "alex.johnson@devmail.com",
  "status": "SCREENED",
  "aiScore": 78,
  "aiRecommendation": "Hire",
  "strengths": "7 years Java experience exceeds requirements, Spring Boot expertise directly applicable, Kafka experience matches our stack, Leadership experience with team of 5",
  "weaknesses": "No explicit microservices architecture mention, TDD mentioned but no test coverage metrics",
  "appliedJobId": 1
}
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| 401 Unauthorized on all requests | Token not included or expired | Re-login and copy fresh token |
| 403 Forbidden | Wrong role or CSRF issue | Confirm role is RECRUITER |
| `screen` returns `status: async` | Groq call failed, Kafka processing queued | Check AI service logs: `docker compose logs ai-service` |
| Kafka connection refused | Kafka not healthy yet | `docker compose ps`, wait for kafka healthy |
| `aiScore` still null after 10s | Kafka consumer not started or crashed | Check ai-service logs |
| POST /api/candidates returns 500 | DB connection failed | `docker compose logs postgres` |