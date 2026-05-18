-- V2: Seed user & workshop data
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (student_code, full_name, email, password, role) VALUES
    (NULL, 'Admin System', 'admin@unihub.edu.vn', crypt('password123', gen_salt('bf', 12)), 'ADMIN'),
    ('21127001', 'Nguyen Van A', '21127001@student.unihub.edu.vn', crypt('password123', gen_salt('bf', 12)), 'STUDENT'),
    (NULL, 'Staff Check-in', 'staff@unihub.edu.vn', crypt('password123', gen_salt('bf', 12)), 'STAFF');

INSERT INTO workshops (title, description, total_slots, remaining_slots, price, start_time, end_time) VALUES
    ('Workshop: Kỹ năng viết CV', 'Hướng dẫn viết CV chinh phục nhà tuyển dụng IT.', 60, 60, 0, '2026-04-20 08:00:00', '2026-05-15 11:00:00'),
    ('Workshop: Cloud Computing AWS', 'Thực hành deploy ứng dụng lên AWS.', 30, 30, 50000, '2026-04-20 13:30:00', '2026-05-16 17:00:00');
