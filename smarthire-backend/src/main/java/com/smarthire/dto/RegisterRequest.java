package com.smarthire.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {
    @NotBlank
    private String username;

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6)
    private String password;
}
