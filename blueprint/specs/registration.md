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

### 3) Xem lịch sử đăng ký workshop
1. Sinh viên gửi `GET /api/v1/workshops/my-workshops` với JWT hợp lệ và role `STUDENT`.
2. Backend join giữa `registrations` và `workshops` để lấy thêm `title`, `price`, `start_time`, `end_time` cho từng lượt đăng ký.
3. Backend trả danh sách theo `created_at` giảm dần.
4. `qr_code` chỉ trả về khi `status = 'SUCCESS'`.
5. `payment_idempotency_key` chỉ cần thiết cho các lượt `PENDING` để cho phép thanh toán lại từ UI.
6. Frontend phân nhóm hiển thị:
   - Nhóm 1: `PENDING` và workshop chưa kết thúc (`end_time > now`).
   - Nhóm 2: `SUCCESS`, `FAILED`, `CANCELLED`, hoặc workshop đã kết thúc.
7. Trong nhóm 2, các lượt `FAILED` / `CANCELLED` / đã kết thúc có thể làm mờ UI.
8. Với lượt `SUCCESS`, UI hiển thị rõ mã QR check-in nhưng vẫn đặt dưới nhóm `PENDING` để sinh viên ưu tiên xử lý giao dịch chưa hoàn tất.
9. Với lượt `PENDING` còn trong thời gian chờ, UI cho phép `Thanh toán` lại bằng `payment_idempotency_key` và `Hủy vé` để giải phóng chỗ ngay.

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

### 401 / 403 khi xem lịch sử đăng ký
- Không có JWT hợp lệ hoặc role không phải `STUDENT`.
- Spring Security hoặc `@PreAuthorize` chặn request trước khi vào service.

### Xử lý Timeout Thanh Toán (Background Job)
- Thời gian giữ chỗ mặc định: 10 phút.
- Nếu quá thời gian này mà chưa nhận được kết quả thanh toán thành công, Background Job sẽ tự động: cập nhật registrations từ PENDING sang CANCELLED và gọi SeatLockingService.releaseSeat(workshopId, userId) để hoàn ghế lại cho hệ thống.

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
- [ ] Xem lịch sử đăng ký trả đúng `start_time`, `end_time`, `created_at`, và chỉ trả `qr_code` khi `SUCCESS`.
- [ ] UI phân nhóm `PENDING` lên trên, `SUCCESS` vẫn hiện QR ở nhóm lịch sử, và các lượt đã kết thúc/thất bại được làm mờ.
