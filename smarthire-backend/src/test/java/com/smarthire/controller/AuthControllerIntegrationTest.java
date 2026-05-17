package com.smarthire.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.TestContainersConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerIntegrationTest {

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
    com.smarthire.repository.UserRepository userRepository;

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

    @AfterEach
    void cleanup() {
        userRepository.deleteAll();
    }

    @Test
    @Order(1)
    void registerReturns201Created() throws Exception {
        String requestBody = """
            {
                "username": "sarah.chen",
                "email": "sarah@techcorp.io",
                "password": "SecurePass123!"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("sarah.chen"))
                .andExpect(jsonPath("$.role").value("RECRUITER"));
    }

    @Test
    @Order(2)
    void registerDuplicateUsernameReturns400() throws Exception {
        String requestBody1 = """
            {
                "username": "duplicate_user",
                "email": "user1@test.com",
                "password": "Password123!"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody1))
                .andExpect(status().isOk());

        String requestBody2 = """
            {
                "username": "duplicate_user",
                "email": "user2@test.com",
                "password": "Password123!"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("Username already taken"));
    }

    @Test
    @Order(3)
    void loginWithCorrectCredentialsReturns200() throws Exception {
        // Register first
        String registerBody = """
            {
                "username": "login_user",
                "email": "login@test.com",
                "password": "SecurePass123!"
            }
            """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk());

        // Then login
        String loginBody = """
            {
                "username": "login_user",
                "password": "SecurePass123!"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.username").value("login_user"));
    }

    @Test
    @Order(4)
    void loginWithWrongPasswordReturns401() throws Exception {
        // Register first
        String registerBody = """
            {
                "username": "wrong_pass_user",
                "email": "wrong@test.com",
                "password": "CorrectPassword123!"
            }
            """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk());

        // Then login with wrong password
        String wrongLoginBody = """
            {
                "username": "wrong_pass_user",
                "password": "WrongPassword123!"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongLoginBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    void loginWithNonexistentUserReturns401() throws Exception {
        String loginBody = """
            {
                "username": "nonexistent_user",
                "password": "AnyPassword123!"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    void registerWithInvalidEmailReturns400() throws Exception {
        String requestBody = """
            {
                "username": "valid_user",
                "email": "not-an-email",
                "password": "SecurePass123!"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}