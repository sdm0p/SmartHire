package com.smarthire.controller;

import com.smarthire.dto.CandidateDto;
import com.smarthire.model.Candidate;
import com.smarthire.service.AIScreeningService;
import com.smarthire.service.CandidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
@Tag(name = "Candidates", description = "Candidate management")
public class CandidateController {

    private final CandidateService candidateService;
    private final AIScreeningService aiScreeningService;

    @PostMapping
    @Operation(summary = "Create a new candidate")
    public ResponseEntity<CandidateDto> create(@Valid @RequestBody CandidateDto dto) {
        Candidate created = candidateService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(candidateService.toDto(created));
    }

    @GetMapping
    @Operation(summary = "List all candidates with pagination")
    public ResponseEntity<Page<CandidateDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);
        Page<Candidate> candidates = candidateService.findAll(pageable);
        return ResponseEntity.ok(candidates.map(candidateService::toDto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get candidate by ID")
    public ResponseEntity<CandidateDto> getById(@PathVariable Long id) {
        Candidate candidate = candidateService.findById(id);
        return ResponseEntity.ok(candidateService.toDto(candidate));
    }

    @PostMapping("/{id}/screen/{jobId}")
    @Operation(summary = "Trigger AI screening for a candidate against a job")
    public ResponseEntity<Map<String, Object>> screen(
            @PathVariable Long id,
            @PathVariable Long jobId
    ) {
        Map<String, Object> result = aiScreeningService.screen(id, jobId);
        return ResponseEntity.ok(result);
    }
}
