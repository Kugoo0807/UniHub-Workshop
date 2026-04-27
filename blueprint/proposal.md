# UniHub Workshop — Project Proposal

## Vấn đề
Hiện tại, "Tuần lễ kỹ năng và nghề nghiệp" của trường đang quản lý đăng ký thông qua Google Form và gửi email thủ công. Quy trình này có nhiều điểm yếu khi quy mô tăng lên:

- Không thể kiểm soát tranh chấp chỗ ngồi theo thời gian thực.

- Google Form không hỗ trợ luồng thanh toán tích hợp.

- Ban tổ chức mất quá nhiều thời gian đối soát thủ công và gửi email.

- Không có cơ chế check-in tại chỗ chuyên nghiệp, đặc biệt tại các giảng đường sóng yếu.

## Mục tiêu
Xây dựng hệ thống UniHub Workshop nhằm số hóa 100% quy trình tổ chức:

- Chịu tải đột biến: Xử lý mượt mà 12.000 lượt truy cập đồng thời trong 10 phút đầu tiên mở đăng ký (60% dồn vào 3 phút đầu ~ 40 req/s).

- Thời gian phản hồi API đăng ký dưới 200ms để đảm bảo trải nghiệm.

- Zero-overbooking: **TUYỆT ĐỐI** tránh trường hợp 2 người cùng đăng ký thành công 1 chỗ ngồi cuối cùng.

- Xây dựng kiến trúc cho hệ thống thông báo, hỗ trợ tích hợp các kênh thông báo mới mà không ảnh hưởng hệ thống cốt lõi.

## Người dùng và nhu cầu

- Sinh viên: Xem lịch, đăng ký, thanh toán an toàn, nhận thông báo xác nhận, nhận vé QR và check-in.

- Ban tổ chức: Tạo/sửa/xóa workshop, theo dõi thống kê realtime, tự động hóa việc tóm tắt nội dung workshop bằng AI.

- Nhân sự check-in: Sử dụng `Mobile App` để quét mã QR, cho phép ghi nhận check-in tạm thời khi không có mạng và tự đồng bộ lại khi kết nối được phục hồi.

## Phạm vi

- In-scope: Backend API Core, hệ thống quản lý giao dịch, Mobile App check-in, service AI tóm tắt PDF, Job đồng bộ CSV.

- Out-scope: Không tích hợp cổng thanh toán ngân hàng thật (sử dụng Mock Gateway), không deploy lên hạ tầng Cloud production (chỉ đóng gói Docker Compose chạy local/server test).

## Rủi ro và ràng buộc

- Tranh chấp ghế (Seat Contention) khi hàng ngàn người nhắm vào workshop có số lượng chỗ ngồi cố định.

- Rủi ro trừ tiền 2 lần do user bấm spam hoặc lỗi mạng lúc thanh toán.

- Hệ thống bị sập nếu cổng thanh toán bên thứ ba bị timeout.

- Rủi ro mất mát dữ liệu khi nhân sự check-in offline và app bị crash trước khi có mạng để đồng bộ.