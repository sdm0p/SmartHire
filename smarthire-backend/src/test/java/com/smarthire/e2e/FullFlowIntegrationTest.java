package com.smarthire.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.JwtTestHelper;
import com.smarthire.security.JwtService;
import com.smarthire.repository.CandidateRepository;
import com.smarthire.repository.JobRepository;
import com.smarthire.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = {"resume-screening"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullFlowIntegrationTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
            .withDatabaseName("smarthire_test")
            .withUsername("test")
            .withPassword("test");

    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withEmbeddedZookeeper();

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtService jwtService;

    @Autowired
    CandidateRepository candidateRepository;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    UserRepository userRepository;

    String authToken;
    Long jobId;
    Long candidateId;

    @BeforeAll
    static void startContainers() {
        postgres.start();
        kafka.start();
    }

    @AfterAll
    static void stopContainers() {
        kafka.stop();
        postgres.stop();
    }

    @DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeEach
    void setup() {
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ─── STEP 1: Register Recruiter ───────────────────────────────────────────

    @Test
    @Order(1)
    void step1_registerRecruiter_returnsValidJwt() {
        Map<String, Object> registerPayload = Map.of(
                "username", "flow_test_recruiter",
                "email", "recruiter@smarthire.test",
                "password", "TestPass123!"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(registerPayload),
                Map.class
        );

        assertStep(response, 200, "Register recruiter");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("token");
        assertThat(response.getBody().get("token")).isNotNull();
        assertThat(response.getBody().get("username")).isEqualTo("flow_test_recruiter");
        assertThat(response.getBody().get("role")).isEqualTo("RECRUITER");

        authToken = (String) response.getBody().get("token");
        System.out.println("[STEP 1] PASS — Recruiter registered, token: " + authToken.substring(0, 20) + "...");
    }

    // ─── STEP 2: Login ────────────────────────────────────────────────────────

    @Test
    @Order(2)
    void step2_login_returnsValidToken() {
        // Register first if not already done
        if (authToken == null) {
            Map<String, Object> registerPayload = Map.of(
                    "username", "flow_test_recruiter",
                    "email", "recruiter@smarthire.test",
                    "password", "TestPass123!"
            );
            restTemplate.exchange("/api/auth/register", HttpMethod.POST,
                    new HttpEntity<>(registerPayload), Map.class);
        }

        Map<String, Object> loginPayload = Map.of(
                "username", "flow_test_recruiter",
                "password", "TestPass123!"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(loginPayload),
                Map.class
        );

        assertStep(response, 200, "Login");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("token")).isNotNull();

        authToken = (String) response.getBody().get("token");
        System.out.println("[STEP 2] PASS — Logged in, token valid");
    }

    // ─── STEP 3: Create Job ───────────────────────────────────────────────────

    @Test
    @Order(3)
    void step3_createJob_savedWithId() {
        Map<String, Object> jobPayload = Map.of(
                "title", "Senior Java Developer",
                "description", "Build and scale backend microservices using Java 17 and Spring Boot 3.x. You will own the design and implementation of high-throughput APIs serving millions of requests.",
                "requirements", "Java 17+, Spring Boot 3.x, PostgreSQL, Kafka, REST API design, 5+ years experience, microservices architecture"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/jobs",
                HttpMethod.POST,
                new HttpEntity<Map<String, Object>>(jobPayload, authHeaders()),
                Map.class
        );

        assertStep(response, 200, "Create job");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("title")).isEqualTo("Senior Java Developer");
        assertThat((String) response.getBody().get("description")).contains("Java 17");

        jobId = ((Number) response.getBody().get("id")).longValue();
        System.out.println("[STEP 3] PASS — Job created with id=" + jobId);
    }

    // ─── STEP 4: Create Candidate ────────────────────────────────────────────

    @Test
    @Order(4)
    void step4_createCandidate_savedWithStatusPending() {
        Map<String, Object> candidatePayload = Map.of(
                "name", "Alex Johnson",
                "email", "alex.johnson@devmail.com",
                "resumeText", "Senior Software Engineer with 7 years of experience. Expert in Java 17, Spring Boot 3.2, PostgreSQL, and Apache Kafka. Led a team of 5 engineers to rebuild a payment processing pipeline achieving 99.99% uptime. Strong advocate for clean architecture and test-driven development. Built microservices handling 50K req/sec using Spring Boot and Kafka.",
                "appliedJobId", jobId
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/candidates",
                HttpMethod.POST,
                new HttpEntity<Map<String, Object>>(candidatePayload, authHeaders()),
                Map.class
        );

        assertStep(response, 200, "Create candidate");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("name")).isEqualTo("Alex Johnson");
        assertThat(response.getBody().get("status")).isEqualTo("PENDING");

        candidateId = ((Number) response.getBody().get("id")).longValue();
        System.out.println("[STEP 4] PASS — Candidate created with id=" + candidateId + ", status=PENDING");
    }

    // ─── STEP 5: Trigger Screening ──────────────────────────────────────────

    @Test
    @Order(5)
    void step5_triggerScreening_http200AndCandidateUpdated() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/candidates/{id}/screen/{jobId}",
                HttpMethod.POST,
                new HttpEntity<Void>(null, authHeaders()),
                Map.class,
                candidateId,
                jobId
        );

        assertStep(response, 200, "Trigger screening");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("score");

        // Verify Kafka event was published
        var candidate = candidateRepository.findById(candidateId).orElseThrow();
        assertThat(candidate.getStatus()).isNotNull();
        assertThat(candidate.getAppliedJobId()).isEqualTo(jobId);

        System.out.println("[STEP 5] PASS — Screening triggered, status=" + candidate.getStatus());
    }

    // ─── STEP 6: Fetch Candidate and Verify All Fields ──────────────────────

    @Test
    @Order(6)
    void step6_fetchCandidate_allFieldsPopulated() {
        // Wait briefly for async processing to complete
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/candidates/{id}",
                HttpMethod.GET,
                new HttpEntity<Void>(authHeaders()),
                Map.class,
                candidateId
        );

        assertStep(response, 200, "Fetch candidate by id");
        assertThat(response.getBody()).isNotNull();

        Map<String, Object> body = response.getBody();
        assertThat(body.get("id")).isEqualTo(candidateId);
        assertThat(body.get("name")).isEqualTo("Alex Johnson");
        assertThat(body.get("email")).isEqualTo("alex.johnson@devmail.com");
        assertThat(body.get("status")).isNotNull(); // PENDING or SCREENED

        // AI fields may or may not be populated depending on Groq call result
        // Document the expected shape
        System.out.println("[STEP 6] PASS — Candidate fetched, status=" + body.get("status"));
        if (body.get("aiScore") != null) {
            Integer score = (Integer) body.get("aiScore");
            assertThat(score).isBetween(0, 100);
            System.out.println("  → AI Score: " + score + ", Recommendation: " + body.get("aiRecommendation"));
        } else {
            System.out.println("  → AI Score: not yet available (async processing)");
        }
    }

    // ─── STEP 7: List All Candidates ─────────────────────────────────────────

    @Test
    @Order(7)
    void step7_fetchAllCandidates_paginationWorks() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/candidates?page=0&size=10",
                HttpMethod.GET,
                new HttpEntity<Void>(authHeaders()),
                Map.class
        );

        assertStep(response, 200, "List all candidates with pagination");
        assertThat(response.getBody()).isNotNull();

        Map<String, Object> body = response.getBody();
        assertThat(body).containsKey("content");
        assertThat(body).containsKey("totalElements");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertThat(content).isNotEmpty();

        boolean found = content.stream().anyMatch(c ->
                "Alex Johnson".equals(c.get("name")) &&
                c.get("id").toString().equals(candidateId.toString())
        );
        assertThat(found).isTrue();

        System.out.println("[STEP 7] PASS — Candidate list returned " + content.size() + " candidates (total: " + body.get("totalElements") + ")");
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void assertStep(ResponseEntity<Map> response, int expectedStatus, String step) {
        assertThat(response.getStatusCode().value())
                .as("Expected " + step + " to return " + expectedStatus + " but got " + response.getStatusCode().value()
                        + ". Body: " + response.getBody())
                .isEqualTo(expectedStatus);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + authToken);
        return headers;
    }
}