# Đặc tả: Workshop Attendance (Danh sách điểm danh)

## Mô tả

Admin có thể xem danh sách tất cả sinh viên **đã đăng ký thành công (SUCCESS)** một workshop cụ thể, kèm theo thông tin sinh viên đó đã **check-in** hay chưa. Tính năng này phục vụ mục đích:

- Theo dõi ai đăng ký nhưng không đến tham dự.
- Đối soát dữ liệu check-in sau buổi workshop.

Tính năng được truy cập từ nút **"View Attendances"** trong dropdown Actions của từng workshop trên trang Admin Dashboard (`/admin`).

---

## Luồng chính

1. Admin nhấn nút **"⋮" (Actions)** trên một dòng workshop → chọn **"View Attendances"**.
2. Frontend gọi `GET /api/v1/admin/workshops/{id}/attendances?page=0&size=5` với JWT role `ADMIN`.
3. Backend thực hiện:
   - Kiểm tra workshop tồn tại (nếu không → `404`).
   - Query `registrations JOIN users LEFT JOIN checkin_records` với điều kiện `status = 'SUCCESS'` và `workshop_id = {id}`.
   - Sắp xếp: **đã check-in trước**, sau đó theo thứ tự `created_at` tăng dần.
   - Wrap kết quả vào `PageResponse`.
4. Trả về `200 OK` + `PageResponse`.
5. Frontend hiển thị modal `AttendanceModal` với:
   - **3 summary cards**: Total Registered / Checked In (page) / Page X/Y.
   - **Bảng danh sách** 5 hàng/trang, màu nền xanh lá cho sinh viên đã check-in.
   - **Filter tìm kiếm** theo tên, mã sinh viên, email (client-side, trong page hiện tại).
   - **Phân trang** dùng component `PaginationControl` (shared).

---

## API Endpoint

| Method | Endpoint | Role | Mô tả |
|--------|----------|------|-------|
| `GET` | `/api/v1/admin/workshops/{id}/attendances` | ADMIN | Lấy danh sách điểm danh phân trang (chỉ SUCCESS) |

---

## Request / Response Schema

### GET `/api/v1/admin/workshops/{id}/attendances`

**Path Parameter:**

| Param | Type | Mô tả |
|-------|------|-------|
| `id`  | Long | ID của workshop |

**Query Parameters:**

| Param  | Type    | Default | Mô tả |
|--------|---------|---------|-------|
| `page` | Integer | `0`     | Trang hiện tại (0-indexed) |
| `size` | Integer | `5`     | Số bản ghi mỗi trang |

**Response `200 OK`:**
```json
{
  "content": [
    {
      "registrationId": 42,
      "userId": 7,
      "studentCode": "SV007",
      "fullName": "Tran Van B",
      "email": "tvb@example.com",
      "phoneNumber": "0901111111",
      "registrationStatus": "SUCCESS",
      "registeredAt": "2026-05-14T09:00:00",
      "checkedIn": true,
      "checkedInAt": "2026-05-20T09:15:00"
    },
    {
      "registrationId": 43,
      "userId": 8,
      "studentCode": "SV008",
      "fullName": "Le Thi C",
      "email": "ltc@example.com",
      "phoneNumber": "0902222222",
      "registrationStatus": "SUCCESS",
      "registeredAt": "2026-05-14T10:00:00",
      "checkedIn": false,
      "checkedInAt": null
    }
  ],
  "page": 0,
  "size": 5,
  "totalElements": 18,
  "totalPages": 4,
  "last": false
}
```

**Thứ tự trả về (trong mỗi page):**
1. Sinh viên đã check-in (`checkedIn = true`) — hiển thị trước.
2. Sinh viên chưa check-in (`checkedIn = false`) — sau đó.
3. Trong mỗi nhóm, sắp xếp theo `registeredAt` tăng dần.

---

## Kịch bản lỗi

| Tình huống | HTTP | Xử lý |
|------------|------|-------|
| Không có JWT hoặc không phải ADMIN | `401` / `403` | Spring Security chặn |
| Workshop `id` không tồn tại | `404` | Service throw `ResourceNotFoundException` |
| Workshop tồn tại nhưng chưa có đăng ký SUCCESS nào | `200` | Trả `PageResponse` với `content = []`, `totalElements = 0` |

---

## Thiết kế kỹ thuật

### Backend

**Controller:** `AdminWorkshopController` — `GET /api/v1/admin/workshops/{id}/attendances`
- `@RequestParam(defaultValue = "0") int page`
- `@RequestParam(defaultValue = "5") int size`
- Trả `PageResponse<WorkshopAttendanceResponse>`

**Service:** `WorkshopService#getWorkshopAttendances(Long workshopId, int page, int size)`
- Gọi `findWorkshopOrThrow(id)` → ném `ResourceNotFoundException` nếu không có.
- Tạo `PageRequest.of(page, size)` → truyền vào `RegistrationRepository`.
- Dùng `Page.map(this::toAttendanceResponse)` để map không cần stream thủ công.
- Wrap vào `PageResponse<>` builder.

**Repository:** `RegistrationRepository#findAttendancesByWorkshopId(Long workshopId, Pageable pageable)`
```sql
-- JPQL tương đương
SELECT r
FROM Registration r
JOIN FETCH r.user u
LEFT JOIN FETCH r.checkinRecord cr
WHERE r.workshop.id = :workshopId
  AND r.status = 'SUCCESS'
ORDER BY (CASE WHEN cr IS NOT NULL THEN 1 ELSE 0 END) DESC,
         r.createdAt ASC
```
> Dùng `LEFT JOIN FETCH` để tránh N+1. Trả `Page<Registration>`.

**Entity thay đổi:** `Registration.java` — thêm inverse side để JPQL `LEFT JOIN FETCH r.checkinRecord` hoạt động:
```java
@OneToOne(mappedBy = "registration", fetch = FetchType.LAZY)
private CheckinRecord checkinRecord;
```

**DTO:** `WorkshopAttendanceResponse`

| Field | Type | Mô tả |
|-------|------|-------|
| `registrationId` | `Long` | ID bản ghi đăng ký |
| `userId` | `Long` | ID sinh viên |
| `studentCode` | `String` | Mã sinh viên |
| `fullName` | `String` | Họ và tên |
| `email` | `String` | Email |
| `phoneNumber` | `String` | Số điện thoại |
| `registrationStatus` | `String` | Luôn là `SUCCESS` |
| `registeredAt` | `LocalDateTime` | Thời điểm đăng ký |
| `checkedIn` | `Boolean` | `true` nếu đã quét QR |
| `checkedInAt` | `LocalDateTime` | Thời điểm quét QR; `null` nếu chưa check-in |

### Frontend

**Trigger:** Nút **"View Attendances"** — item đầu tiên trong dropdown Actions trên `Dashboard.jsx`.

**Component:** `src/components/workshops/AttendanceModal.jsx`
- Modal full-overlay, đóng bằng `Escape` hoặc click backdrop.
- **Header:** Gradient `indigo-600 → violet-600`.
- **Summary Cards:** Total Registered / Checked In (page) / Page X/Y.
- **Search:** filter client-side theo tên, mã SV, email trong page hiện tại.
- **Table:** 6 cột — `#` / Student / Contact / Registered At / Check-in / Checked-in At.
  - Không có cột Reg. Status (chỉ hiện SUCCESS nên không cần).
  - Số thứ tự toàn cục: `page * pageSize + idx + 1`.
  - Hàng đã check-in: nền `emerald-50`.
- **Pagination:** dùng component dùng chung `PaginationControl` (props: `currentPage`, `totalPages`, `totalElements`, `pageSize`, `onPageChange`, `itemLabel="attendees"`).

**Service:** `adminWorkshopService.getAttendances(id, page=0, size=5)` → `GET /api/v1/admin/workshops/{id}/attendances?page={page}&size={size}`

---

## Tiêu chí chấp nhận

### Unit Tests (Service Layer)

| Test ID | Scenario | Expected |
|---------|----------|----------|
| `WA-UT-01` | Workshop `id` không tồn tại | Throw `ResourceNotFoundException`; `findAttendancesByWorkshopId` không được gọi |
| `WA-UT-02` | Workshop tồn tại, không có đăng ký SUCCESS | `PageResponse.content = []`, `totalElements = 0` |
| `WA-UT-03` | Đăng ký SUCCESS, chưa check-in | `checkedIn = false`, `checkedInAt = null` |
| `WA-UT-04` | Đăng ký SUCCESS, đã check-in | `checkedIn = true`, `checkedInAt` = đúng timestamp quét QR |
| `WA-UT-05` | Danh sách hỗn hợp (có / không check-in) | Trả đủ tất cả records trong page |
| `WA-UT-06` | Mapping DTO đầy đủ | Tất cả 10 fields đúng với entity |
| `WA-UT-07` | Delegation xuống Repository | `findAttendancesByWorkshopId(workshopId, pageable)` gọi đúng 1 lần với đúng `workshopId` |
| `WA-UT-08` | PageResponse meta-fields | `page`, `size`, `totalElements`, `totalPages`, `last` đúng; `Pageable` truyền xuống đúng `pageNumber` và `pageSize` |

### Integration Tests (Controller Layer)

| Test ID | Endpoint | Scenario | Status |
|---------|----------|----------|--------|
| `WA-IT-01` | `GET /api/v1/admin/workshops/{id}/attendances` | Admin JWT, workshop có đăng ký SUCCESS | `200` + `PageResponse` |
| `WA-IT-02` | `GET /api/v1/admin/workshops/{id}/attendances` | Admin JWT, workshop không có đăng ký SUCCESS | `200` + `content = []` |
| `WA-IT-03` | `GET /api/v1/admin/workshops/{id}/attendances` | Admin JWT, `id` không tồn tại | `404` |
| `WA-IT-04` | `GET /api/v1/admin/workshops/{id}/attendances` | Student JWT | `403` |
| `WA-IT-05` | `GET /api/v1/admin/workshops/{id}/attendances` | Không có JWT | `401` |
| `WA-IT-06` | `GET /api/v1/admin/workshops/{id}/attendances?page=1&size=5` | Admin JWT, trang 2 | `200` + `page = 1`, `last` đúng |

---

## Dependencies & Blocking

| Dependency | Loại | Ghi chú |
|------------|------|---------|
| Feature: Workshop Management | **Blocking** | Cần bảng `workshops` |
| Feature: Registration | **Blocking** | Cần bảng `registrations` với `status = 'SUCCESS'` |
| Feature: Check-in (Mobile App) | **Blocking** | Cần bảng `checkin_records` có dữ liệu |
| Feature: Auth | **Blocking** | Cần JWT + RBAC |
