package com.example.controller;

import com.example.dto.ConsentAgreeRequest;
import com.example.dto.ConsentInfoResponse;
import com.example.service.ConsentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public-facing consent endpoints. Path {@code /api/consents/**} is whitelisted
 * in {@link com.example.config.security.SecurityConfig}; identity is proved
 * with token + parent-phone-last-4 inside the service.
 */
@RestController
@RequestMapping("/api/consents")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    @GetMapping("/{token}")
    public ResponseEntity<ConsentInfoResponse> get(@PathVariable String token) {
        return ResponseEntity.ok(consentService.get(token));
    }

    @PostMapping("/{token}/agree")
    public ResponseEntity<Void> agree(@PathVariable String token,
                                      @Valid @RequestBody ConsentAgreeRequest req,
                                      HttpServletRequest http) {
        consentService.agree(token, req, http);
        return ResponseEntity.ok().build();
    }
}
