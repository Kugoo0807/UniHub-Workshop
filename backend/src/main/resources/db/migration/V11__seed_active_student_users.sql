-- V11: Seed 10 active student users with bcrypt-hashed password "password123".

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (student_code, full_name, email, password, role, status)
VALUES
    ('22127001', 'Seed Student 01', '22127001@student.hcmus.edu.vn', crypt('password123', gen_salt('bf', 12)), 'STUDENT', 'ACTIVE'),
    ('22127002', 'Seed Student 02', '22127002@student.hcmus.edu.vn', crypt('password123', gen_salt('bf', 12)), 'STUDENT', 'ACTIVE'),
    ('22127003', 'Seed Student 03', '22127003@student.hcmus.edu.vn', crypt('password123', gen_salt('bf', 12)), 'STUDENT', 'ACTIVE'),
    ('22127004', 'Seed Student 04', '22127004@student.hcmus.edu.vn', crypt('password123', gen_salt('bf', 12)), 'STUDENT', 'ACTIVE'),
    ('22127005', 'Seed Student 05', '22127005@student.hcmus.edu.vn', crypt('password123', gen_salt('bf', 12)), 'STUDENT', 'ACTIVE'),
    ('22127006', 'Seed Student 06', '22127006@student.hcmus.edu.vn', crypt('password123', gen_salt('bf', 12)), 'STUDENT', 'ACTIVE'),
    ('22127007', 'Seed Student 07', '22127007@student.hcmus.edu.vn', crypt('password123', gen_salt('bf', 12)), 'STUDENT', 'ACTIVE'),
    ('22127008', 'Seed Student 08', '22127008@student.hcmus.edu.vn', crypt('password123', gen_salt('bf', 12)), 'STUDENT', 'ACTIVE'),
    ('22127009', 'Seed Student 09', '22127009@student.hcmus.edu.vn', crypt('password123', gen_salt('bf', 12)), 'STUDENT', 'ACTIVE'),
    ('22127010', 'Seed Student 10', '22127010@student.hcmus.edu.vn', crypt('password123', gen_salt('bf', 12)), 'STUDENT', 'ACTIVE')
ON CONFLICT (student_code) DO UPDATE
SET full_name = EXCLUDED.full_name,
    email = EXCLUDED.email,
    password = EXCLUDED.password,
    role = EXCLUDED.role,
    status = EXCLUDED.status;
