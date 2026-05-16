-- V8: Create notifications table and optimize query indexes

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id), -- Allow NULL for system-wide notifications
    title VARCHAR(255) NOT NULL,
    content_html TEXT NOT NULL,
    channel VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index to optimize fetching paginated notifications for a specific user (sorted by newest first)
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);

-- Index to optimize the background cron job searching for failed messages to retry
CREATE INDEX idx_notifications_status_retry ON notifications (status, retry_count);