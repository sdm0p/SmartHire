package com.smarthire.service;

import com.smarthire.dto.CandidateDto;
import com.smarthire.model.Candidate;
import com.smarthire.repository.CandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateRepository candidateRepository;

    public Candidate create(CandidateDto dto) {
        Candidate candidate = Candidate.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .resumeText(dto.getResumeText())
                .status(Candidate.CandidateStatus.PENDING)
                .appliedJobId(dto.getAppliedJobId())
                .build();
        return candidateRepository.save(candidate);
    }

    public Page<Candidate> findAll(Pageable pageable) {
        return candidateRepository.findAll(pageable);
    }

    public Candidate findById(Long id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Candidate not found with id: " + id));
    }

    public Candidate update(Candidate candidate) {
        return candidateRepository.save(candidate);
    }

    public CandidateDto toDto(Candidate candidate) {
        return CandidateDto.builder()
                .id(candidate.getId())
                .name(candidate.getName())
                .email(candidate.getEmail())
                .resumeText(candidate.getResumeText())
                .status(candidate.getStatus())
                .aiScore(candidate.getAiScore())
                .aiRecommendation(candidate.getAiRecommendation())
                .strengths(candidate.getStrengths())
                .weaknesses(candidate.getWeaknesses())
                .appliedJobId(candidate.getAppliedJobId())
                .build();
    }
}
