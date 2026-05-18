# Đặc tả: Tranh chấp Chỗ Ngồi (Seat Contention) - Hạ tầng

## Phạm vi
Tài liệu này chỉ mô tả **cơ chế hạ tầng** liên quan đến quản lý chỗ ngồi và chống trùng lặp. Dev 1 chịu trách nhiệm cung cấp các công cụ hạ tầng (Redis, rate limiting, seat locking). Tài liệu **không mô tả** luồng HTTP, mã lỗi API, hoặc thao tác lưu bảng `registrations`.

## Mục tiêu
1. **Zero-overbooking:** Không cấp trùng chỗ cuối cùng.
2. **Hiệu năng cao:** Xử lý tải lớn mà không tạo bottleneck.
3. **Công bằng:** Giới hạn spam theo user/IP.
4. **Idempotency:** Chặn thanh toán lặp, đảm bảo kết quả nhất quán.

## Interface Contract (Contract-First)

### SeatLockingService
Đây là hợp đồng giao tiếp duy nhất giữa hạ tầng và nghiệp vụ.

```text
interface SeatLockingService {
  // Reserve a seat. Returns true if success, false if no slot.
  boolean reserveSeat(String workshopId, String userId);

  // Release a previously reserved seat.
  void releaseSeat(String workshopId, String userId);

  // Read-only check (optional, no side effect).
  int getRemainingSlots(String workshopId);
}
```

### IdempotencyService
Quản lý kết quả thanh toán để chống gọi lại Payment Gateway.

```text
interface IdempotencyService {
  // Returns IN_FLIGHT, SUCCESS, FAILED, or NOT_FOUND.
  IdempotencyState getState(String idempotencyKey);

  // Mark a key as in-flight with short TTL.
  void markInFlight(String idempotencyKey, Duration ttl);

  // Store the final result with TTL (24h).
  void storeResult(String idempotencyKey, IdempotencyResult result, Duration ttl);
}
```

## Cơ chế hạ tầng

### 1. Redis Seat Lock (Lua Script)
- Key: `workshop:{workshopId}:slots`. Đồng thời set TTL cho key này = (Thời gian kết thúc đăng ký workshop - Hiện tại) + 24h.
- Hành vi: kiểm tra và giảm chỗ trong một thao tác atomic.

```lua
local count = redis.call('GET', KEYS[1])
if count and tonumber(count) > 0 then
    redis.call('DECR', KEYS[1])
    return 1
else
    return -1
end
```

### 2. Release Seat
- Khi reserveSeat thành công, cần tạo một key phụ dạng `hold:{workshopId}:{userId}` trên Redis (Không set TTL) để đánh dấu chỗ.
- Thiết lập một Background Job (CronJob) định kỳ quét cơ sở dữ liệu để tìm các bản ghi `registrations` ở trạng thái `PENDING` đã vượt quá 10 phút.
- Khi phát hiện bản ghi quá hạn, Job ngầm sẽ chủ động hủy đơn, cập nhật DB và gọi hàm `releaseSeat(workshopId, userId)` để xóa key `hold` đồng thời hoàn lại slot ngay trên Redis. Cơ chế này đảm bảo tính nhất quán tuyệt đối giữa PostgreSQL và Cache.  

### 3. Rate Limiting
- Áp dụng theo `userId` và IP để chặn spam.
- Mục tiêu: đảm bảo công bằng và bảo vệ hạ tầng.

### 4. Idempotency (Redis)
- Key: `payment:{idempotencyKey}`
- State:
  - `IN_FLIGHT` (TTL ngắn, ví dụ 1-5 phút)
  - `SUCCESS` / `FAILED` (TTL 24h)
- Khi cùng `Idempotency-Key` lặp lại:
  - Nếu `IN_FLIGHT` -> trả lại trạng thái đang xử lý.
  - Nếu `SUCCESS`/`FAILED` -> trả kết quả đã lưu.

## Khởi tạo dữ liệu hạ tầng
1. Khi tạo workshop: set `workshop:{workshopId}:slots = total_slots`.
2. Khi thay đổi `total_slots`: cập nhật lại key chỗ ngồi theo chênh lệch.
3. Khi workshop kết thúc: xóa key để tiết kiệm bộ nhớ.

## Kịch bản lỗi (Hạ tầng)

### Lỗi 1: 2 yêu cầu đồng thời khi chỉ còn 1 chỗ
- Lua Script đảm bảo chỉ 1 yêu cầu thành công.

### Lỗi 2: Redis restart
- Nếu key mất, `reserveSeat()` phải trả false (hết chỗ).
- Cần có cơ chế phục hồi dữ liệu (RDB/AOF, replication).

### Lỗi 3: Idempotency key lặp
- Nếu `IN_FLIGHT`: trả trạng thái đang xử lý.
- Nếu `SUCCESS`/`FAILED`: trả kết quả đã lưu.

## Ràng buộc
- **Atomicity:** reserveSeat phải atomic.
- **Hiệu năng:** Lua Script dưới 1ms.
- **Rate limiting:** bắt buộc theo user/IP.
- **Persistence:** Redis bật RDB/AOF hoặc replication.

## Tiêu chí chấp nhận
- [ ] `SeatLockingService.reserveSeat()` trả false khi hết chỗ.
- [ ] Không có overbooking khi 2 request tranh chấp chỗ cuối.
- [ ] `releaseSeat()` hoàn ghế đúng cách.
- [ ] Rate limiting hoạt động theo user/IP.
- [ ] Idempotency trả kết quả cũ, không tạo xử lý mới.
