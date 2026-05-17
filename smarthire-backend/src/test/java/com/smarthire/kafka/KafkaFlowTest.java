package com.smarthire.kafka;

import com.smarthire.TestContainersConfig;
import com.smarthire.JwtTestHelper;
import com.smarthire.repository.CandidateRepository;
import com.smarthire.repository.JobRepository;
import com.smarthire.security.JwtService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = {"resume-screening"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KafkaFlowTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
            .withDatabaseName("smarthire_test")
            .withUsername("test")
            .withPassword("test");

    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withEmbeddedZookeeper();

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JwtService jwtService;

    @Autowired
    CandidateRepository candidateRepository;

    @Autowired
    JobRepository jobRepository;

    String authToken;
    Long candidateId;
    Long jobId;

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
        authToken = JwtTestHelper.generateTestToken(jwtService, "kafka_test_user");

        // Create a job
        Map<String, Object> jobPayload = Map.of(
                "title", "Senior Backend Engineer",
                "description", "Build scalable APIs using Java and Kafka",
                "requirements", "Java 17, Spring Boot, Kafka, PostgreSQL"
        );
        ResponseEntity<Map> jobResponse = restTemplate.exchange(
                "/api/jobs",
                HttpMethod.POST,
                new HttpEntity<>(jobPayload, authHeaders()),
                Map.class
        );
        jobId = ((Number) jobResponse.getBody().get("id")).longValue();

        // Create a candidate
        Map<String, Object> candidatePayload = Map.of(
                "name", "Alex Johnson",
                "email", "alex.kafka@test.com",
                "resumeText", "Senior Java engineer with 6 years experience in Spring Boot and Kafka"
        );
        ResponseEntity<Map> candidateResponse = restTemplate.exchange(
                "/api/candidates",
                HttpMethod.POST,
                new HttpEntity<>(candidatePayload, authHeaders()),
                Map.class
        );
        candidateId = ((Number) candidateResponse.getBody().get("id")).longValue();
    }

    @Test
    @Order(1)
    void kafkaEventPublishedWhenScreenEndpointCalled() throws Exception {
        // Trigger screening
        ResponseEntity<Map> screenResponse = restTemplate.exchange(
                "/api/candidates/{id}/screen/{jobId}",
                HttpMethod.POST,
                new HttpEntity<Void>(null, authHeaders()),
                Map.class,
                candidateId,
                jobId
        );

        assertThat(screenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Consume from Kafka to verify event was published
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                        ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group",
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
                )
        );
        consumer.subscribe(Collections.singletonList("resume-screening"));

        boolean eventFound = false;
        ConsumerRecord<String, String> foundRecord = null;

        for (int i = 0; i < 10; i++) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value().contains("candidate_id")) {
                    eventFound = true;
                    foundRecord = record;
                    break;
                }
            }
            if (eventFound) break;
        }

        consumer.close();

        assertThat(eventFound).isTrue();
        assertThat(foundRecord).isNotNull();

        String payload = foundRecord.value();
        assertThat(payload).contains("candidate_id");
        assertThat(payload).contains("resume_text");
        assertThat(payload).contains("job_description");

        // Parse and verify key fields
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> event = mapper.readValue(payload, Map.class);
        assertThat(event.get("candidate_id")).isEqualTo(candidateId.intValue());
        assertThat(event.get("resume_text")).isNotNull();
        assertThat(event.get("resume_text").toString()).contains("Java");
    }

    @Test
    @Order(2)
    void candidateStatusUpdatedAfterScreening() {
        // Trigger screening
        ResponseEntity<Map> screenResponse = restTemplate.exchange(
                "/api/candidates/{id}/screen/{jobId}",
                HttpMethod.POST,
                new HttpEntity<Void>(null, authHeaders()),
                Map.class,
                candidateId,
                jobId
        );

        assertThat(screenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify candidate status in DB
        var candidate = candidateRepository.findById(candidateId).orElseThrow();
        assertThat(candidate.getStatus()).isNotNull();
        assertThat(candidate.getAppliedJobId()).isEqualTo(jobId);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + authToken);
        return new HttpEntity<>(null, headers).getHeaders();
    }
}