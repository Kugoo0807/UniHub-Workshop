# Đặc tả: Tranh chấp Chỗ Ngồi (Seat Contention)

## Mô tả
Khi mở đăng ký workshop, có thể có hàng ngàn sinh viên cố gắng đăng ký cùng lúc cho một workshop chỉ có hạn chế chỗ ngồi (ví dụ: 60 chỗ). Hệ thống phải đảm bảo:

1. **Zero-overbooking:** Không bao giờ có hai sinh viên cùng được cấp 1 chỗ ngồi cuối cùng
2. **Hiệu năng cao:** Xử lý hàng ngàn request/giây mà không bị bottleneck tại database
3. **Công bằng:** Tất cả sinh viên có cơ hội bằng nhau (FIFO hoặc random)

Giải pháp: Sử dụng **Redis Lua Script** thực hiện kiểm tra số ghế và giảm số ghế trong 1 atomic operation.

## Luồng chính

### Khởi tạo workshop
1. Ban tổ chức tạo workshop với `total_slots = 60` (ví dụ)
2. Backend lưu thông tin workshop vào PostgreSQL
3. Backend khởi tạo key trong Redis: `workshop:{workshopId}:slots = 60`

### Luồng đăng ký — Kiểm tra & giảm chỗ ngồi
1. Sinh viên bấm "Đăng ký" trên frontend
2. Frontend gửi request POST `/api/registrations/{workshopId}` (kèm JWT token)
3. Backend nhận request, chuẩn bị dữ liệu:
   - `workshopId`
   - `userId` (lấy từ JWT token)
   - `slotKey = "workshop:{workshopId}:slots"` (key trong Redis)
4. Backend gọi **Lua Script** trong Redis với hai lệnh atomic:
   ```lua
   local count = redis.call('GET', KEYS[1])
   if count and tonumber(count) > 0 then
       redis.call('DECR', KEYS[1])
       return 1  -- Thành công, giảm chỗ
   else
       return -1  -- Thất bại, hết chỗ
   end
   ```
5. Nếu Lua Script trả về `1`:
  - Tạo bản ghi trong bảng `registrations` (trạng thái `SUCCESS` hoặc `PENDING` tùy loại workshop)
  - Trả về `HTTP 201 Created` kèm mã QR (hoặc thông tin thanh toán)
6. Nếu Lua Script trả về `-1`:
   - Không tạo bản ghi trong bảng `registrations`
   - Trả về `HTTP 409 Conflict` với thông báo "Hết chỗ"

### Đồng bộ Redis ↔ PostgreSQL
1. Sau mỗi ngày (hoặc sau mỗi sự kiện kết thúc), Backend thực hiện job:
   - Query bảng `registrations` để đếm số lượng bản ghi có trạng thái `SUCCESS` cho mỗi workshop
   - So sánh với giá trị trong Redis
   - Nếu có chênh lệch (do lỗi hoặc data corruption), ghi log warning + cập nhật Redis
2. Khắc phục các trường hợp đặc biệt liên quan payment flow:
  - Với luồng "Charge-then-DECR": nếu payment đã thành công nhưng DECR trả về -1 (hết chỗ), server không ghi `payments` vào DB nhưng phải lưu `Idempotency-Key` trong Redis để tránh trừ tiền lặp lại. Việc refund/compensation phải xử lý bởi quy trình vận hành.
  - Khuyến nghị: thực hiện "Reserve-before-charge" (DECR với TTL) để giảm rủi ro bị trừ tiền mà không có chỗ.
2. Sau khi workshop kết thúc, xóa key workshop từ Redis để tiết kiệm bộ nhớ

## Kịch bản lỗi

### Lỗi 1: 2 request đến cùng lúc khi chỉ còn 1 chỗ
- **Điều kiện:** 2 sinh viên cùng bấm "Đăng ký" khi `slots = 1`
- **Hành vi:**
  - Request 1: Lua Script kiểm tra `slots > 0` ✓, giảm xuống `0`, trả về `1`
  - Request 2: Lua Script kiểm tra `slots > 0` ✗ (slots = 0), trả về `-1`
  - Chỉ request 1 được đăng ký thành công; request 2 nhận lỗi "Hết chỗ"
  - **Bảo đảm zero-overbooking**

### Lỗi 2: Redis bị restart hay crashed
- **Điều kiện:** Redis bị shutdown giữa chừng, mất dữ liệu trong memory
- **Hành vi:**
  - Khi Redis khởi động lại, key `workshop:{workshopId}:slots` không tồn tại
  - Lua Script kiểm tra `count == nil` hoặc `count <= 0`, trả về `-1`
  - Tất cả request sau đó sẽ nhận lỗi "Hết chỗ"
  - **Cách phòng ngừa:** Sử dụng Redis Persistence (RDB hoặc AOF) và Redis replication để backup dữ liệu; có cơ chế khôi phục (`recovery job`) sau restart

### Lỗi 3: PostgreSQL ghi bản ghi nhưng Redis không được cập nhật
- **Điều kiện:** Lua Script thất bại hoặc mất kết nối sau khi kiểm tra nhưng trước khi giảm
- **Hành vi:**
  - Nếu Lua Script return -1, Backend không ghi vào PostgreSQL → không có rủi ro
  - Nếu Lua Script thành công (return 1) nhưng Backend ghi vào PostgreSQL thất bại, Redis đã giảm nhưng PostgreSQL không có bản ghi → **chênh lệch dữ liệu**
  - **Cách phòng ngừa:** Thực hiện bước giảm **trước** khi ghi vào PostgreSQL; nếu ghi vào PostgreSQL thất bại, **rollback** bằng cách tăng Redis lên
  ```lua
  -- Phương án cải tiến
  local count = redis.call('GET', KEYS[1])
  if count and tonumber(count) > 0 then
      redis.call('DECR', KEYS[1])
      return 1
  else
      return -1
  end
  ```
  Backend thực hiện: `redis.eval(script, 1, slotKey)` → nếu return 1, ghi PostgreSQL. Nếu PostgreSQL thất bại, gọi `redis.incr(slotKey)` để rollback.

### Lỗi 4: Request bị timeout sau khi Lua Script thành công
- **Điều kiện:** Network lag hoặc server bị overload, client không nhận được response
- **Hành vi:**
  - Redis đã giảm số ghế thành công
  - PostgreSQL đã tạo bản ghi thành công
  - Client không nhận được response
  - Sinh viên có thể retry (bấm "Đăng ký" lại)
  - **Cách phòng ngừa:** Client phải check xem đã đăng ký chưa trước khi retry, hoặc Backend phải bảo vệ bằng constraint UNIQUE + xử lý conflict

### Lỗi 5: Số lượng chỗ trong Redis và PostgreSQL không khớp
- **Điều kiện:** Do bug, race condition không được xử lý, hoặc data corruption
- **Hành vi:**
  - Backend chạy job định kỳ (ngày 1 lần) để kiểm tra: `SELECT COUNT(*) FROM registrations WHERE workshop_id = ? AND status = 'SUCCESS'`
  - So sánh với `GET workshop:{workshopId}:slots` từ Redis
  - Nếu có chênh lệch, ghi log WARNING và cập nhật Redis theo PostgreSQL
  - **Lý do:** PostgreSQL là source of truth (có persistence tốt hơn), Redis chỉ dùng để kiểm soát tải

### Lỗi 6: Workshop bị update (số ghế thay đổi) giữa quá trình đăng ký
- **Điều kiện:** Ban tổ chức tăng/giảm `total_slots` khi đang có sinh viên đăng ký
- **Hành vi:**
  - Giả sử workshop có 60 chỗ, đã bán được 50, còn 10 chỗ trong Redis
  - Ban tổ chức thay đổi `total_slots = 65` (tăng 5 chỗ)
  - Backend phải cập nhật Redis: `workshop:{workshopId}:slots = 15` (10 + 5)
  - Các request sau đó sẽ hoạt động bình thường với số ghế mới
  - **Kỹ thuật:** Sử dụng job hoặc webhook để cập nhật Redis khi `total_slots` thay đổi

## Ràng buộc

- **Atomicity:** Kiểm tra và giảm chỗ phải là 1 atomic operation → dùng Lua Script
- **Hiệu năng:**
  - Lua Script phải thực thi dưới 1ms
  - Hệ thống phải chịu được ít nhất 12.000 request/10 phút (~ 20 req/s bình quân, 40 req/s ở 3 phút đầu)
  - API không được bottleneck tại database row-level lock
- **Tính nhất quán:**
  - Redis và PostgreSQL phải đồng bộ (trong mục đích thực tế, chênh lệch <= 1% được chấp nhận)
  - Job kiểm tra đồng bộ phải chạy định kỳ (mỗi ngày)
- **Persistence:**
  - Redis phải có cơ chế backup (RDB, AOF, hoặc Replication) để tránh mất dữ liệu khi restart
  - PostgreSQL là source of truth cho số ghế (dữ liệu tối hậu)

## Tiêu chí chấp nhận

- [ ] Lua Script được implement đúng (atomic GET + DECR)
- [ ] Khi chỉ còn 1 chỗ, 2 request đồng thời chỉ 1 cái được duyệt
- [ ] Hệ thống xử lý được 40 request/s mà không có request bị timeout
- [ ] Khi Redis restart, hệ thống tự động phục hồi số ghế từ PostgreSQL
- [ ] Job kiểm tra đồng bộ chạy thành công mỗi ngày
- [ ] Khi `total_slots` thay đổi, số ghế trong Redis được cập nhật
- [ ] PostgreSQL không ghi bản ghi khi Lua Script thất bại
- [ ] Benchmark test: 1000 concurrent request tới workshop có 100 chỗ, chỉ 100 request được duyệt (zero-overbooking)
 - [ ] Payment flow edge cases handled: if payment succeeds but DECR fails, system records idempotency key and provides a clear operational path for refund/compensation (or switch to reserve-before-charge).
 - [ ] Idempotency for payments: same `Idempotency-Key` within 24h returns stored result and does not call Payment Gateway again.
