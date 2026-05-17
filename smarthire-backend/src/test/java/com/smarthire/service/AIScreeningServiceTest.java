package com.smarthire.service;

import com.smarthire.kafka.ResumeEventProducer;
import com.smarthire.model.Candidate;
import com.smarthire.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIScreeningServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private ResumeEventProducer resumeEventProducer;

    @Mock
    private CandidateService candidateService;

    @Mock
    private JobService jobService;

    @InjectMocks
    private AIScreeningService aiScreeningService;

    private Candidate candidate;
    private Job job;

    @BeforeEach
    void setUp() {
        candidate = Candidate.builder()
                .id(1L)
                .name("Alex Johnson")
                .email("alex@devmail.com")
                .resumeText("5 years Java experience")
                .status(Candidate.CandidateStatus.PENDING)
                .build();

        job = Job.builder()
                .id(1L)
                .title("Senior Java Developer")
                .description("Build scalable backend services")
                .requirements("Java 17, Spring Boot 3.x, PostgreSQL")
                .build();
    }

    @Test
    void screenCandidateSuccess() {
        Map<String, Object> aiResponse = Map.of(
                "score", 85,
                "strengths", List.of("Strong Java background", "Spring Boot expert"),
                "weaknesses", List.of("Limited Kafka experience"),
                "recommendation", "Hire"
        );

        when(candidateService.findById(1L)).thenReturn(candidate);
        when(jobService.findById(1L)).thenReturn(job);
        when(candidateService.update(any(Candidate.class))).thenReturn(candidate);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(aiResponse));

        Map<String, Object> result = aiScreeningService.screen(1L, 1L);

        assertThat(result).isNotNull();
        assertThat(result.get("score")).isEqualTo(85);
        assertThat(result.get("recommendation")).isEqualTo("Hire");
        verify(resumeEventProducer, times(1)).sendResumeEvent(any());
    }

    @Test
    void screenCandidateTimeout() {
        when(candidateService.findById(1L)).thenReturn(candidate);
        when(jobService.findById(1L)).thenReturn(job);
        when(candidateService.update(any(Candidate.class))).thenReturn(candidate);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.error(new RuntimeException("Connection timeout")));

        Map<String, Object> result = aiScreeningService.screen(1L, 1L);

        assertThat(result).isNotNull();
        assertThat(result).containsKey("status");
        assertThat(result.get("status")).isEqualTo("async");
        verify(resumeEventProducer, times(1)).sendResumeEvent(any());
    }

    @Test
    void screenCandidateInvalidResponse() {
        when(candidateService.findById(1L)).thenReturn(candidate);
        when(jobService.findById(1L)).thenReturn(job);
        when(candidateService.update(any(Candidate.class))).thenReturn(candidate);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("invalid", "response")));

        Map<String, Object> result = aiScreeningService.screen(1L, 1L);

        assertThat(result).isNotNull();
        assertThat(result).containsKey("invalid");
    }
}