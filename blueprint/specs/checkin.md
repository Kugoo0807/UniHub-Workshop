# Đặc tả: Quét mã QR Check-in tại sự kiện

## Mô tả

Nhân sự check-in (role `STAFF`) sử dụng mobile app Android để quét mã QR của sinh viên tại cửa phòng workshop. App hoạt động theo mô hình **offline-first**: tải trước danh sách QR hợp lệ về SQLite, quét và ghi nhận check-in tại local, sau đó đồng bộ dữ liệu lên server khi có kết nối mạng.

### Actors
- **Nhân sự check-in (STAFF)**: Người thực hiện quét QR tại cửa phòng.

### Preconditions
- STAFF đã đăng nhập vào mobile app bằng tài khoản có role `STAFF`.
- Workshop đang ở trạng thái `PUBLISHED` hoặc `COMPLETED`.
- Sinh viên đã đăng ký thành công (registration status = `SUCCESS`) và có mã QR.

---

## Luồng chính

### Bước 1 — Đăng nhập (một lần)
1. STAFF mở app → Nhập email + password.
2. App gọi `POST /api/v1/auth/login/app` → Nhận JWT tokens.
3. Lưu access token + refresh token vào SharedPreferences.
4. Chuyển sang màn hình danh sách workshops.

### Bước 2 — Tải danh sách QR (trước sự kiện)
1. STAFF chọn workshop cần check-in từ danh sách.
2. Nhấn nút **"Tải danh sách QR"**.
3. App gọi `GET /api/v1/workshops/{id}/attendees` (kèm Bearer token).
4. Server trả về danh sách attendees hợp lệ: `{ qrCode, registrationId, studentName, studentCode }`.
5. App lưu toàn bộ vào SQLite (Room Database), ghi đè dữ liệu cũ.
6. Hiển thị: **"Cập nhật lần cuối lúc: [HH:mm dd/MM/yyyy]"**.

### Bước 3 — Quét mã QR (tại sự kiện)
1. STAFF nhấn **"Quét QR"** → Mở camera.
2. Camera quét mã QR → Trích xuất nội dung (giá trị `qr_code` từ bảng `registrations`).
3. App kiểm tra trong SQLite local:
   - **Tìm thấy + chưa check-in** → Đánh dấu `is_checked_in = true`, lưu `scanned_at = now()`, đặt `is_synced = false`. Hiển thị ✅ kèm tên sinh viên, MSSV.
   - **Tìm thấy + đã check-in** → Hiển thị ⚠️ "Sinh viên [tên] đã check-in trước đó lúc [scanned_at]".
   - **Không tìm thấy** → Hiển thị ❌ "Vé không hợp lệ hoặc không thuộc workshop này".

### Bước 4 — Đồng bộ dữ liệu lên server

#### 4a. Đồng bộ tự động (Background Sync)
1. App đăng ký WorkManager job với constraint `NetworkType.CONNECTED`.
2. Khi thiết bị có kết nối mạng → Worker tự động chạy:
   - Query SQLite: Lấy tất cả records có `is_synced = false`.
   - Gom thành batch gọi `POST /api/v1/checkins/sync` với body:
     ```json
     {
       "workshopId": 5,
       "records": [
         { "qrCode": "abc-123", "scannedAt": "2026-05-20T09:15:30" },
         { "qrCode": "def-456", "scannedAt": "2026-05-20T09:16:45" }
       ]
     }
     ```
   - Server xử lý từng record: `INSERT INTO checkin_records (...) ON CONFLICT (registration_id) DO NOTHING`.
   - Server trả kết quả: `{ totalReceived, successCount, duplicateCount }`.
   - App update `is_synced = true` cho các records đã sync thành công.

#### 4b. Đồng bộ thủ công (Dự phòng)
1. STAFF nhấn nút **"Đồng bộ"** trên toolbar.
2. App thực hiện sync ngay lập tức (cùng logic với background sync).
3. Hiển thị kết quả: "Đã đồng bộ X/Y lượt check-in".

---

## API Endpoints

### `GET /api/v1/workshops/{id}/attendees`
- **Auth**: Bearer token, role `STAFF`
- **Response**: `200 OK`
  ```json
  [
    {
      "qrCode": "uuid-abc-123",
      "registrationId": 42,
      "studentName": "Nguyễn Văn A",
      "studentCode": "21127001"
    }
  ]
  ```
- **Errors**: `401 Unauthorized`, `403 Forbidden`, `404 Workshop not found`

### `POST /api/v1/checkins/sync`
- **Auth**: Bearer token, role `STAFF`
- **Request Body**:
  ```json
  {
    "workshopId": 5,
    "records": [
      { "qrCode": "uuid-abc-123", "scannedAt": "2026-05-20T09:15:30" }
    ]
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "totalReceived": 10,
    "successCount": 8,
    "duplicateCount": 2,
    "failedCount": 0,
    "failures": []
  }
  ```
- **Errors**: `401 Unauthorized`, `403 Forbidden`

---

## Kịch bản lỗi

| Kịch bản | Hành vi |
|:---|:---|
| Mất mạng khi quét | App vẫn quét bình thường (offline-first), ghi nhận vào SQLite. Sync sau khi có mạng. |
| Mất mạng khi tải danh sách QR | Hiển thị lỗi "Không có kết nối mạng. Vui lòng thử lại.", sử dụng dữ liệu cũ nếu có. |
| QR code không thuộc workshop | App kiểm tra local → Hiển thị "Vé không hợp lệ". |
| Quét trùng QR | App kiểm tra `is_checked_in` → Hiển thị "Đã check-in trước đó". |
| Sync trùng lặp (multi-device) | Server dùng `ON CONFLICT DO NOTHING` → Trả `duplicateCount`, không lỗi. |
| Token hết hạn | App tự refresh token qua `/auth/refresh`. Nếu refresh cũng hết hạn → Yêu cầu đăng nhập lại. |
| OS kill background job | STAFF dùng nút "Đồng bộ" thủ công để đẩy dữ liệu. |

---

## Ràng buộc

- **Idempotency**: Mỗi registration chỉ được check-in 1 lần trong DB (`UNIQUE(registration_id)` + `ON CONFLICT DO NOTHING`).
- **Offline-first**: Mọi thao tác quét QR phải hoạt động khi không có mạng.
- **Security**: Chỉ role `STAFF` được access API check-in. JWT Bearer token bắt buộc.
- **Data integrity**: `scanned_at` ghi nhận thời gian quét thực tế tại app (không phải thời gian server nhận).
- **Performance**: Sync theo batch, không gửi từng record riêng lẻ.

---

## Tiêu chí chấp nhận

1. ✅ STAFF đăng nhập thành công bằng tài khoản role `STAFF`.
2. ✅ Tải danh sách QR hợp lệ của workshop về SQLite local.
3. ✅ Quét QR thành công khi có mạng → Check-in ghi nhận + sync ngay.
4. ✅ Quét QR thành công khi **không có mạng** → Check-in ghi nhận local, sync khi có mạng.
5. ✅ Quét QR trùng → Hiển thị cảnh báo, không duplicate trong DB.
6. ✅ Quét QR không hợp lệ → Hiển thị lỗi rõ ràng.
7. ✅ Nút "Đồng bộ" thủ công hoạt động đúng.
8. ✅ Background sync tự động chạy khi kết nối mạng phục hồi.
9. ✅ Server API trả kết quả sync chi tiết (success/duplicate/failed counts).
