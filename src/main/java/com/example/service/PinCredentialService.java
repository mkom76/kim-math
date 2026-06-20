package com.example.service;

import com.example.entity.Student;
import com.example.entity.Teacher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PinCredentialService {

    private static final String STUDENT_PIN_PATTERN = "\\d{4}";
    private static final String TEACHER_PIN_PATTERN = "\\d{6}";

    private final PasswordEncoder passwordEncoder;

    public void setStudentPin(Student student, String rawPin) {
        validateStudentPin(rawPin);
        student.setPinHash(passwordEncoder.encode(rawPin));
        student.setPin(null);
        student.setFailedLoginCount(0);
        student.setLockedUntil(null);
    }

    public void setTeacherPin(Teacher teacher, String rawPin) {
        validateTeacherPin(rawPin);
        teacher.setPinHash(passwordEncoder.encode(rawPin));
        teacher.setPin(null);
        teacher.setFailedLoginCount(0);
        teacher.setLockedUntil(null);
    }

    public boolean verifyStudentPin(Student student, String rawPin) {
        if (!StringUtils.hasText(rawPin)) return false;
        if (StringUtils.hasText(student.getPinHash())) {
            return passwordEncoder.matches(rawPin, student.getPinHash());
        }
        if (rawPin.equals(student.getPin())) {
            setStudentPin(student, rawPin);
            return true;
        }
        return false;
    }

    public boolean verifyTeacherPin(Teacher teacher, String rawPin) {
        if (!StringUtils.hasText(rawPin)) return false;
        if (StringUtils.hasText(teacher.getPinHash())) {
            return passwordEncoder.matches(rawPin, teacher.getPinHash());
        }
        if (rawPin.equals(teacher.getPin())) {
            setTeacherPin(teacher, rawPin);
            return true;
        }
        return false;
    }

    public void validateStudentPin(String rawPin) {
        if (rawPin == null || !rawPin.matches(STUDENT_PIN_PATTERN)) {
            throw new IllegalArgumentException("학생 PIN은 숫자 4자리여야 합니다");
        }
    }

    public void validateTeacherPin(String rawPin) {
        if (rawPin == null || !rawPin.matches(TEACHER_PIN_PATTERN)) {
            throw new IllegalArgumentException("선생님 PIN은 숫자 6자리여야 합니다");
        }
    }
}
