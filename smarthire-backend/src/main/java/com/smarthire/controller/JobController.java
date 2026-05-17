package com.smarthire.controller;

import com.smarthire.dto.JobDto;
import com.smarthire.model.Job;
import com.smarthire.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "Job management")
public class JobController {

    private final JobService jobService;

    @PostMapping
    @Operation(summary = "Create a new job")
    public ResponseEntity<JobDto> create(@Valid @RequestBody JobDto dto) {
        Job created = jobService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.toDto(created));
    }

    @GetMapping
    @Operation(summary = "List all jobs")
    public ResponseEntity<List<JobDto>> list() {
        List<Job> jobs = jobService.findAll();
        return ResponseEntity.ok(jobs.stream().map(jobService::toDto).toList());
    }
}
