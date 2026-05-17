package com.smarthire.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.TestContainersConfig;
import com.smarthire.JwtTestHelper;
import com.smarthire.security.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {"resume-screening"})
@Import(com.smarthire.KafkaTestContainerConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CandidateControllerIntegrationTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
            .withDatabaseName("smarthire_test")
            .withUsername("test")
            .withPassword("test");

    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withEmbeddedZookeeper();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtService jwtService;

    String authToken;
    Long createdCandidateId;

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
    void setUp() {
        authToken = JwtTestHelper.generateTestToken(jwtService, "integration_test_user");
    }

    @Test
    @Order(1)
    void postCandidateReturns201Created() throws Exception {
        String requestBody = """
            {
                "name": "Alex Johnson",
                "email": "alex.johnson@devmail.com",
                "resumeText": "5 years Java experience, Spring Boot expert, PostgreSQL, Kafka"
            }
            """;

        mockMvc.perform(post("/api/candidates")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Alex Johnson"))
                .andExpect(jsonPath("$.email").value("alex.johnson@devmail.com"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andDo(result -> {
                    String id = result.getResponse().getContentAsString()
                            .split("\"id\":")[1].split(",")[0].trim();
                    createdCandidateId = Long.parseLong(id);
                });
    }

    @Test
    @Order(2)
    void getCandidatesReturnsPaginatedList() throws Exception {
        mockMvc.perform(get("/api/candidates")
                        .header("Authorization", "Bearer " + authToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").exists())
                .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    @Order(3)
    void getCandidateByIdReturnsCandidate() throws Exception {
        mockMvc.perform(get("/api/candidates/" + createdCandidateId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdCandidateId))
                .andExpect(jsonPath("$.name").value("Alex Johnson"));
    }

    @Test
    @Order(4)
    void getCandidateByIdNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/candidates/999999")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    void screenCandidateWithoutJwtReturns401() throws Exception {
        mockMvc.perform(post("/api/candidates/1/screen/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    void screenCandidateWithValidJwtReturns200() throws Exception {
        mockMvc.perform(post("/api/candidates/{id}/screen/{jobId}", createdCandidateId, 1L)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    void postCandidateWithDuplicateEmailReturns409() throws Exception {
        String requestBody = """
            {
                "name": "Alex Johnson",
                "email": "alex.johnson@devmail.com",
                "resumeText": "5 years Java experience"
            }
            """;

        mockMvc.perform(post("/api/candidates")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }
}