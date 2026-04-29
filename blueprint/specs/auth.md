# Đặc tả: Authentication & Authorization (V1)

## Mô tả
- Hệ thống sử dụng cơ chế stateless dựa trên JWT (JSON Web Token) để xác thực và phân quyền dựa trên vai trò (RBAC).

## Luồng hoạt động
1. **Đăng ký (Student):**
    + Student nhập các thông tin cơ bản của bản thân (`full_name`, `student_code`, `email`, `phone_number`, `password`).
    
    + Hệ thống thực hiện **ĐỐI SOÁT** `student_code`, `full_name`, và `email` với Database để đảm Student này thực sự tồn tại trong trường.

        * Không tồn tại => Báo lỗi.

        * Có tồn tại nhưng `password` != NULL => Báo lỗi (vì tài khoản này đã được kích hoạt).

        * Có tồn tại nhưng `password` = NULL => Qua bước tiếp theo

    + Hệ thống mã hóa mật khẩu bằng `bcrypt`, sau đó chỉ cập nhật trường `password`, `phone_number` (nếu có) và `status = 'ACTIVE'` xuống Database.

    + Thông báo thành công.

2. **Đăng ký (Admin & Staff):**
    + Việc đăng ký Admin và Staff được thực hiện riêng biệt với Student vì có flow hoàn toàn khác.

    + Chỉ có Admin mới được phép tạo acc cho Admin và Staff.

    + Admin nhập các thông tin cơ bản của tài khoản cần tạo, hệ thống kiểm tra Database và thực hiện cập nhật (set `status = 'ACTIVE'`).

3. **Đăng nhập (Web):**
    + Chỉ có Admin và Student (RBAC).

    + Đầu vào: Email/Password

    + Hệ thống xác thực và kiểm tra role.

    + Đầu ra: `access_token` và `refresh_token` chứa `student_id` và `role`.

4. **Đăng nhập (App):**
    + Chỉ có Staff (RBAC).

    + Đầu vào: Email/Password

    + Hệ thống xác thực và kiểm tra role.

    + Đầu ra: `access_token` và `refresh_token` chứa `user_id` và `role`.

5. **Refresh Token:**
    + Được Client gửi sau khi bị báo lỗi `401`.

    + Đầu vào: `refresh_token`

    + Đầu ra: `access_token` và `refresh_token` chứa `user_id` và `role`.

6. **Truy cập API:**
    + Client gửi `access_token` trong Header `Authorization: Bearer <token>`.

    + `access_token` hết hạn => gọi API **Refresh Token**.

## Kịch bản lỗi
1. **Đăng ký (Student):**
    + Thông tin không tồn tại trong Database => `400`

    + Thông tin tồn tại nhưng `password` != NULL (đã kích hoạt tài khoản) => `400`

    + Lỗi server trong quá trình xử lý => `500`

2. **Đăng ký (Admin & Staff):**
    + Người thao tác không có role `ADMIN` => `403`

    + Thông tin người dùng đã có trong Database (`email` đã tồn tại) => `400`

    + Lỗi server trong quá trình xử lý => `500`

3. **Đăng nhập (Web):**
    + Sai `email`/`password` trong quá trình xác thực với database => `400`

    + Tài khoản bị vô hiệu hóa hoặc chưa kích hoạt (`status = 'INACTIVE'`) => `400`

    + Hệ thống kiểm tra sau khi tra database và xác nhận người dùng có role `STAFF` => `403`

    + Lỗi server trong quá trình xử lý => `500`

4. **Đăng nhập (App):**
    + Sai `email`/`password` trong quá trình xác thực với database => `400`

    + Tài khoản bị vô hiệu hóa hoặc chưa kích hoạt (`status = 'INACTIVE'`) => `400`

    + Hệ thống kiểm tra sau khi tra database và xác nhận người dùng có role `STUDENT` & `ADMIN` => `403`

    + Lỗi server trong quá trình xử lý => `500`

5. **Refresh Token:**
    + `refresh_token` sai hoặc hết hạn => `401`

    + Lỗi server trong quá trình xử lý => `500`

6. **Truy cập API:**
    + `access_token` sai hoặc hết hạn => `401`

    + Lỗi server trong quá trình xử lý => Tùy thuộc vô API truy cập.

## Ràng buộc
- JWT phải có thời gian hết hạn (TTL):
    + `access_token`: 30 phút.
    
    + `refresh_token`: 1 ngày.

- Mật khẩu không bao giờ được trả về trong API response (Yêu cầu sử dụng @JsonIgnore ở thuộc tính password trong Entity User).

- Xử lý tầng Security: Trừ `/api/test`, `/api/v1/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, cần được kiểm tra luồng 6.