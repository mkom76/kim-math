-- Migration: Add Clinic Management Tables
-- Date: 2026-01-10
-- Description: Add clinics and clinic_registrations tables, add clinic settings to academy_classes

-- 1. Add clinic settings to academy_classes table
ALTER TABLE academy_classes
ADD COLUMN clinic_day_of_week VARCHAR(20) NULL,
ADD COLUMN clinic_time TIME NULL;

-- 2. Create clinics table
CREATE TABLE clinics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    clinic_date DATE NOT NULL,
    clinic_time TIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_clinics_class FOREIGN KEY (class_id) REFERENCES academy_classes(id) ON DELETE CASCADE,
    INDEX idx_clinic_class_date (class_id, clinic_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Create clinic_registrations table
CREATE TABLE clinic_registrations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    clinic_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_clinic_registrations_clinic FOREIGN KEY (clinic_id) REFERENCES clinics(id) ON DELETE CASCADE,
    CONSTRAINT fk_clinic_registrations_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT uk_clinic_student UNIQUE (clinic_id, student_id),
    INDEX idx_clinic_registration_clinic (clinic_id),
    INDEX idx_clinic_registration_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. (Optional) Add sample clinic settings to existing classes
-- UPDATE academy_classes SET clinic_day_of_week = 'SATURDAY', clinic_time = '10:00:00' WHERE id = 1;
-- UPDATE academy_classes SET clinic_day_of_week = 'SATURDAY', clinic_time = '14:00:00' WHERE id = 2;
