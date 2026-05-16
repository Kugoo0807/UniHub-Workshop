# UniHub-Workshop 

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![React](https://img.shields.io/badge/React-20232A?style=for-the-badge&logo=react&logoColor=61DAFB)
![Vite](https://img.shields.io/badge/Vite-646CFF?style=for-the-badge&logo=vite&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-38B2AC?style=for-the-badge&logo=tailwind-css&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-005571?style=for-the-badge&logo=fastapi)
![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Supabase](https://img.shields.io/badge/Supabase-3ECF8E?style=for-the-badge&logo=supabase&logoColor=white)
![Cloudinary](https://img.shields.io/badge/Cloudinary-3448C5?style=for-the-badge&logo=cloudinary&logoColor=white)
![Gemini](https://img.shields.io/badge/Google%20Gemini-8E75B2?style=for-the-badge&logo=googlegemini&logoColor=white)


**UniHub-Workshop** là một hệ thống quản lý hội thảo (workshop) toàn diện dành cho môi trường đại học. Hệ thống hỗ trợ từ việc quản lý địa điểm (phòng học/hội trường), lập lịch workshop, đăng ký tham gia, điểm danh bằng mã QR, đến việc tự động tóm tắt nội dung bằng AI.

---

## Tính năng nổi bật (Key Features)

### Quản trị viên (Admin Dashboard)
- **Quản lý Workshop:** Tạo, chỉnh sửa, xuất bản hoặc hủy các workshop. Theo dõi trạng thái thời gian thực.
- **Quản lý Phòng (Room Management):** Quản lý sức chứa và sơ đồ mặt bằng (floor maps) được lưu trữ trên Cloudinary.
- **AI Summary:** Tự động tóm tắt nội dung và trích xuất tên diễn giả từ file PDF giới thiệu workshop bằng mô hình ngôn ngữ lớn (LLM).
- **Thống kê chuyên sâu:** Theo dõi doanh thu, tỷ lệ lấp đầy phòng và xu hướng đăng ký thông qua biểu đồ trực quan (Recharts).
- **Đồng bộ sinh viên:** Tự động đồng bộ danh sách sinh viên từ file CSV trên Supabase Storage.

### Sinh viên (Mobile App & Web)
- **Khám phá Workshop:** Xem danh sách các workshop sắp diễn ra, lọc theo chủ đề hoặc thời gian.
- **Đăng ký & Thanh toán:** Quy trình đăng ký nhanh chóng với tích hợp giả lập cổng thanh toán.
- **Điểm danh thông minh:** Sử dụng mã QR để điểm danh nhanh tại sảnh workshop.
- **Theo dõi lịch trình:** Quản lý danh sách các workshop đã đăng ký và trạng thái thanh toán.

---

## Công nghệ sử dụng (Tech Stack)

### Backend (Core Service)
- **Ngôn ngữ:** Java 25
- **Framework:** Spring Boot 4.0.6
- **Database:** PostgreSQL (với Flyway để quản lý migrations)
- **Caching:** Redis (Lưu trữ session và dữ liệu tạm)
- **Security:** Spring Security & Stateless JWT (JJWT)
- **Documentation:** Swagger/OpenAPI 3
- **Cloud Integration:** Cloudinary (Ảnh), Supabase (CSV Storage/DB)

### AI Microservice
- **Framework:** FastAPI (Python 3.11+)
- **AI Engine:** Gemini / Groq API (Xử lý LLM)
- **PDF Processing:** PyPDF2 / pdfplumber
- **Kiến trúc:** Chạy độc lập trong Docker internal network (Non-blocking async).

### Frontend (Web Admin)
- **Framework:** React 18+ (Vite)
- **Styling:** Tailwind CSS (Modern, Responsive UI)
- **Charts:** Recharts
- **State Management:** React Context API

### Mobile (Student App)
- **Nền tảng:** Native Android (Kotlin)
- **Build tool:** Gradle (Kotlin DSL)

---

## Cấu trúc dự án (Project Structure)

```text
UniHub-Workshop/
├── backend/            # Spring Boot Core API
├── frontend/           # React Admin Dashboard
├── mobile/             # Native Android App (Kotlin)
├── ai-service/         # FastAPI Microservice (AI Processing)
├── blueprint/          # Tài liệu đặc tả & Specs kỹ thuật
│   └── specs/          # Chi tiết nghiệp vụ (Room, AI, Attendance...)
└── docker-compose.yml  # Cấu hình Docker (Redis, DB, ...)
```

---

## Cài đặt và Chạy dự án (Setup)

### 1. Yêu cầu hệ thống
- JDK 25+
- Node.js 18+
- Python 3.11+
- Docker & Docker Compose
- PostgreSQL & Redis (Có thể chạy qua Docker)

### 2. Biến môi trường (Environment Variables)
Tạo file `.env` trong thư mục `backend/` và `ai-service/` dựa trên các file mẫu `.env.example`. Các thông số quan trọng:
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `CLOUDINARY_URL`, `JWT_ACCESS_SECRET`
- `LLM_API_KEY` (Gemini hoặc Groq)
- `SYNC_STUDENTS_URL` (Link file CSV trên Supabase)

### 3. Chạy Backend
```bash
cd backend
./mvnw spring-boot:run
```
*API Swagger sẽ có tại:* `http://localhost:8080/swagger-ui/index.html`

### 4. Chạy Frontend
```bash
cd frontend
npm install
npm run dev
```

### 5. Chạy AI Service
```bash
cd ai-service
pip install -r requirements.txt
python main.py
```

---

##  Kiến trúc hệ thống (System Architecture)

Hệ thống được thiết kế theo hướng **Microservices-lite**:
- **Core Backend** xử lý các nghiệp vụ chính, bảo mật và lưu trữ dữ liệu.
- **AI Service** chạy tách biệt. Khi Admin upload PDF, Backend sẽ gửi request bất đồng bộ (@Async) sang AI Service. Điều này đảm bảo hệ thống lõi không bị treo khi xử lý các tệp tin nặng.
- **Redis** được sử dụng để tối ưu hóa tốc độ truy xuất và quản lý rate limit.

--

---
*© 2026 UniHub Team - Advanced Agentic Coding Project.*
