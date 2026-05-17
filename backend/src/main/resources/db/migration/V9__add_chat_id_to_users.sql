-- V9: Adding chat id (TELEGRAM) to users
ALTER TABLE users
    ADD COLUMN chat_id VARCHAR(255);