-- V5: Add constraints to tables
ALTER TABLE workshops ADD CONSTRAINT check_workshop_times CHECK (start_time < end_time);
ALTER TABLE workshops ADD CONSTRAINT check_registration_times CHECK (registration_start_time < registration_end_time);
ALTER TABLE workshops ADD CONSTRAINT check_positive_slots CHECK (remaining_slots >= 0);
ALTER TABLE workshops ADD CONSTRAINT check_slots CHECK (remaining_slots <= total_slots);

ALTER TABLE registrations ADD CONSTRAINT unique_user_workshop UNIQUE (user_id, workshop_id);

ALTER TABLE payments ALTER COLUMN amount TYPE BIGINT;