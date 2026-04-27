-- V1: Initialize database schema for workshop registration system

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    student_code VARCHAR(20) UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL
);

CREATE TABLE workshops (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    total_slots INTEGER NOT NULL,
    remaining_slots INTEGER NOT NULL,
    price DECIMAL DEFAULT 0,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL
);

CREATE TABLE registrations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    workshop_id BIGINT REFERENCES workshops(id),
    qr_code VARCHAR(255) UNIQUE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    registration_id BIGINT UNIQUE REFERENCES registrations(id),
    amount DECIMAL NOT NULL,
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    transaction_id VARCHAR(255),
    status VARCHAR(20) NOT NULL
);

CREATE TABLE checkin_records (
    registration_id BIGINT PRIMARY KEY REFERENCES registrations(id),
    scanned_at TIMESTAMP NOT NULL,
    synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);