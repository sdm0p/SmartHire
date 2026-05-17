package com.smarthire.service;

import com.smarthire.dto.CandidateDto;
import com.smarthire.model.Candidate;
import com.smarthire.repository.CandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock
    private CandidateRepository candidateRepository;

    @InjectMocks
    private CandidateService candidateService;

    private CandidateDto sampleDto;
    private Candidate savedCandidate;

    @BeforeEach
    void setUp() {
        sampleDto = CandidateDto.builder()
                .name("Alex Johnson")
                .email("alex@devmail.com")
                .resumeText("5 years Java experience, Spring Boot expert")
                .build();

        savedCandidate = Candidate.builder()
                .id(1L)
                .name("Alex Johnson")
                .email("alex@devmail.com")
                .resumeText("5 years Java experience, Spring Boot expert")
                .status(Candidate.CandidateStatus.PENDING)
                .build();
    }

    @Test
    void saveCandidateSuccess() {
        when(candidateRepository.save(any(Candidate.class))).thenReturn(savedCandidate);

        Candidate result = candidateService.create(sampleDto);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Alex Johnson");
        assertThat(result.getEmail()).isEqualTo("alex@devmail.com");
        assertThat(result.getStatus()).isEqualTo(Candidate.CandidateStatus.PENDING);
        verify(candidateRepository, times(1)).save(any(Candidate.class));
    }

    @Test
    void getCandidateByIdNotFound() {
        when(candidateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.findById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Candidate not found with id: 99");
    }

    @Test
    void getCandidateByIdSuccess() {
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(savedCandidate));

        Candidate result = candidateService.findById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Alex Johnson");
    }

    @Test
    void listCandidatesReturnsPaginatedResults() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Candidate> page = new PageImpl<>(List.of(savedCandidate));
        when(candidateRepository.findAll(pageable)).thenReturn(page);

        Page<Candidate> result = candidateService.findAll(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Alex Johnson");
    }

    @Test
    void updateCandidateSuccess() {
        savedCandidate.setAiScore(85);
        savedCandidate.setStatus(Candidate.CandidateStatus.SCREENED);
        when(candidateRepository.save(any(Candidate.class))).thenReturn(savedCandidate);

        Candidate result = candidateService.update(savedCandidate);

        assertThat(result.getAiScore()).isEqualTo(85);
        assertThat(result.getStatus()).isEqualTo(Candidate.CandidateStatus.SCREENED);
    }
}