package com.smarthire.service;

import com.smarthire.dto.JobDto;
import com.smarthire.model.Job;
import com.smarthire.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobService jobService;

    private JobDto sampleDto;
    private Job savedJob;

    @BeforeEach
    void setUp() {
        sampleDto = JobDto.builder()
                .title("Senior Java Developer")
                .description("Build scalable backend services using Java 17 and Spring Boot")
                .requirements("Java 17, Spring Boot 3.x, PostgreSQL, Kafka, 5+ years experience")
                .build();

        savedJob = Job.builder()
                .id(1L)
                .title("Senior Java Developer")
                .description("Build scalable backend services using Java 17 and Spring Boot")
                .requirements("Java 17, Spring Boot 3.x, PostgreSQL, Kafka, 5+ years experience")
                .build();
    }

    @Test
    void saveJobSuccess() {
        when(jobRepository.save(any(Job.class))).thenReturn(savedJob);

        Job result = jobService.create(sampleDto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Senior Java Developer");
        assertThat(result.getDescription()).contains("Java 17");
        assertThat(result.getRequirements()).contains("Spring Boot");
        verify(jobRepository, times(1)).save(any(Job.class));
    }

    @Test
    void getJobByIdNotFound() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.findById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Job not found with id: 99");
    }

    @Test
    void getJobByIdSuccess() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(savedJob));

        Job result = jobService.findById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Senior Java Developer");
    }

    @Test
    void findAllJobsReturnsAllJobs() {
        Job job2 = Job.builder()
                .id(2L)
                .title("DevOps Engineer")
                .description("Manage CI/CD pipelines and cloud infrastructure")
                .requirements("Docker, Kubernetes, AWS")
                .build();
        when(jobRepository.findAll()).thenReturn(List.of(savedJob, job2));

        List<Job> result = jobService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("Senior Java Developer");
        assertThat(result.get(1).getTitle()).isEqualTo("DevOps Engineer");
    }

    @Test
    void toDtoMapsAllFieldsCorrectly() {
        JobDto dto = jobService.toDto(savedJob);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getTitle()).isEqualTo("Senior Java Developer");
        assertThat(dto.getDescription()).contains("Java 17");
    }
}