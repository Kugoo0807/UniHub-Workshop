-- V10: Seed additional demo data for workshops, rooms, registrations, payments, and check-ins.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Add one more student so the seeded registrations are spread across 2 student users:
-- 21127001@student.unihub.edu.vn already exists from V2.
INSERT INTO users (student_code, full_name, email, password, role, status)
VALUES
    ('21127002', 'Tran Thi B', '21127002@student.unihub.edu.vn', crypt('password123', gen_salt('bf', 12)), 'STUDENT', 'ACTIVE')
ON CONFLICT (email) DO NOTHING;

-- 5 additional rooms.
INSERT INTO rooms (name, layout_map_url, capacity)
VALUES
    ('Room E5 - Innovation Hall', NULL, 90),
    ('Room F6 - Engineering Lab', NULL, 70),
    ('Auditorium G7', NULL, 150),
    ('Lab H8 - Research Studio', NULL, 45),
    ('Studio I9 - Product Space', NULL, 60)
ON CONFLICT (name) DO NOTHING;

-- 10 additional workshops:
-- 5 PUBLISHED, 2 DRAFT, 2 CANCELLED, 1 COMPLETED.
INSERT INTO workshops (
    title,
    description,
    room_id,
    speaker,
    status,
    total_slots,
    remaining_slots,
    price,
    start_time,
    end_time,
    registration_start_time,
    registration_end_time
)
SELECT
    data.title,
    data.description,
    room_lookup.id,
    data.speaker,
    data.status,
    data.total_slots,
    data.remaining_slots,
    data.price,
    data.start_time,
    data.end_time,
    data.registration_start_time,
    data.registration_end_time
FROM (
    VALUES
        (
            'Seed Demo: Java Spring Boot Patterns',
            'Hands-on patterns for building maintainable Spring Boot services.',
            'Room E5 - Innovation Hall',
            'Dr. Le Minh Quan',
            'PUBLISHED',
            70,
            68,
            75000::BIGINT,
            '2026-05-16 08:30:00'::TIMESTAMP,
            '2026-05-20 17:00:00'::TIMESTAMP,
            '2026-05-01 08:00:00'::TIMESTAMP,
            '2026-05-15 23:59:00'::TIMESTAMP
        ),
        (
            'Seed Demo: UI UX Research Sprint',
            'Practice user interviews, affinity mapping, and prototype validation.',
            'Lab H8 - Research Studio',
            'Ms. Pham Ngoc Anh',
            'PUBLISHED',
            45,
            44,
            0::BIGINT,
            '2026-05-17 09:00:00'::TIMESTAMP,
            '2026-05-18 18:00:00'::TIMESTAMP,
            '2026-05-01 08:00:00'::TIMESTAMP,
            '2026-05-16 23:59:00'::TIMESTAMP
        ),
        (
            'Seed Demo: Data Engineering with Kafka',
            'Build a small event pipeline and learn streaming data fundamentals.',
            'Room F6 - Engineering Lab',
            'Mr. Do Hoang Nam',
            'PUBLISHED',
            55,
            54,
            120000::BIGINT,
            '2026-05-16 13:30:00'::TIMESTAMP,
            '2026-05-19 17:00:00'::TIMESTAMP,
            '2026-05-01 08:00:00'::TIMESTAMP,
            '2026-05-15 23:59:00'::TIMESTAMP
        ),
        (
            'Seed Demo: Cybersecurity Blue Team Lab',
            'Monitor logs, triage alerts, and respond to common security incidents.',
            'Studio I9 - Product Space',
            'Mr. Vo Gia Huy',
            'PUBLISHED',
            40,
            39,
            150000::BIGINT,
            '2026-06-22 08:00:00'::TIMESTAMP,
            '2026-06-22 12:00:00'::TIMESTAMP,
            '2026-05-15 08:00:00'::TIMESTAMP,
            '2026-06-20 23:59:00'::TIMESTAMP
        ),
        (
            'Seed Demo: Career Talk Product Mindset',
            'A practical conversation about product thinking for software engineers.',
            'Auditorium G7',
            'Ms. Nguyen Bao Tram',
            'PUBLISHED',
            80,
            80,
            0::BIGINT,
            '2026-06-25 14:00:00'::TIMESTAMP,
            '2026-06-25 17:00:00'::TIMESTAMP,
            '2026-05-15 08:00:00'::TIMESTAMP,
            '2026-06-23 23:59:00'::TIMESTAMP
        ),
        (
            'Seed Demo: AI Prompting for Analysts',
            'Draft workshop for practicing prompt workflows in data analysis.',
            'Lab H8 - Research Studio',
            'TBD',
            'DRAFT',
            35,
            35,
            0::BIGINT,
            '2026-07-03 09:00:00'::TIMESTAMP,
            '2026-07-03 11:30:00'::TIMESTAMP,
            '2026-06-01 08:00:00'::TIMESTAMP,
            '2026-07-01 23:59:00'::TIMESTAMP
        ),
        (
            'Seed Demo: Mobile Flutter Clinic',
            'Draft workshop for debugging Flutter layouts and state management.',
            'Room F6 - Engineering Lab',
            'TBD',
            'DRAFT',
            50,
            50,
            90000::BIGINT,
            '2026-07-10 13:30:00'::TIMESTAMP,
            '2026-07-10 17:00:00'::TIMESTAMP,
            '2026-06-05 08:00:00'::TIMESTAMP,
            '2026-07-08 23:59:00'::TIMESTAMP
        ),
        (
            'Seed Demo: Git Collaboration Deep Dive',
            'Cancelled workshop kept for testing cancellation history screens.',
            'Room E5 - Innovation Hall',
            'Mr. Bui Thanh Son',
            'CANCELLED',
            60,
            60,
            0::BIGINT,
            '2026-06-05 08:30:00'::TIMESTAMP,
            '2026-06-05 11:30:00'::TIMESTAMP,
            '2026-05-01 08:00:00'::TIMESTAMP,
            '2026-06-03 23:59:00'::TIMESTAMP
        ),
        (
            'Seed Demo: Database Indexing Clinic',
            'Cancelled workshop for index tuning and query plan practice.',
            'Studio I9 - Product Space',
            'Dr. Hoang Duc Minh',
            'CANCELLED',
            45,
            45,
            100000::BIGINT,
            '2026-06-30 09:00:00'::TIMESTAMP,
            '2026-06-30 12:00:00'::TIMESTAMP,
            '2026-05-20 08:00:00'::TIMESTAMP,
            '2026-06-28 23:59:00'::TIMESTAMP
        ),
        (
            'Seed Demo: Alumni Tech Talk',
            'Completed alumni sharing session for attendance and revenue reports.',
            'Auditorium G7',
            'Ms. Le Thanh Ha',
            'COMPLETED',
            100,
            98,
            50000::BIGINT,
            '2026-05-05 08:00:00'::TIMESTAMP,
            '2026-05-05 11:00:00'::TIMESTAMP,
            '2026-04-20 08:00:00'::TIMESTAMP,
            '2026-05-03 23:59:00'::TIMESTAMP
        )
) AS data(
    title,
    description,
    room_name,
    speaker,
    status,
    total_slots,
    remaining_slots,
    price,
    start_time,
    end_time,
    registration_start_time,
    registration_end_time
)
JOIN rooms room_lookup ON room_lookup.name = data.room_name
WHERE NOT EXISTS (
    SELECT 1
    FROM workshops existing_workshop
    WHERE existing_workshop.title = data.title
);

-- 8 registrations from 2 users.
INSERT INTO registrations (user_id, workshop_id, qr_code, status, created_at)
SELECT
    user_lookup.id,
    workshop_lookup.id,
    data.qr_code,
    data.status,
    data.created_at
FROM (
    VALUES
        (
            '21127001@student.unihub.edu.vn',
            'Seed Demo: Java Spring Boot Patterns',
            'QR-SEED-SPRING-21127001',
            'SUCCESS',
            '2026-05-10 09:00:00'::TIMESTAMP
        ),
        (
            '21127002@student.unihub.edu.vn',
            'Seed Demo: Java Spring Boot Patterns',
            'QR-SEED-SPRING-21127002',
            'SUCCESS',
            '2026-05-10 09:05:00'::TIMESTAMP
        ),
        (
            '21127001@student.unihub.edu.vn',
            'Seed Demo: UI UX Research Sprint',
            'QR-SEED-UX-21127001',
            'SUCCESS',
            '2026-05-12 10:00:00'::TIMESTAMP
        ),
        (
            '21127002@student.unihub.edu.vn',
            'Seed Demo: Data Engineering with Kafka',
            'QR-SEED-KAFKA-21127002',
            'SUCCESS',
            '2026-05-11 14:00:00'::TIMESTAMP
        ),
        (
            '21127001@student.unihub.edu.vn',
            'Seed Demo: Cybersecurity Blue Team Lab',
            'QR-SEED-BLUE-PENDING-21127001',
            'PENDING',
            (CURRENT_TIMESTAMP - INTERVAL '2 minutes')::TIMESTAMP
        ),
        (
            '21127002@student.unihub.edu.vn',
            'Seed Demo: Git Collaboration Deep Dive',
            'QR-SEED-GIT-CANCELLED-21127002',
            'CANCELLED',
            '2026-05-04 16:00:00'::TIMESTAMP
        ),
        (
            '21127001@student.unihub.edu.vn',
            'Seed Demo: Alumni Tech Talk',
            'QR-SEED-ALUMNI-21127001',
            'SUCCESS',
            '2026-04-25 09:00:00'::TIMESTAMP
        ),
        (
            '21127002@student.unihub.edu.vn',
            'Seed Demo: Alumni Tech Talk',
            'QR-SEED-ALUMNI-21127002',
            'SUCCESS',
            '2026-04-25 09:10:00'::TIMESTAMP
        )
) AS data(user_email, workshop_title, qr_code, status, created_at)
JOIN users user_lookup ON user_lookup.email = data.user_email
JOIN LATERAL (
    SELECT id
    FROM workshops
    WHERE title = data.workshop_title
    ORDER BY id DESC
    LIMIT 1
) workshop_lookup ON TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM registrations existing_registration
    WHERE existing_registration.qr_code = data.qr_code
);

-- 5 payments linked to paid registrations.
INSERT INTO payments (registration_id, amount, idempotency_key, transaction_id, status)
SELECT
    registration_lookup.id,
    data.amount,
    data.idempotency_key,
    data.transaction_id,
    data.status
FROM (
    VALUES
        (
            'QR-SEED-SPRING-21127001',
            75000::BIGINT,
            'SEED-PAY-SPRING-21127001',
            'TX-SEED-SPRING-21127001',
            'COMPLETED'
        ),
        (
            'QR-SEED-SPRING-21127002',
            75000::BIGINT,
            'SEED-PAY-SPRING-21127002',
            'TX-SEED-SPRING-21127002',
            'COMPLETED'
        ),
        (
            'QR-SEED-KAFKA-21127002',
            120000::BIGINT,
            'SEED-PAY-KAFKA-21127002',
            'TX-SEED-KAFKA-21127002',
            'COMPLETED'
        ),
        (
            'QR-SEED-BLUE-PENDING-21127001',
            150000::BIGINT,
            'SEED-PAY-BLUE-21127001',
            NULL::VARCHAR,
            'PENDING'
        ),
        (
            'QR-SEED-ALUMNI-21127002',
            50000::BIGINT,
            'SEED-PAY-ALUMNI-21127002',
            'TX-SEED-ALUMNI-21127002',
            'COMPLETED'
        )
) AS data(qr_code, amount, idempotency_key, transaction_id, status)
JOIN registrations registration_lookup ON registration_lookup.qr_code = data.qr_code
WHERE NOT EXISTS (
    SELECT 1
    FROM payments existing_payment
    WHERE existing_payment.idempotency_key = data.idempotency_key
);

-- 6 check-in records for SUCCESS registrations.
INSERT INTO checkin_records (registration_id, scanned_at, synced_at)
SELECT
    registration_lookup.id,
    data.scanned_at,
    data.synced_at
FROM (
    VALUES
        (
            'QR-SEED-SPRING-21127001',
            '2026-05-16 08:45:00'::TIMESTAMP,
            '2026-05-16 08:46:00'::TIMESTAMP
        ),
        (
            'QR-SEED-SPRING-21127002',
            '2026-05-16 08:50:00'::TIMESTAMP,
            '2026-05-16 08:51:00'::TIMESTAMP
        ),
        (
            'QR-SEED-UX-21127001',
            '2026-05-17 09:05:00'::TIMESTAMP,
            '2026-05-17 09:06:00'::TIMESTAMP
        ),
        (
            'QR-SEED-KAFKA-21127002',
            '2026-05-16 13:45:00'::TIMESTAMP,
            '2026-05-16 13:46:00'::TIMESTAMP
        ),
        (
            'QR-SEED-ALUMNI-21127001',
            '2026-05-05 08:05:00'::TIMESTAMP,
            '2026-05-05 08:06:00'::TIMESTAMP
        ),
        (
            'QR-SEED-ALUMNI-21127002',
            '2026-05-05 08:07:00'::TIMESTAMP,
            '2026-05-05 08:08:00'::TIMESTAMP
        )
) AS data(qr_code, scanned_at, synced_at)
JOIN registrations registration_lookup ON registration_lookup.qr_code = data.qr_code
WHERE NOT EXISTS (
    SELECT 1
    FROM checkin_records existing_checkin
    WHERE existing_checkin.registration_id = registration_lookup.id
);
