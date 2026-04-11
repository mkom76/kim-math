package com.example.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateClassOwnerRequest {
    @NotNull
    private Long teacherId;
}
