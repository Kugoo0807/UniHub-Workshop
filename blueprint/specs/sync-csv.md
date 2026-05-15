# Đặc tả: Đồng bộ dữ liệu Sinh viên (CSV Sync)

## Mô tả / Phạm vi

Tính năng này chịu trách nhiệm định kỳ cập nhật danh sách sinh viên chính quy từ hệ thống quản lý cũ của trường vào cơ sở dữ liệu của UniHub Workshop. 

- **Mục tiêu:** Đảm bảo chỉ những sinh viên có tên trong danh sách của trường mới có quyền đăng ký tham gia workshop.

- **Phạm vi:**

   - Lấy file CSV từ nguồn cấp (giả lập trên Supabase Storage).
   
   - Xác thực định dạng và chuyển đổi dữ liệu thành thực thể `User` với vai trò `STUDENT`.
   
   - Cập nhật thông tin nếu sinh viên đã tồn tại hoặc thêm mới nếu chưa có.

## Luồng chính

1. **Kích hoạt:** Hệ thống tự động kích hoạt Job đồng bộ vào lúc 02:00 sáng hàng ngày (sử dụng `@Scheduled`).

2. **Lấy dữ liệu (Fetch):** 

   - `StudentSyncService` gọi `CsvFetchStrategy` (hiện tại là `SupabaseHttpStrategy`) để mở một kết nối Stream tới URL chứa file CSV.
   
   - Dữ liệu được đọc dưới dạng `InputStream` để tối ưu bộ nhớ.

3. **Xử lý Batching:**
   
   - Hệ thống đọc từng dòng trong CSV và map sang đối tượng `User`.
   
   - Các bản ghi được gom thành từng đợt (Batch) với kích thước 500 - 1000 records.

4. **Lưu trữ (Persistence):** Sử dụng `JdbcTemplate` thực thi câu lệnh Native SQL `INSERT ... ON CONFLICT (student_code) DO UPDATE` để thực hiện UPSERT đồng loạt cho cả Batch.

5. **Hoàn tất:** Ghi nhận nhật ký (Log) về tổng số dòng đã xử lý thành công và số dòng thất bại.

## Kịch bản lỗi

| Kịch bản lỗi | Mã lỗi (Error Code) | Xử lý của hệ thống | Response / Trạng thái |
| :--- | :--- | :--- | :--- |
| **Không tìm thấy file CSV** | `SYNC_FILE_NOT_FOUND` | Ghi Log Error, gửi thông báo (Telegram/Email) cho Admin và ngắt Job. (@TODO: Chỉ thêm comment @TODO, tuyệt đối không cài đặt) | Trạng thái Job: `ABORTED`. <br> Nếu trigger thủ công qua API: `404 Not Found` - `{"error": "CSV source unreachable"}` |
| **Dòng dữ liệu sai định dạng** | `SYNC_ROW_PARSE_ERROR` | Bọc `try-catch` cho từng dòng. Hệ thống ghi data lỗi vào log/bảng `sync_errors` và tiếp tục xử lý dòng kế tiếp. | Dòng bị lỗi: `SKIPPED`. <br> Trạng thái Job: Vẫn tiếp tục `PROCESSING`. |
| **Lỗi kết nối Database** | `SYNC_DB_TIMEOUT` | Sử dụng cơ chế Retry (tối đa 3 lần). Nếu vẫn thất bại, Rollback Batch hiện tại để tránh mất mát dữ liệu và dừng Job. | Trạng thái Job: `FAILED`. <br> Bắn cảnh báo `CRITICAL` hệ thống. |
| **Dữ liệu trùng lặp trong file** | `SYNC_DUPLICATE_RESOLVED` | Xử lý tự động ở tầng Database bằng cơ chế `ON CONFLICT` của PostgreSQL (tự động ghi đè bản ghi mới nhất). | Thực thi `UPSERT` thành công. <br> Trạng thái Job: Vẫn tiếp tục `PROCESSING`. |

## Ràng buộc

- **Hiệu năng:** Thời gian xử lý cho 50.000 sinh viên không được quá 5 phút.

- **Tài nguyên:** Phải sử dụng phương pháp Stream, không được load toàn bộ file vào RAM để tránh lỗi OutOfMemory (OOM).

- **Tính mở rộng:** Phải sử dụng **Strategy Pattern** cho module lấy file để có thể chuyển đổi sang SFTP hoặc S3 chỉ bằng cách thay đổi cấu hình `application.yml`.

- **An toàn:** Vai trò của tất cả người dùng được nhập từ CSV mặc định luôn là `STUDENT`.

## Tiêu chí chấp nhận

- [ ] Job chạy đúng giờ quy định trong cấu hình Cron.

- [ ] Sinh viên mới có trong CSV có thể đăng nhập/đăng ký workshop ngay sau khi đồng bộ.

- [ ] Thông tin sinh viên cũ (tên, email) được cập nhật chính xác nếu có thay đổi trong file CSV.

- [ ] Hệ thống không bị treo hoặc sập nếu file CSV chứa một số dòng dữ liệu "rác" (sai format).

- [ ] Log hệ thống hiển thị rõ ràng số lượng bản ghi thành công/thất bại sau mỗi phiên chạy.
