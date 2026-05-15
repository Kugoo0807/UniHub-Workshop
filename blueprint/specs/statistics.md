# Đặc tả: Admin Statistics (Trang thống kê tổng quan)

## Mô tả

Admin có thể xem trang thống kê tổng quan hệ thống tại `/admin/statistics`. Trang này tổng hợp dữ liệu từ các bảng `workshops`, `registrations`, `payments`, `rooms` và hiển thị dưới dạng biểu đồ trực quan.

Trang được truy cập từ nút **"📊 View Analytics"** trên Admin Dashboard (`/admin`).

---

## Luồng chính

1. Admin nhấn nút **"View Analytics"** trên Dashboard → điều hướng đến `/admin/statistics`.
2. Frontend gọi `GET /api/v1/admin/stats` với JWT role `ADMIN`.
3. Backend tổng hợp dữ liệu từ DB và trả về `GlobalStatsResponse`.
4. Frontend render các biểu đồ (recharts).

---

## API Endpoint

| Method | Endpoint | Role | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/admin/stats` | ADMIN | Lấy toàn bộ số liệu thống kê tổng quan |

---

## Response Schema

### GET `/api/v1/admin/stats`

**Response `200 OK`:**
```json
{
  "totalRevenue": 5000000,
  "totalWorkshops": 12,
  "totalRegistrations": 320,
  "paymentSuccessRate": 87.5,
  "paymentFailureRate": 12.5,
  "actualParticipationRate": 76.3,
  "cancellationRate": 8.1,
  "topSpeakers": [
    { "speaker": "Nguyen Van A", "workshopCount": 4 },
    { "speaker": "Tran Thi B",   "workshopCount": 2 }
  ],
  "roomUtilization": [
    { "roomName": "A101", "capacity": 60, "workshopCount": 5, "avgFillRate": 82.0 }
  ],
  "registrationsByHour": [
    { "hour": 9,  "count": 45 },
    { "hour": 10, "count": 78 },
    { "hour": 14, "count": 32 }
  ],
  "workshopFillRates": [
    { "workshopId": 1, "title": "Clean Code với Java", "totalSlots": 60, "registered": 54, "fillRate": 90.0 }
  ]
}
```

---

## Các chỉ số thống kê

### KPI Cards (tổng quan nhanh)

| Chỉ số | Nguồn dữ liệu | Mô tả |
|---|---|---|
| Tổng số Workshop | `COUNT(workshops)` | Tất cả trạng thái |
| Đăng ký thành công | `COUNT(registrations WHERE status='SUCCESS')` | Tổng toàn hệ thống |
| Doanh thu thực tế | `SUM(payments.amount WHERE status='COMPLETED')` | Đơn vị: VNĐ |
| Tỉ lệ tham gia | `SUCCESS / COUNT(registrations) * 100` | Tính trên tổng đăng ký |

### Biểu đồ chi tiết

| Biểu đồ | Loại | Mô tả |
|---|---|---|
| Tỉ lệ thanh toán | PieChart (donut) | COMPLETED / FAILED / khác — chỉ tính workshop có phí (`price > 0`) |
| Tỉ lệ tham gia & hủy | PieChart (donut) | SUCCESS / CANCELLED / khác trên tổng registrations |
| Khung giờ đăng ký | AreaChart | Group `registrations.created_at` theo giờ trong ngày (0–23) |
| Fill rate top 10 workshop | BarChart (horizontal) | Màu xanh < 60%, cam 60–89%, đỏ ≥ 90% |
| Diễn giả | RadarChart + bảng xếp hạng | Top 6 speaker cho radar; top 5 hiển thị bảng xếp hạng |
| Hiệu suất phòng | BarChart (vertical) | Avg fill rate theo phòng — chỉ PUBLISHED/COMPLETED/CANCELLED |
| Phân bổ chỗ đăng ký | BarChart (stacked, horizontal) | Đã đăng ký vs. còn trống — tất cả workshop |

---

## Công thức tính

```
paymentSuccessRate  = countCompleted(paid) / totalAttempts(paid) * 100
paymentFailureRate  = countFailed(paid)    / totalAttempts(paid) * 100
  (totalAttempts = COMPLETED + FAILED, chỉ workshop price > 0)

actualParticipationRate = countByStatus('SUCCESS') / count(registrations) * 100
cancellationRate        = countByStatus('CANCELLED') / count(registrations) * 100

fillRate (per workshop)  = registered / totalSlots * 100
avgFillRate (per room)   = avg(fillRate) của các workshop trong phòng đó
```

---

## Kịch bản lỗi

| Tình huống | HTTP | Xử lý |
|---|---|---|
| Không có JWT hoặc không phải ADMIN | `401` / `403` | Spring Security chặn |
| Chưa có dữ liệu (DB rỗng) | `200` | Trả về 0 / mảng rỗng — frontend hiển thị trạng thái empty |

---

## Thiết kế kỹ thuật

### Backend

**Controller:** `AdminStatsController` — `GET /api/v1/admin/stats`

**Service:** `GlobalStatsService`
- Gọi `PaymentRepository`: `sumRevenue()`, `countCompletedForPaidWorkshops()`, `countFailedForPaidWorkshops()`, `countTotalAttemptsForPaidWorkshops()`
- Gọi `RegistrationRepository`: `countByStatus()`, `countByHourOfDay()`, `countWorkshopsBySpeaker()`, `roomUtilizationStats()`, `workshopFillRates()`
- Gọi `WorkshopRepository`: `count()`

**DTO:** `GlobalStatsResponse` với các inner class:
- `SpeakerStat { speaker, workshopCount }`
- `RoomUtilization { roomName, capacity, workshopCount, avgFillRate }`
- `HourlyRegistration { hour, count }`
- `WorkshopFillRate { workshopId, title, totalSlots, registered, fillRate }`

**Queries dùng JPQL** — không dùng native SQL để giữ portability.

### Frontend

**Route:** `/admin/statistics` (Admin-only, `ProtectedRoute`)

**Page:** `StatisticsPage.jsx`

**Thư viện biểu đồ:** `recharts` (đã cài: `npm install recharts`)

**Components sử dụng:**
- `BarChart`, `Bar`, `Cell` — fill rate và phòng
- `PieChart`, `Pie` — tỉ lệ thanh toán, tham gia
- `AreaChart`, `Area` — đăng ký theo giờ
- `RadarChart`, `Radar` — speaker
- `ResponsiveContainer` — responsive layout

---

## Tiêu chí chấp nhận

| Test ID | Scenario | Expected |
|---|---|---|
| `STAT-IT-01` | `GET /api/v1/admin/stats` với Admin JWT | `200` + đủ các fields |
| `STAT-IT-02` | `GET /api/v1/admin/stats` với Student JWT | `403` |
| `STAT-IT-03` | `GET /api/v1/admin/stats` không có JWT | `401` |
| `STAT-IT-04` | DB rỗng (không có workshop/registration) | `200` + tất cả số = 0, mảng = [] |
| `STAT-UT-01` | `paymentSuccessRate` khi không có paid workshop | Trả `0.0` (không chia 0) |
| `STAT-UT-02` | `fillRate` khi `totalSlots = 0` | Trả `0.0` (không chia 0) |
| `STAT-UT-03` | `totalRevenue` khi không có COMPLETED payment | Trả `0` |

---

## Dependencies & Blocking

| Dependency | Loại | Ghi chú |
|---|---|---|
| Feature: Workshop Management | **Blocking** | Cần bảng `workshops` và dữ liệu |
| Feature: Registration | **Blocking** | Cần bảng `registrations` |
| Feature: Payment | **Blocking** | Cần bảng `payments` cho revenue |
| Feature: Auth | **Blocking** | Cần JWT + RBAC |
| `recharts` npm package | **Blocking** | Đã cài — `npm install recharts` |
