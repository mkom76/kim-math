package com.example.controller;

import com.example.dto.DeviceTokenRegisterRequest;
import com.example.exception.ForbiddenException;
import com.example.service.DeviceTokenService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Device-token registration for push notifications. Identity is taken from the
 * session: only the currently logged-in student can register a device for
 * themselves (we deliberately don't accept studentId from the body).
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody DeviceTokenRegisterRequest req,
                                         HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        String userRole = (String) session.getAttribute("userRole");
        if (userId == null || !"STUDENT".equals(userRole)) {
            throw new ForbiddenException("학생 로그인이 필요합니다");
        }
        deviceTokenService.register(userId, req.getToken(), req.getPlatform(), req.getAppVersion());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{token}")
    public ResponseEntity<Void> unregister(@PathVariable String token, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        String userRole = (String) session.getAttribute("userRole");
        if (userId == null || !"STUDENT".equals(userRole)) {
            throw new ForbiddenException("학생 로그인이 필요합니다");
        }
        deviceTokenService.unregister(token);
        return ResponseEntity.ok().build();
    }
}
