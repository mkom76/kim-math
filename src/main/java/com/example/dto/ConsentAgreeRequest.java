package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ConsentAgreeRequest {
    /** Last 4 digits of the parent phone, used as the identity check on the link. */
    @NotBlank
    @Pattern(regexp = "^[0-9]{4}$", message = "휴대폰 뒤 4자리(숫자)를 입력하세요")
    private String parentPhoneLast4;
}
