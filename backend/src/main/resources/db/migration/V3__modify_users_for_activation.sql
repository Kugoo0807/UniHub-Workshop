-- V3: Adding phone_number, status và allow NULLABLE password value

ALTER TABLE users
    ADD COLUMN phone_number VARCHAR(15) UNIQUE,
    ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE', -- Current records will be set to 'ACTIVE' by default
    ALTER COLUMN password DROP NOT NULL;

-- Default 'INACTIVE' for new records
ALTER TABLE users
    ALTER COLUMN status SET DEFAULT 'INACTIVE';
