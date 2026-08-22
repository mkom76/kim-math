package com.example.service;

import com.example.entity.AcademyClass;

public final class AcademyClassPolicy {
    private AcademyClassPolicy() {
    }

    public static void assertActive(AcademyClass academyClass) {
        if (academyClass.isEnded()) {
            throw new IllegalArgumentException("종강한 반에는 새 항목을 등록할 수 없습니다. 먼저 반 운영을 재개해주세요.");
        }
    }
}
