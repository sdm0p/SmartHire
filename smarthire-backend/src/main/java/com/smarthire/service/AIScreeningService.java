package com.smarthire.service;

import com.smarthire.dto.ScreeningResponse;
import com.smarthire.kafka.ResumeEventProducer;
import com.smarthire.model.Candidate;
import com.smarthire.model.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIScreeningService {

    private final WebClient webClient;
    private final ResumeEventProducer resumeEventProducer;
    private final CandidateService candidateService;
    private final JobService jobService;

    public Map<String, Object> screen(Long candidateId, Long jobId) {
        Candidate candidate = candidateService.findById(candidateId);
        Job job = jobService.findById(jobId);

        candidate.setAppliedJobId(jobId);
        candidate.setStatus(Candidate.CandidateStatus.PENDING);
        candidateService.update(candidate);

        Map<String, Object> kafkaEvent = new HashMap<>();
        kafkaEvent.put("candidate_id", candidateId);
        kafkaEvent.put("resume_text", candidate.getResumeText());
        kafkaEvent.put("job_description", job.getDescription() + "\nRequirements: " + job.getRequirements());
        resumeEventProducer.sendResumeEvent(kafkaEvent);

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/api/ai/screen")
                    .bodyValue(Map.of(
                            "resume_text", candidate.getResumeText(),
                            "job_description", job.getDescription() + "\nRequirements: " + job.getRequirements()
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                Object score = response.get("score");
                candidate.setAiScore(score instanceof Number ? ((Number) score).intValue() : 50);
                candidate.setAiRecommendation((String) response.get("recommendation"));

                Object strengths = response.get("strengths");
                if (strengths instanceof java.util.List) {
                    candidate.setStrengths(String.join(", ", (java.util.List<String>) strengths));
                }

                Object weaknesses = response.get("weaknesses");
                if (weaknesses instanceof java.util.List) {
                    candidate.setWeaknesses(String.join(", ", (java.util.List<String>) weaknesses));
                }

                candidate.setStatus(Candidate.CandidateStatus.SCREENED);
                candidateService.update(candidate);

                return response;
            }
        } catch (Exception e) {
            log.error("Failed to call AI service for candidate {}: {}", candidateId, e.getMessage());
        }

        return Map.of(
                "score", 0,
                "strengths", java.util.List.of(),
                "weaknesses", java.util.List.of("AI service unavailable"),
                "recommendation", "Pending",
                "status", "async"
        );
    }
}
