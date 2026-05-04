-- V4: Complete workshops schema and create rooms table

CREATE TABLE rooms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    layout_map_url VARCHAR(1024),
    capacity INTEGER NOT NULL
);

ALTER TABLE workshops
    ADD COLUMN room_id BIGINT REFERENCES rooms(id),
    ADD COLUMN speaker VARCHAR(255) DEFAULT 'TBD',
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN registration_start_time TIMESTAMP,
    ADD COLUMN registration_end_time TIMESTAMP;

-- Create sample rooms
INSERT INTO rooms (name, layout_map_url, capacity) VALUES
    ('Room A1', NULL, 80),
    ('Room B2', NULL, 50),
    ('Room C3', NULL, 120),
    ('Room D4', NULL, 60);

-- Update existing workshops with default values
UPDATE workshops
SET speaker = 'TBD'
WHERE speaker IS NULL;

-- Times & status updates
UPDATE workshops
SET registration_start_time = start_time,
    registration_end_time = end_time
WHERE registration_start_time IS NULL
  AND registration_end_time IS NULL;

UPDATE workshops
SET start_time = (registration_end_time::date + 5) + TIME '08:00',
    end_time = (registration_end_time::date + 5) + TIME '11:00';

UPDATE workshops
SET status = 'PUBLISHED';

-- Assign rooms to workshops and adjust slots based on room capacity
UPDATE workshops
SET room_id = (
    SELECT id
    FROM rooms
    ORDER BY random()
    LIMIT 1
)
WHERE room_id IS NULL;

UPDATE workshops
SET total_slots = LEAST(total_slots, rooms.capacity),
    remaining_slots = LEAST(remaining_slots, rooms.capacity)
FROM rooms
WHERE workshops.room_id = rooms.id
  AND (workshops.total_slots > rooms.capacity
       OR workshops.remaining_slots > rooms.capacity);

ALTER TABLE workshops
    ALTER COLUMN room_id SET NOT NULL,
    ALTER COLUMN speaker SET NOT NULL,
    ALTER COLUMN registration_start_time SET NOT NULL,
    ALTER COLUMN registration_end_time SET NOT NULL;
