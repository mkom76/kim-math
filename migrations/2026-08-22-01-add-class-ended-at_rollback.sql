ALTER TABLE academy_classes
    DROP INDEX idx_academy_classes_academy_ended,
    DROP COLUMN ended_at;
