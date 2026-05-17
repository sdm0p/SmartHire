package com.smarthire.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import com.smarthire.model.Candidate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateDto {
    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String resumeText;

    private Candidate.CandidateStatus status;

    private Integer aiScore;
    private String aiRecommendation;
    private String strengths;
    private String weaknesses;
    private Long appliedJobId;
}
