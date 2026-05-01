# Đặc tả: Đăng ký Workshop

## Mô tả
Sinh viên có thể xem danh sách workshop, chọn workshop cần đăng ký. Tùy vào loại workshop (miễn phí hoặc có phí), hệ thống sẽ hướng người dùng đến luồng thanh toán hoặc xác nhận trực tiếp. Sau khi đăng ký thành công, sinh viên nhận mã QR dùng để check-in tại sự kiện.

## Luồng chính

### Luồng đăng ký workshop miễn phí
1. Sinh viên xem danh sách workshop trên trang chủ (yêu cầu xác thực)
2. Bấm nút "Đăng ký" trên workshop miễn phí
3. Backend kiểm tra:
   - Sinh viên đã xác thực chưa? (kiểm tra JWT token)
   - Workshop còn chỗ không? (kiểm tra Redis Lua Script)
   - Sinh viên đã đăng ký workshop này rồi chưa? (kiểm tra bảng `registrations`)
4. Nếu tất cả hợp lệ:
   - Lua Script giảm số ghế còn lại trong Redis
   - Tạo bản ghi mới trong bảng `registrations` với trạng thái `SUCCESS`
   - Sinh viên phát sự kiện thông báo (email, Telegram, ...)
   - Trả về mã QR cho sinh viên
5. Frontend hiển thị thông báo thành công và mã QR

### Luồng đăng ký workshop có phí
1. Sinh viên xem danh sách workshop trên trang chủ (yêu cầu xác thực)
2. Bấy nút "Đăng ký" trên workshop có phí
3. Backend kiểm tra:
   - Sinh viên đã xác thực chưa? (kiểm tra JWT token)
   - Workshop còn chỗ không? (kiểm tra Redis Lua Script)
   - Sinh viên đã đăng ký workshop này rồi chưa? (kiểm tra bảng `registrations`)
4. Nếu tất cả hợp lệ:
   - **Chưa giảm số ghế tại bước này** — tạm thời giữ nguyên
   - Tạo bản ghi mới trong bảng `registrations` với trạng thái `PENDING`
   - Trả về thông tin thanh toán (số tiền, mã đơn) cho frontend
5. Frontend hiển thị form thanh toán, sinh viên nhập thông tin thanh toán
6. Backend (hoặc frontend, tùy vào kiến trúc) gửi yêu cầu thanh toán đến Mock Payment Gateway kèm theo `Idempotency-Key`
7. Thanh toán — luồng chi tiết (hiện thực và lưu ý):
  - Backend kiểm tra `Idempotency-Key` trong Redis (TTL 24h). Nếu key đã tồn tại, trực tiếp trả về kết quả đã lưu, **không gọi lại Payment Gateway**.
  - Backend gọi Payment Gateway với timeout 30s và Circuit Breaker (Resilience4j). Nếu Circuit Breaker mở hoặc timeout thì trả `HTTP 503 Service Unavailable` và **giữ `registrations` ở trạng thái `PENDING`** để client retry sau.
  - Nếu Payment Gateway trả thành công (transaction id):
     - **Hiện thực (quan sát):** hệ thống sẽ gọi Lua Script trong Redis để giảm số ghế (atomic DECR). Nếu DECR thành công → ghi `payments` và cập nhật `registrations` thành `SUCCESS`, trả mã QR.
     - **Nếu DECR thất bại (hết chỗ):** theo hiện tại hệ thống sẽ **không** ghi `payments` vào PostgreSQL nhưng sẽ lưu `Idempotency-Key` trong Redis (ví dụ `no_seat:{transactionId}`) để ngăn việc trừ tiền lặp lại khi client retry với cùng key; server trả lỗi `HTTP 409 Conflict` với thông báo "Workshop này đã hết chỗ". Lưu ý: trong kịch bản này, khách hàng có thể đã bị trừ tiền bởi gateway — cần xử lý bù trừ/refund ngoài luồng này hoặc chọn giải pháp khác (xem phần Recommended).
  - Nếu Payment Gateway trả lỗi (card declined, insufficient funds): cập nhật `registrations` thành `FAILED`, trả `HTTP 402 Payment Required`, **không giảm số ghế**.

8. Ghi nhớ và khuyến nghị vận hành:
  - Hiện có hai chiến lược khả thi:
     1. Reserve-before-charge (khuyến nghị): thực hiện DECR (ghi reservation với TTL ngắn) trước khi charge; nếu charge thành công, convert reservation → SUCCESS; nếu charge thất bại hoặc timeout, release reservation.
     2. Charge-then-DECR (hiện đang triển khai): charge trước, sau đó gọi DECR; nếu DECR thất bại cần có quy trình refund/compensation.
  - Quyết định chiến lược phải ghi rõ trong spec. Hiện code mẫu áp dụng "Charge-then-DECR" với cơ chế lưu `Idempotency-Key` để tránh trừ tiền trùng lặp; tuy nhiên refund/compensation không được tự động xử lý trong code và cần vận hành hỗ trợ.

## Kịch bản lỗi

### Lỗi 1: Hết chỗ (Redis trả về -1)
- **Điều kiện:** Lua Script trong Redis kiểm tra số ghế còn lại và thấy <= 0
- **Hành vi:**
  - Backend nhận kết quả -1 từ Redis
  - Trả về lỗi `HTTP 409 Conflict` với thông báo "Workshop này đã hết chỗ"
  - Không tạo bản ghi trong bảng `registrations`
  - Frontend hiển thị thông báo cho sinh viên biết hết chỗ

### Lỗi 2: Sinh viên đã đăng ký
- **Điều kiện:** Bảng `registrations` đã có bản ghi với `(user_id, workshop_id)` giống nhau
- **Hành vi:**
  - Backend kiểm tra constraint `UNIQUE(user_id, workshop_id)` hoặc query trước
  - Trả về lỗi `HTTP 409 Conflict` với thông báo "Bạn đã đăng ký workshop này rồi"
  - Frontend hiển thị thông báo

### Lỗi 3: Token không hợp lệ
- **Điều kiện:** JWT token hết hạn, không có token, hoặc token bị giả mạo
- **Hành vi:**
  - Backend trả về `HTTP 401 Unauthorized`
  - Frontend chuyển hướng đến trang login

### Lỗi 4: Thanh toán timeout (chỉ riêng cho workshop có phí)
- **Điều kiện:** Mock Payment Gateway không phản hồi trong vòng 30 giây
- **Hành vi:**
  - Spring Boot kích hoạt Circuit Breaker (đã set ngưỡng lỗi)
  - Trả về `HTTP 503 Service Unavailable` với thông báo "Dịch vụ thanh toán tạm thời không khả dụng"
  - Bản ghi `registrations` vẫn ở trạng thái `PENDING`
  - Sinh viên có thể retry sau (sử dụng idempotency key)

### Lỗi 5: Thanh toán thất bại (chỉ riêng cho workshop có phí)
- **Điều kiện:** Mock Payment Gateway trả về mã lỗi (ví dụ: insufficient balance, card declined)
- **Hành vi:**
  - Cập nhật bản ghi `registrations` thành trạng thái `FAILED`
  - Không tạo bản ghi trong bảng `payments`
  - Trả về `HTTP 402 Payment Required` với thông báo chi tiết lỗi
  - Sinh viên có thể thử lại với thẻ khác

### Lỗi 6: Race condition — 2 request cùng lúc
- **Điều kiện:** 2 sinh viên cùng bấm "Đăng ký" trên workshop chỉ còn 1 chỗ
- **Hành vi:**
  - Lua Script trong Redis xử lý atomic — request thứ nhất thành công (Redis trả về 0), request thứ hai thất bại (Redis trả về -1)
  - Chỉ 1 sinh viên được đăng ký thành công; sinh viên còn lại nhận lỗi "Hết chỗ"
  - Không có sinh viên nào bị "overbooking"

### Lỗi 7: Trừ tiền 2 lần (chỉ riêng cho workshop có phí)
- **Điều kiện:** Sinh viên bấm "Thanh toán" 2 lần do mạng lag
- **Hành vi:**
  - Frontend phải sinh ra `Idempotency-Key` (UUID) duy nhất cho mỗi lần bấm "Thanh toán"
  - Request thứ nhất: Backend kiểm tra `Idempotency-Key` chưa có trong Redis, xử lý thanh toán, lưu key + kết quả vào Redis (TTL 24h) + PostgreSQL
  - Request thứ hai: Backend kiểm tra `Idempotency-Key` đã tồn tại trong Redis, trực tiếp trả về kết quả đã lưu, **không gọi lại Payment Gateway**
  - Chỉ 1 giao dịch được tạo trong bảng `payments`

### Lỗi 8: Mất kết nối sau khi thanh toán thành công nhưng trước khi nhận mã QR
- **Điều kiện:** Client mất mạng sau khi thanh toán xong nhưng trước khi nhận response từ server
- **Hành vi:**
  - Server đã cập nhật `registrations` và `payments`, phát sự kiện thông báo
  - Sinh viên không thấy mã QR vì response bị mất
  - Sinh viên có thể gửi request GET `/api/registrations/{workshopId}` để lấy lại mã QR đã tạo

## Ràng buộc

- **Hiệu năng:** API đăng ký phải xử lý trong dưới 200ms để đảm bảo trải nghiệm khi có tải cao
- **Tính nhất quán dữ liệu:**
  - Số ghế trong Redis và PostgreSQL phải đồng bộ
  - Một sinh viên không được phép đăng ký 2 lần cho cùng 1 workshop
  - Bảng `registrations` phải có constraint `UNIQUE(user_id, workshop_id)` (đề nghị áp dụng migration Flyway để enforce ở DB)
  - Ứng dụng phải kiểm tra `Idempotency-Key` (Redis) trước khi gọi Payment Gateway; lưu kết quả idempotency (TTL 24h)
  - Payment Gateway calls phải được bọc bằng Circuit Breaker (Resilience4j) và timeout 30s
- **Bảo mật:**
  - Chỉ sinh viên đã xác thực mới được đăng ký
  - Mã QR phải là unique và khó đoán (sinh ra bằng UUID hoặc hash)
- **Idempotency:** Cho workshop có phí, mỗi yêu cầu thanh toán phải có `Idempotency-Key` duy nhất; server phải kiểm tra key này trong 24 giờ

## Tiêu chí chấp nhận

- [ ] Sinh viên có thể xem danh sách workshop
- [ ] Sinh viên có thể đăng ký workshop miễn phí và nhận mã QR ngay
- [ ] Sinh viên có thể đăng ký workshop có phí, đi đến trang thanh toán, nhập thông tin thẻ
- [ ] Sau khi thanh toán thành công, sinh viên nhận mã QR
- [ ] Hệ thống không cho phép sinh viên đăng ký 2 lần cùng 1 workshop
- [ ] Hệ thống không cho phép 2 sinh viên cùng đăng ký 1 chỗ ngồi cuối cùng (zero-overbooking)
- [ ] Nếu sinh viên bấm "Thanh toán" 2 lần, chỉ 1 giao dịch được tạo
- [ ] Khi thanh toán thất bại, sinh viên có thể thử lại mà không bị khoá
- [ ] Khi Payment Gateway timeout liên tục, hệ thống vẫn cho phép xem danh sách workshop
- [ ] API đăng ký đáp ứng trong dưới 200ms trong điều kiện bình thường
 - [ ] Payment Gateway calls được bảo vệ bằng Circuit Breaker; khi circuit open trả `503` và `registrations` giữ `PENDING` (client có thể retry với same `Idempotency-Key`).
 - [ ] Idempotency: nếu client gửi cùng `Idempotency-Key` trong 24h, server trả kết quả đã lưu và không gọi lại Payment Gateway.
 - [ ] Có migration Flyway để enforce `UNIQUE(user_id, workshop_id)` trong bảng `registrations`.
