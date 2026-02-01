CREATE TABLE student_video_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    lesson_video_id BIGINT NOT NULL,
    current_time INT NOT NULL DEFAULT 0,
    duration INT NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    last_watched_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (lesson_video_id) REFERENCES lesson_videos(id) ON DELETE CASCADE,

    UNIQUE KEY uk_student_video (student_id, lesson_video_id),
    INDEX idx_student_id (student_id),
    INDEX idx_lesson_video_id (lesson_video_id),
    INDEX idx_completed (completed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
