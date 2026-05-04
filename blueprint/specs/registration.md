# Đặc tả: Đăng ký Workshop - Nghiệp vụ/API

## Phạm vi
Tài liệu này chỉ mô tả **luồng API của sinh viên**, xử lý Controller/Service, lưu PostgreSQL, và gọi Mock Payment Gateway. Tài liệu **không mô tả** Redis, Lua Script, hay cơ chế atomic. Việc quản lý chỗ ngồi được gọi qua hợp đồng `SeatLockingService`.

## Luồng chính

### 1) Đăng ký workshop miễn phí
1. Sinh viên truy cập ứng dụng. Nếu chưa có JWT, redirect về Login.
2. Sau khi xác thực, sinh viên xem danh sách workshop `PUBLISHED`.
3. Bấm "Đăng ký" ở workshop miễn phí.
4. Backend kiểm tra:
   - JWT hợp lệ.
   - Rate limit đã được áp dụng (nếu vượt, trả `429`).
   - Workshop `PUBLISHED`.
   - Trong thời gian đăng ký (`registration_start_time` → `registration_end_time`).
   - Sinh viên chưa đăng ký workshop này (UNIQUE `(user_id, workshop_id)`).
5. Backend gọi `SeatLockingService.reserveSeat(workshopId, userId)`.
   - Nếu trả `false` -> throw exception hết chỗ, trả `HTTP 409`.
6. Nếu thành công:
   - Tạo bản ghi `registrations` trạng thái `SUCCESS`.
   - Phát sự kiện thông báo (email, Telegram, ...).
   - Trả `HTTP 201` + mã QR.

### 2) Đăng ký workshop có phí
1. Sinh viên truy cập ứng dụng. Nếu chưa có JWT, redirect về Login.
2. Sau khi xác thực, sinh viên xem danh sách workshop `PUBLISHED`.
3. Bấm "Đăng ký" ở workshop có phí.
4. Backend kiểm tra:
   - JWT hợp lệ.
   - Rate limit đã được áp dụng (nếu vượt, trả `429`).
   - Workshop `PUBLISHED`.
   - Trong thời gian đăng ký.
   - Sinh viên chưa đăng ký workshop này.
5. Backend gọi `SeatLockingService.reserveSeat(workshopId, userId)`.
   - Nếu trả `false` -> throw exception hết chỗ, trả `HTTP 409`.
6. Nếu thành công:
   - Tạo bản ghi `registrations` trạng thái `PENDING`.
   - Trả thông tin thanh toán (số tiền, mã đơn).
7. Frontend gửi yêu cầu thanh toán kèm `Idempotency-Key`.
8. Backend xử lý thanh toán:
   - Nếu `Idempotency-Key` đang `IN_FLIGHT` -> trả `HTTP 202`.
   - Nếu đã có kết quả -> trả lại kết quả cũ, không gọi Payment Gateway.
   - Nếu chưa có -> đánh dấu `IN_FLIGHT`, gọi Payment Gateway (timeout 30s, Circuit Breaker).
9. Kết quả thanh toán:
   - Thành công: lưu `payments`, cập nhật `registrations` thành `SUCCESS`, trả QR.
   - Thất bại (card declined, insufficient funds): cập nhật `registrations` thành `FAILED`, gọi `SeatLockingService.releaseSeat(workshopId, userId)`, trả `HTTP 402`.
   - Timeout hoặc circuit open: giữ `registrations` ở `PENDING`, trả `HTTP 503`.

## Kịch bản lỗi (HTTP)

### 401 Unauthorized
- JWT hết hạn hoặc không hợp lệ.
- Hệ thống trả `HTTP 401`.

### 402 Payment Required
- Payment Gateway trả lỗi thanh toán.
- `registrations` -> `FAILED`.
- Gọi `releaseSeat()` để hoàn ghế.

### 409 Conflict
- Workshop hết chỗ (reserveSeat trả `false`).
- Sinh viên đã đăng ký (UNIQUE conflict).
- Workshop không `PUBLISHED` hoặc ngoài thời gian đăng ký.

### 503 Service Unavailable
- Circuit Breaker mở hoặc Payment Gateway timeout.
- `registrations` giữ `PENDING` để client retry với cùng `Idempotency-Key`.

## Ràng buộc
- **Hiệu năng:** API đăng ký dưới 200ms trong điều kiện bình thường.
- **Tính nhất quán dữ liệu:**
  - `registrations` có UNIQUE `(user_id, workshop_id)` (Flyway migration).
  - Nếu Payment thất bại phải gọi `releaseSeat()`.
  - Idempotency trả kết quả cũ trong 24h.
- **Bảo mật:** chỉ sinh viên đã xác thực mới được đăng ký.

## Tiêu chí chấp nhận
- [ ] Đăng ký miễn phí trả QR ngay.
- [ ] Đăng ký có phí tạo `PENDING` và gọi Payment Gateway.
- [ ] `reserveSeat()` false -> trả `HTTP 409`.
- [ ] Payment thất bại -> `FAILED` + gọi `releaseSeat()` + trả `HTTP 402`.
- [ ] Payment timeout/circuit open -> trả `HTTP 503`, giữ `PENDING`.
- [ ] Gửi lại cùng `Idempotency-Key` -> trả kết quả cũ, không gọi Payment Gateway.
- [ ] Không cho đăng ký 2 lần cùng workshop.
