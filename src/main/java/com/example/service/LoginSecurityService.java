package com.example.service;

import com.example.entity.Student;
import com.example.entity.Teacher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class LoginSecurityService {

    private static final int LOCK_THRESHOLD = 5;
    private static final long MAX_LOCK_MINUTES = 15;

    public boolean isLocked(Student student, LocalDateTime now) {
        return student.getLockedUntil() != null && student.getLockedUntil().isAfter(now);
    }

    public boolean isLocked(Teacher teacher, LocalDateTime now) {
        return teacher.getLockedUntil() != null && teacher.getLockedUntil().isAfter(now);
    }

    public void recordFailure(Student student, LocalDateTime now) {
        int failures = safeCount(student.getFailedLoginCount()) + 1;
        student.setFailedLoginCount(failures);
        if (failures >= LOCK_THRESHOLD) {
            student.setLockedUntil(now.plusMinutes(lockMinutes(failures)));
        }
    }

    public void recordFailure(Teacher teacher, LocalDateTime now) {
        int failures = safeCount(teacher.getFailedLoginCount()) + 1;
        teacher.setFailedLoginCount(failures);
        if (failures >= LOCK_THRESHOLD) {
            teacher.setLockedUntil(now.plusMinutes(lockMinutes(failures)));
        }
    }

    public void recordSuccess(Student student, LocalDateTime now) {
        student.setFailedLoginCount(0);
        student.setLockedUntil(null);
        student.setLastLoginAt(now);
    }

    public void recordSuccess(Teacher teacher, LocalDateTime now) {
        teacher.setFailedLoginCount(0);
        teacher.setLockedUntil(null);
        teacher.setLastLoginAt(now);
    }

    public String lockMessage(LocalDateTime lockedUntil, LocalDateTime now) {
        long seconds = Math.max(1, Duration.between(now, lockedUntil).toSeconds());
        long minutes = Math.max(1, (long) Math.ceil(seconds / 60.0));
        return "로그인 시도가 많아 잠시 잠겼습니다. " + minutes + "분 후 다시 시도해주세요.";
    }

    private int safeCount(Integer count) {
        return count == null ? 0 : count;
    }

    private long lockMinutes(int failures) {
        int exponent = Math.min(failures - LOCK_THRESHOLD, 4);
        return Math.min(MAX_LOCK_MINUTES, 1L << exponent);
    }
}
