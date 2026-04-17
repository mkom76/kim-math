-- Rollback Migration: Remove Clinic Management Tables
-- Date: 2026-01-10
-- Description: Rollback clinic tables and columns

-- 1. Drop clinic_registrations table (must drop first due to FK constraint)
DROP TABLE IF EXISTS clinic_registrations;

-- 2. Drop clinics table
DROP TABLE IF EXISTS clinics;

-- 3. Remove clinic settings columns from academy_classes
ALTER TABLE academy_classes
DROP COLUMN IF EXISTS clinic_day_of_week,
DROP COLUMN IF EXISTS clinic_time;
