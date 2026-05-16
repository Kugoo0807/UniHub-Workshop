# Đặc tả: Hệ thống Thông báo

## Mô tả
Hệ thống Thông báo là cấu phần chịu trách nhiệm truyền tải các thông tin quan trọng từ hệ thống UniHub Workshop đến người dùng (Sinh viên và Quản trị viên). Để đảm bảo trải nghiệm người dùng mượt mà và khả năng mở rộng trong tương lai, hệ thống áp dụng kiến trúc bất đồng bộ kết hợp song song hai mẫu thiết kế: **Observer Pattern** (tách biệt luồng nghiệp vụ chính và luồng gửi tin) và **Strategy Pattern** (linh hoạt chuyển đổi hoặc kích hoạt đồng thời nhiều kênh thông báo).

Hệ thống hỗ trợ 3 kênh thông báo cốt lõi:
1. **Gmail:** Kênh bắt buộc (100% hoạt động) cho các sự kiện giao dịch quan trọng (đăng ký vé, mã QR, thông báo khẩn cấp).

2. **Telegram:** Kênh tùy chọn mở rộng, gửi thông tin nhanh khi người dùng cấu hình số điện thoại tích hợp Telegram.

3. **In-app Notification (Thông báo trong ứng dụng):** Hiển thị trực tiếp trên giao diện Web Frontend của Sinh viên và Admin.

   - **Yêu cầu UI/UX Frontend:** 
      + Tại layout chung (Header), bổ sung một biểu tượng chiếc chuông thông báo.

      + Khi người dùng nhấn vào biểu tượng chuông, hệ thống sẽ chuyển hướng (Redirect) toàn màn hình sang trang Danh sách thông báo (`/notifications`).
      
      + Trang này bắt buộc phải tích hợp cơ chế phân trang (Pagination) ở cả giao diện lẫn API backend để tối ưu hiệu năng tải dữ liệu, tránh làm lag trình duyệt khi số lượng thông báo tích lũy lớn.

---

## Luồng chính (4 Luồng Nghiệp vụ Trọng tâm)

### Luồng 1: Sinh viên đăng ký workshop thành công

* **Bối cảnh kích hoạt:** Sau khi server đã cập nhật bản ghi `registrations` thành `SUCCESS`.

* **Các bước xử lý:**
  
  1. RegistrationService phát đi một WorkshopRegistrationSuccessEvent chứa: registrationId, studentEmail, studentName, workshopTitle, qrCodeUrl.

  2. NotificationEventListener bắt được sự kiện bất đồng bộ (@Async) sau khi transaction đã commit thành công (TransactionPhase.AFTER_COMMIT).
  
  3. Hệ thống nạp template nội dung tương ứng cho luồng đăng ký thành công.
  
  4. Hệ thống kiểm tra cấu hình người dùng: Mặc định đẩy vào hàng đợi kênh EMAIL và kênh IN_APP. Nếu hồ sơ người dùng có số điện thoại hợp lệ đã liên kết Telegram, tự động thêm kênh TELEGRAM vào danh sách.
  
  5. NotificationDispatcher điều phối và kích hoạt song song các Strategy để gửi tin.

* **Yêu cầu nội dung template:** Phải chứa lời chào cá nhân hóa, tiêu đề workshop, thời gian tổ chức, số phòng và **bắt buộc nhúng mã QR hoàn chỉnh** (dưới dạng thẻ ảnh đính kèm trong email hoặc đường link ảnh hiển thị rõ ràng trên Telegram/In-app) để phục vụ check-in tại cửa.

### Luồng 2: Ban tổ chức hủy Workshop
* **Bối cảnh kích hoạt:** Người quản trị (Admin) thay đổi trạng thái Workshop thành CANCELLED.

* **Các bước xử lý:**
  
  1. Hệ thống xác định danh sách tất cả các sinh viên đã có trạng thái đăng ký là SUCCESS tại workshop bị hủy đó.
  
  2. WorkshopService phát đi một WorkshopCancelledEvent chứa thông tin định danh của workshop và danh sách đối tượng sinh viên bị ảnh hưởng.
  
  3. Async Listener tiếp nhận event, bóc tách danh sách sinh viên theo từng nhóm nhỏ (Batching - kích thước 50 dòng/batch) để đẩy vào Thread Pool xử lý song song, tránh nghẽn thread.
  
  4. Hệ thống nạp template hủy sự kiện, kiểm tra thông tin cấu hình giá vé của workshop (workshops.price).
  
  5. Phát thông báo đồng loạt qua tất cả các kênh khả dụng (EMAIL, IN_APP, và TELEGRAM if available).
* **Yêu cầu nội dung template:**
  - Đối với workshop miễn phí: Thông báo lời xin lỗi sâu sắc.

  - Đối với workshop có phí (price > 0): **BẮT BUỘC** hiển thị rõ ràng nội dung hướng dẫn quy trình hoàn trả tiền: "Vui lòng mang theo mã đăng ký này và thẻ sinh viên đến văn phòng Phòng Công tác Sinh viên trong giờ hành chính để thực hiện thủ tục hoàn tiền mặt."

### Luồng 3: Cron job nhắc lịch lúc 18h00 hằng ngày

* **Bối cảnh kích hoạt:** Tiến trình tự động @Scheduled chạy đúng 18:00:00 mỗi ngày trên máy chủ Backend.

* **Các bước xử lý:**

  1. Bộ lập lịch (Scheduler) truy vấn PostgreSQL để tìm toàn bộ các Workshop có thời gian bắt đầu (start_time) diễn ra trong ngày mai (Ngày hiện tại + 1 ngày).

  2. Với mỗi workshop tìm thấy, hệ thống truy vấn ra danh sách sinh viên tương ứng đã đăng ký vé thành công.

  3. Hệ thống phát ra WorkshopReminderEvent cho từng workshop.

  4. Listener bất đồng bộ tiếp nhận sự kiện, tự động sinh nội dung nhắc nhở dựa trên thông tin: Tên sự kiện, thời gian chính xác, phòng học và sơ đồ phòng (nếu có).

  5. Gửi thông báo nhắc lịch đến sinh viên thông qua kênh IN_APP và EMAIL/TELEGRAM.

### Luồng 4: Đồng bộ hóa file CSV dữ liệu sinh viên hoàn tất

* **Bối cảnh kích hoạt:** Tiến trình Batch Job import dữ liệu sinh viên từ hệ thống cũ chạy ngầm vào lúc 2h sáng kết thúc quá trình quét.

* **Các bước xử lý:**

  1. Trong suốt quá trình đọc Stream và thực hiện xử lý UPSERT dữ liệu, đối tượng Context của Job sẽ thu thập số liệu thống kê. Các dòng dữ liệu lỗi định dạng bị bắt bằng block try-catch riêng lẻ sẽ được tăng biến đếm lỗi và ghi lại số dòng (Line number).

  2. Khi file được đọc hết, hệ thống tổng hợp metadata bao gồm: Tổng số dòng đã quét, số dòng UPSERT thành công vào bảng users, số dòng bị lỗi format/rác bị bỏ qua, thời gian hoàn tất tiến trình.

  3. Hệ thống phát đi một CsvSyncCompletedEvent.

  4. Listener tiếp nhận và nạp template báo cáo quản trị dành riêng cho vai trò ADMIN.

  5. Gửi thông báo kết quả chi tiết trực tiếp qua kênh IN_APP, Gmail của tài khoản Admin.

### LƯU Ý: Toàn bộ template viết bằng tiếng Anh.

---

## API Endpoints (Phục vụ Kênh In-app)

Mọi API endpoints tương tác dưới đây đều yêu cầu Client đính kèm `Header Authorization: Bearer <JWT_TOKEN>` để xác thực và phân quyền người dùng.

### Lấy danh sách thông báo cá nhân (Có phân trang)

* **Endpoint:** `GET /api/v1/notifications`

* **Query Parameters:**
   - page (int, default = 0): Chỉ mục trang cần lấy (bắt đầu từ vị trí 0).
  
   - size (int, default = 10): Số lượng bản ghi tối đa hiển thị trên một trang.

* **Lưu Ý:** Phải lấy thêm những thông báo có `user_id = null` để lấy thông báo hệ thống.

---

## Kịch bản lỗi và Phương án xử lý

### 1. Lỗi kết nối dịch vụ nhà cung cấp bên ngoài (External API Failure / Timeout)
* **Tình huống:** Mail Server (SMTP) bị nghẽn mạng đột xuất hoặc Telegram API trả về mã lỗi 5xx / Connection Timeout do đứt cáp.

* **Xử lý:** Do luồng thông báo chạy bất đồng bộ tách biệt, lỗi này tuyệt đối không được ném ra làm rollback transaction đăng ký của sinh viên. Hệ thống sẽ bọc khối xử lý gửi của từng Strategy trong `try-catch`. Khi lỗi xảy ra, Strategy cập nhật bản ghi nhật ký thông báo (`notification_logs`) về trạng thái FAILED, đồng thời lưu trữ chi tiết stack trace lỗi vào cột `error_message`. Một cơ chế Retry tự động cấu hình bằng `@Retryable (Spring Retry)` sẽ kích hoạt thử lại tối đa 3 lần với khoảng thời gian giảng cách tăng dần (`Backoff policy`).

### 2. Quá tải Thread Pool xử lý bất đồng bộ (Thread Pool Exhaustion)
* **Tình huống:** Vào thời điểm 3 phút đầu khi mở cổng đăng ký cho 12.000 sinh viên, số lượng Event phát ra đồng loạt vượt quá giới hạn hàng đợi xử lý của ThreadPoolTaskExecutor.

* **Xử lý:** Cấu hình chiến lược từ chối (`RejectedExecutionHandler`) cho Task Executor là `ThreadPoolExecutor.CallerRunsPolicy`. Khi pool và queue đều đầy, thread chính đang gọi (chính là thread chạy cron job hoặc thread hoàn tất transaction) sẽ đứng ra trực tiếp thực thi tác vụ đó. Cơ chế này hoạt động như một `Backpressure`, làm chậm tốc độ sinh request mới của luồng xử lý chính để bảo vệ tài nguyên bộ nhớ hệ thống, tránh lỗi `OutOfMemory (OOM)`.

### 3. Lỗi parse template hoặc lỗi định dạng dữ liệu (Template Exception)
* **Tình huống:** File HTML template của email bị lỗi cú pháp cú pháp thymeleaf/freemarker, hoặc đối tượng dữ liệu truyền vào bị null ở một trường bắt buộc dẫn đến lỗi runtime khi render text.

* **Xử lý:** Hệ thống cô lập lỗi ở cấp độ từng thông báo cá nhân. Ghi log lỗi cấp độ ERROR kèm thông tin định danh userId và workshopId. Hủy bỏ lượt gửi đó, đánh dấu bản ghi nhật ký là `CRITICAL_ERROR` và không tiến hành cấu hình retry tự động đối với lỗi logic phần mềm này để tránh lãng phí CPU tài nguyên.

---

## Thiết kế Kỹ thuật

### Mối quan hệ mẫu kiến trúc công nghệ
Hệ thống triển khai Strategy Pattern kết hợp Spring Container Autowiring. NotificationDispatcher sẽ tự động inject toàn bộ danh sách các bean cài đặt giao diện NotificationStrategy vào một danh sách.

- Lớp điều phối chính: NotificationDispatcher

- Giao diện chung: NotificationStrategy

- Các chiến lược hiện thực cụ thể chạy song song độc lập:
  + EmailNotificationStrategy: Đảm nhận kết nối hệ thống gửi Mail (SMTP).
  
  + TelegramNotificationStrategy: Đảm nhận kết nối Telegram Bot API qua giao thức HTTPS REST.
  
  + InAppNotificationStrategy: Đảm nhận ghi dữ liệu trực tiếp vào PostgreSQL phục vụ Web Client.

---

## Ràng buộc Hệ thống

1. **Tính nhất quán yếu (Weak-consistency):** Luồng thông báo bắt buộc phải chạy bất đồng bộ hoàn toàn. Thời gian xử lý logic gửi tin của các Strategy không được phép cộng vào tổng thời gian phản hồi API của luồng đăng ký hay các luồng xử lý chính.

2. **Cô lập luồng dữ liệu (Thread Isolation):** Thread pool phục vụ cho tác vụ thông báo (`notificationTaskExecutor`) phải được định nghĩa tường minh với các thông số cấu hình giới hạn (Core size, Max size, Queue capacity) tách biệt độc lập với Thread pool xử lý HTTP request chính của hệ thống Web Tomcat.

3. **Tuân thủ giới hạn tần suất (External API Rate Limiting):** Đối với kênh Telegram, luồng gửi tin số lượng lớn (như luồng hủy Workshop) phải tuân thủ nghiêm ngặt quy định giới hạn tần suất của Telegram Bot API (Tối đa 30 tin nhắn mỗi giây gửi đến các người dùng khác nhau) bằng cách chèn độ trễ ngắn (Throttling) ở tầng Strategy hoặc chia nhỏ danh sách bằng luồng Batch Job.

---

## Tiêu chí chấp nhận

* [ ] Kiểm tra màn hình Web Frontend sinh viên, biểu tượng chiếc chuông tại Header. Khi nhấp vào, trình duyệt chuyển hướng thành công đến trang `/notifications` hiển thị danh sách dạng bảng phân trang rõ ràng, danh sách được sắp xếp giảm dần theo thời gian nhận (created_at), phải cho phép nhấn vào từng item trong list và khi nhấn vào sẽ hiển thị đầy đủ nội dung giống như Template Email (Thông báo tại list chỉ hiển thị tiêu đề).

* [ ] Khi thực hiện đăng ký thành công một workshop, email gửi về hòm thư sinh viên phải hiển thị đúng cấu trúc HTML, hiển thị rõ ràng mã QR code đại diện mà không bị lỗi hiển thị ảnh broken link.

* [ ] Khi Ban tổ chức kích hoạt tính năng hủy một workshop có thiết lập mức phí, toàn bộ sinh viên đã đăng ký tham dự phải nhận được thông báo qua Email/In-app với nội dung chứa nguyên văn dòng chữ chỉ dẫn đến địa điểm Văn phòng Phòng Công tác Sinh viên để nhận lại tiền mặt.

* [ ] Khi tiến trình import CSV ban đêm chạy xong, kiểm tra bảng thông báo của tài khoản Admin phải xuất hiện bản ghi tóm tắt tiến trình thể hiện rõ ràng các chỉ số: tổng số dòng đọc được, số dòng thành công và số dòng thất bại mà không làm gián đoạn hay dừng đột ngột luồng xử lý của hệ thống.

* [ ] Giả lập tình huống ngắt kết nối mạng đến SMTP Server trong lúc phát thông báo, hệ thống cốt lõi vẫn phải trả về kết quả đăng ký vé thành công cho sinh viên trên màn hình, đồng thời bản ghi trong DB notifications chuyển sang trạng thái FAILED kèm ghi nhận log lỗi chi tiết.
