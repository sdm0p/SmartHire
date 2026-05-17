package com.smarthire.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreeningRequest {
    private String resumeText;
    private String jobDescription;
}
