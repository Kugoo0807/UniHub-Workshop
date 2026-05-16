# Đặc tả: Liên kết Tài khoản Telegram (Deep Linking)

## Mô tả

Tính năng cho phép Sinh viên và Ban tổ chức liên kết tài khoản Telegram cá nhân với hệ thống UniHub Workshop chỉ bằng một cú click. Sử dụng cơ chế Deep Linking của Telegram để định danh tự động mà không cần xác thực số điện thoại, giúp lấy mã `chat_id` phục vụ cho việc gửi thông báo.

---

# Luồng chính (Telegram Deep Link Flow)

1. Sinh viên truy cập trang `/profile` trên giao diện Web, nhấn nút **"Connect Telegram"**.

2. Web Frontend tự động sinh ra một link Deep Link có gắn định danh của user đó.

   Ví dụ:

   ```text
   t.me/UniHubWorkshopBot?start=user_123
   ```

   (với `123` là ID của user).

3. Hệ thống chuyển hướng người dùng mở ứng dụng Telegram.

4. Sinh viên nhấn `/start`.

5. Telegram Bot API tự động bắn một sự kiện Webhook về Backend. Trong nội dung tin nhắn sẽ chứa text là:

   ```text
   /start user_123
   ```

6. Backend tiếp nhận payload tại endpoint:

   ```http
   POST /api/v1/telegram/webhook
   ```

   Xử lý logic:

   - Tách chuỗi text lấy ra chữ `"user_123"`, suy ra ID của sinh viên trong Database là `123`.
   - Lấy `chat_id` của Telegram (nằm trong trường `message.from.id`).
   - Thực hiện lệnh `UPDATE` bảng `users`, gán cái `chat_id` vừa lấy được cho user có ID là `123`.
   - Điều khiển Bot gửi một tin nhắn báo kết nối thành công:

   ```text
   "✅ Xin chào! Tài khoản UniHub Workshop của bạn đã được liên kết thành công."
   ```

---

# API Endpoints

## Tiếp nhận Webhook từ Telegram

### Endpoint

```http
POST /api/v1/telegram/webhook
```

### Security

Public endpoint (Không qua JWT), có thể dùng secret token trên URL của webhook để bảo mật.

Ví dụ:

```http
POST /api/v1/telegram/webhook?token=SECRET_CUA_MAY
```

---

## Payload Request mẫu từ Telegram

```json
{
  "update_id": 987654321,
  "message": {
    "message_id": 45,
    "from": {
      "id": 583920123,
      "is_bot": false,
      "first_name": "Khoa"
    },
    "chat": {
      "id": 583920123,
      "type": "private"
    },
    "date": 1715842853,
    "text": "/start user_123"
  }
}
```

---

## Phản hồi (Response)

Backend phải trả về:

```http
HTTP/1.1 200 OK
```

Body rỗng ngay lập tức để xác nhận với Telegram rằng request đã được xử lý thành công.

---

# Tiêu chí chấp nhận (Acceptance Criteria)

- [ ] Kiểm tra Frontend tạo đúng định dạng Deep Link (có chứa tham số `start`).

- [ ] Kiểm tra endpoint Webhook bóc tách thành công `user_id` từ trường `text` (`/start user_id`).

- [ ] Cập nhật thành công mã `chat_id` từ Telegram vào đúng record của user trong Database.

- [ ] Bot phản hồi tin nhắn thành công ngay sau khi bấm Start.
