package com.example.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudentUiFeedbackRequest(
        @NotBlank @Size(max = 24) String sentiment,
        @Size(max = 32) String category,
        @Size(max = 500) String message,
        @NotBlank @Size(max = 255) String pagePath,
        @NotBlank @Size(max = 20) String uiVersion,
        @Min(0) @Max(10000) Integer viewportWidth,
        @Size(max = 20) String platform,
        @Size(max = 50) String appVersion
) {
}
