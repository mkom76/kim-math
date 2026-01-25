package com.example.controller;

import com.example.dto.*;
import com.example.entity.ClinicRegistrationStatus;
import com.example.service.ClinicService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/clinics")
@RequiredArgsConstructor
public class ClinicController {
    private final ClinicService clinicService;

    @PostMapping("/class/{classId}/create-for-this-week")
    public ResponseEntity<ClinicDto> createClinicForThisWeek(@PathVariable Long classId) {
        return ResponseEntity.ok(clinicService.createClinicForThisWeek(classId));
    }

    @PostMapping("/class/{classId}")
    public ResponseEntity<ClinicDto> createClinic(
            @PathVariable Long classId,
            @RequestBody CreateClinicRequest request) {
        return ResponseEntity.ok(clinicService.createClinic(
                classId, request.getClinicDate(), request.getClinicTime()));
    }

    @GetMapping("/class/{classId}")
    public ResponseEntity<List<ClinicDto>> getClinicsByClass(@PathVariable Long classId) {
        return ResponseEntity.ok(clinicService.getClinicsByClass(classId));
    }

    @GetMapping("/class/{classId}/upcoming")
    public ResponseEntity<ClinicDto> getUpcomingClinic(@PathVariable Long classId) {
        Optional<ClinicDto> clinic = clinicService.getUpcomingClinic(classId);
        return clinic.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/{clinicId}/detail")
    public ResponseEntity<ClinicDetailDto> getClinicDetail(@PathVariable Long clinicId) {
        return ResponseEntity.ok(clinicService.getClinicDetail(clinicId));
    }

    @PostMapping("/{clinicId}/register")
    public ResponseEntity<ClinicRegistrationDto> registerForClinic(
            @PathVariable Long clinicId,
            @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(clinicService.registerForClinic(clinicId, request.getStudentId()));
    }

    @DeleteMapping("/{clinicId}/register/{studentId}")
    public ResponseEntity<Void> cancelRegistration(
            @PathVariable Long clinicId,
            @PathVariable Long studentId) {
        clinicService.cancelRegistration(clinicId, studentId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/registrations/{registrationId}/attendance")
    public ResponseEntity<ClinicRegistrationDto> updateAttendance(
            @PathVariable Long registrationId,
            @RequestBody UpdateAttendanceRequest request) {
        return ResponseEntity.ok(clinicService.updateAttendance(registrationId, request.getStatus()));
    }

    @GetMapping("/student/{studentId}/info")
    public ResponseEntity<StudentClinicInfoDto> getStudentClinicInfo(@PathVariable Long studentId) {
        return ResponseEntity.ok(clinicService.getStudentClinicInfo(studentId));
    }

    @PutMapping("/{clinicId}/close")
    public ResponseEntity<ClinicDto> closeClinic(@PathVariable Long clinicId) {
        return ResponseEntity.ok(clinicService.closeClinic(clinicId));
    }

    @DeleteMapping("/{clinicId}")
    public ResponseEntity<Void> deleteClinic(@PathVariable Long clinicId) {
        clinicService.deleteClinic(clinicId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{clinicId}/start")
    public ResponseEntity<Void> startClinic(@PathVariable Long clinicId) {
        clinicService.startClinic(clinicId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{clinicId}/end")
    public ResponseEntity<Void> endClinic(@PathVariable Long clinicId) {
        clinicService.endClinic(clinicId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{clinicId}/progress")
    public ResponseEntity<List<ClinicHomeworkProgressDto>> getClinicProgress(@PathVariable Long clinicId) {
        return ResponseEntity.ok(clinicService.getClinicProgress(clinicId));
    }

    @GetMapping("/student/{studentId}/recent-result")
    public ResponseEntity<RecentClinicResultDto> getRecentClinicResult(@PathVariable Long studentId) {
        Optional<RecentClinicResultDto> result = clinicService.getRecentClinicResult(studentId);
        return result.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.noContent().build());
    }

    // Request DTOs
    public static class CreateClinicRequest {
        private LocalDate clinicDate;
        private LocalTime clinicTime;

        public LocalDate getClinicDate() {
            return clinicDate;
        }

        public void setClinicDate(LocalDate clinicDate) {
            this.clinicDate = clinicDate;
        }

        public LocalTime getClinicTime() {
            return clinicTime;
        }

        public void setClinicTime(LocalTime clinicTime) {
            this.clinicTime = clinicTime;
        }
    }

    public static class RegisterRequest {
        private Long studentId;

        public Long getStudentId() {
            return studentId;
        }

        public void setStudentId(Long studentId) {
            this.studentId = studentId;
        }
    }

    public static class UpdateAttendanceRequest {
        private ClinicRegistrationStatus status;

        public ClinicRegistrationStatus getStatus() {
            return status;
        }

        public void setStatus(ClinicRegistrationStatus status) {
            this.status = status;
        }
    }
}
