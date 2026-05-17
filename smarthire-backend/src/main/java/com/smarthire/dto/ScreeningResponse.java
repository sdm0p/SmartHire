package com.smarthire.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreeningResponse {
    private int score;
    private java.util.List<String> strengths;
    private java.util.List<String> weaknesses;
    private String recommendation;
}
