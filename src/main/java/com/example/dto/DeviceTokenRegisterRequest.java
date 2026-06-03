package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DeviceTokenRegisterRequest {
    @NotBlank
    private String token;

    @NotBlank
    @Pattern(regexp = "android|ios", message = "platform은 'android' 또는 'ios'여야 합니다")
    private String platform;

    private String appVersion;
}
