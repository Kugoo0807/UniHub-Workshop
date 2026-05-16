# Đặc tả: Room Management

## Mô tả

Admin có thể quản lý phòng học/hội trường (CRUD cơ bản). Mỗi phòng có tên, sức chứa, và URL bản đồ/sơ đồ phòng (`layout_map_url`). Bản đồ phòng được tải lên dưới dạng ảnh từ phía frontend và lưu vào **Cloudinary**, sau đó URL trả về từ Cloudinary sẽ được lưu vào DB.

Feature này là **prerequisite** của Workshop Management — mỗi workshop phải gắn với một room có sức chứa đủ.

---

## Schema tham chiếu (`V4__complete_workshop_and_rooms.sql`)

```sql
CREATE TABLE rooms (
    id             BIGSERIAL       PRIMARY KEY,
    name           VARCHAR(100)    UNIQUE NOT NULL,
    layout_map_url VARCHAR(1024),
    capacity       INTEGER         NOT NULL
);
```

**Lưu ý:**
- `name` phải là **UNIQUE** — không được tồn tại 2 phòng cùng tên.
- `layout_map_url` là **nullable** — admin có thể tạo phòng trước, tải ảnh bản đồ sau.
- `capacity` là số nguyên dương — không được <= 0.
- Một phòng đang có workshop PUBLISHED/DRAFT gắn vào **không thể xóa** (ràng buộc FK).

---

## Cloudinary Upload Flow

```
[Admin chọn ảnh] → [Frontend gửi multipart/form-data]
    → [POST /api/v1/admin/rooms/{id}/map-image]
        → [CloudinaryService.upload(file)]
            → [Cloudinary API trả về secure_url]
                → [Cập nhật room.layoutMapUrl = secure_url vào DB]
                    → [Trả về RoomResponse với layoutMapUrl mới]
```

- Chỉ chấp nhận file ảnh: `image/jpeg`, `image/jpg`, `image/png`, `image/webp`.
- Giới hạn kích thước: **5MB**.
- Ảnh được upload vào Cloudinary folder `unihub/rooms/`.
- Nếu phòng đã có ảnh cũ, ảnh mới sẽ **ghi đè** (không xóa ảnh cũ trên Cloudinary để đơn giản hóa).

---

## Luồng chính

### 1. Lấy danh sách phòng (Admin)

1. Client gửi `GET /api/v1/admin/rooms` với JWT role `ADMIN`.
2. Service truy vấn tất cả phòng từ DB, sắp xếp theo `id` tăng dần.
3. Trả về `200 OK` kèm danh sách `RoomResponse`.

### 2. Lấy chi tiết phòng (Admin)

1. Client gửi `GET /api/v1/admin/rooms/{id}` với JWT role `ADMIN`.
2. Nếu không tìm thấy → trả `404 Not Found`.
3. Trả về `200 OK` kèm `RoomResponse`.

### 3. Tạo phòng mới (Admin)

1. Client gửi `POST /api/v1/admin/rooms` với body JSON.
2. Controller validate:
   - `name`: not blank, max 100 ký tự.
   - `capacity`: not null, > 0.
3. Service validate thêm:
   - `name` phải **unique** — nếu đã tồn tại → throw `ConflictException` (`409`).
4. INSERT phòng vào DB (không bao gồm `layout_map_url`, upload ảnh là bước riêng).
5. Trả về `201 Created` kèm `RoomResponse`.

### 4. Cập nhật thông tin phòng (Admin)

1. Client gửi `PUT /api/v1/admin/rooms/{id}` với body JSON.
2. Nếu `id` không tồn tại → trả `404`.
3. Validate tương tự như tạo phòng.
4. Nếu `name` thay đổi và tên mới đã tồn tại ở phòng khác → `409 Conflict`.
5. **Không được thay đổi `capacity`** khi phòng đang có workshop ở trạng thái `DRAFT` hoặc `PUBLISHED`:
   - Nếu vi phạm → `409 Conflict` — "Cannot change capacity while N active workshop(s) are using this room".
   - Lý do: Workshops đang active có thể có registrations, thay đổi capacity có thể vi phạm `total_slots <= capacity`.
6. Cập nhật `name`, `capacity`.  
   > **Lưu ý:** `layout_map_url` KHÔNG cập nhật qua endpoint này — dùng endpoint upload ảnh riêng.
7. Trả về `200 OK` kèm `RoomResponse` đã cập nhật.

### 5. Upload/cập nhật ảnh bản đồ phòng (Admin)

1. Client gửi `POST /api/v1/admin/rooms/{id}/map-image` với `multipart/form-data`.
2. Nếu `id` không tồn tại → trả `404`.
3. Validate file:
   - Không được rỗng.
   - Content-Type phải thuộc tập hợp: `image/jpeg`, `image/jpg`, `image/png`, `image/webp`.
   - Kích thước ≤ 5MB.
4. `CloudinaryService.upload(bytes, filename, folder)` → trả về `secure_url`.
5. Cập nhật `room.layoutMapUrl = secure_url` vào DB.
6. Trả về `200 OK` kèm `RoomResponse` với `layoutMapUrl` mới.

### 6. Xóa phòng (Admin)

1. Client gửi `DELETE /api/v1/admin/rooms/{id}` với JWT role `ADMIN`.
2. Nếu `id` không tồn tại → trả `404`.
3. Kiểm tra phòng có đang được dùng bởi bất kỳ workshop nào có `status IN ('DRAFT', 'PUBLISHED')` không:
   - Nếu có → throw `ConflictException` (`409`) — "Room is currently used by active workshops".
4. Hard delete phòng khỏi DB.
5. Trả về `204 No Content`.

---

## API Endpoints

| Method | Endpoint | Role | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/admin/rooms` | ADMIN | Lấy danh sách tất cả phòng |
| `GET` | `/api/v1/admin/rooms/{id}` | ADMIN | Lấy chi tiết một phòng |
| `POST` | `/api/v1/admin/rooms` | ADMIN | Tạo phòng mới |
| `PUT` | `/api/v1/admin/rooms/{id}` | ADMIN | Cập nhật tên/sức chứa phòng |
| `POST` | `/api/v1/admin/rooms/{id}/map-image` | ADMIN | Upload ảnh bản đồ phòng lên Cloudinary |
| `DELETE` | `/api/v1/admin/rooms/{id}` | ADMIN | Xóa phòng (nếu không có workshop active) |

> **Endpoint GET /api/v1/rooms (public)** đã tồn tại từ trước — giữ nguyên, không xóa.

---

## Request / Response Schema

### `RoomRequest` (POST/PUT body)

```json
{
  "name": "Hall A",
  "capacity": 120
}
```

### `RoomResponse`

```json
{
  "id": 1,
  "name": "Hall A",
  "capacity": 120,
  "layoutMapUrl": "https://res.cloudinary.com/demo/image/upload/unihub/rooms/hall-a.jpg",
  "activeWorkshopCount": 2
}
```

- `activeWorkshopCount`: số workshop đang `DRAFT` hoặc `PUBLISHED` gắn với phòng này (dùng để disable nút Delete trên UI).

---

## Kịch bản lỗi

| Tình huống | HTTP Status | Xử lý |
|---|---|---|
| `GET /api/v1/admin/rooms/{id}` — ID không tồn tại | `404` | "Room not found" |
| `POST /api/v1/admin/rooms` — `name` đã tồn tại | `409` | "Room name already exists" |
| `POST /api/v1/admin/rooms` — `capacity` <= 0 | `400` | Bean Validation (`@Positive`) |
| `POST /api/v1/admin/rooms` — Thiếu `name` | `400` | Bean Validation |
| `POST /api/v1/admin/rooms/{id}/map-image` — File rỗng | `400` | "File is empty" |
| `POST /api/v1/admin/rooms/{id}/map-image` — Không phải ảnh | `400` | "Only image files are accepted (jpeg, jpg, png, webp)" |
| `POST /api/v1/admin/rooms/{id}/map-image` — File > 5MB | `413` | "File size exceeds 5MB limit" |
| `PUT /api/v1/admin/rooms/{id}` — Đổi capacity khi phòng có workshop DRAFT/PUBLISHED | `409` | "Cannot change capacity while N active workshop(s) are using this room" |
| Bất kỳ endpoint nào — Không có JWT hoặc không phải ADMIN | `401`/`403` | Spring Security chặn |

---

## Ràng buộc

- **Security:** Tất cả endpoints `/api/v1/admin/rooms/**` đều yêu cầu `ADMIN` role.
- **Cloudinary credentials** được đọc từ biến môi trường: `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`.
- **Unique name:** Enforce ở cả Service layer (friendly error) và DB (UNIQUE constraint).
- **Không xóa phòng** khi có workshop DRAFT/PUBLISHED gắn vào — tránh orphan data.
- **Không dùng Flyway migration mới** cho feature này vì schema `rooms` đã tồn tại từ `V4`.

---

## Entity & DTO (Java)

### Entity `Room` (đã có — cần thêm `location` nếu muốn, hiện tại giữ nguyên)

```java
@Entity
@Table(name = "rooms")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "layout_map_url", length = 1024)
    private String layoutMapUrl;

    @Column(nullable = false)
    private Integer capacity;
}
```

### DTO `RoomRequest`

```java
public record RoomRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    String name,

    @NotNull(message = "Capacity is required")
    @Positive(message = "Capacity must be greater than 0")
    Integer capacity
) {}
```

### DTO `RoomResponse`

```java
@Getter @Builder
public class RoomResponse {
    private Long id;
    private String name;
    private Integer capacity;
    private String layoutMapUrl;
    private Long activeWorkshopCount;
}
```

### Service `CloudinaryService`

```java
@Service
public class CloudinaryService {
    private final Cloudinary cloudinary;

    public String uploadImage(byte[] bytes, String filename, String folder) {
        // Upload to Cloudinary with folder param
        // Return secure_url from result map
    }
}
```

### Service `RoomService`

```java
@Service
public class RoomService {
    RoomResponse getAllRooms();
    RoomResponse getRoomById(Long id);
    RoomResponse createRoom(RoomRequest request);
    RoomResponse updateRoom(Long id, RoomRequest request);
    RoomResponse uploadMapImage(Long id, MultipartFile file);
    void deleteRoom(Long id);
}
```

---

## Frontend

### Trang `/admin/rooms`

- **Table** hiển thị danh sách phòng: Tên, Sức chứa, Có ảnh bản đồ hay không, Số workshop đang active, Actions.
- **Create button** → Mở modal tạo phòng (form: Name, Capacity).
- **Actions** (dropdown giống Dashboard):
  - **Edit** → Modal chỉnh sửa Name/Capacity.
  - **Upload Map** → Modal tải ảnh bản đồ (input file, preview, submit).
  - **Delete** → Confirmation modal. Disabled nếu `activeWorkshopCount > 0`.
- **Hiển thị ảnh bản đồ** → Click icon/thumbnail mở preview ảnh full-size.

### Service `adminRoomService.js`

```js
const adminRoomUrl = '/admin/rooms';

const adminRoomService = {
    getAll() { ... },
    getById(id) { ... },
    create(data) { ... },         // { name, capacity }
    update(id, data) { ... },     // { name, capacity }
    uploadMapImage(id, file) { ... },  // multipart/form-data
    delete(id) { ... },
};
```

---

## Tiêu chí chấp nhận

| Test ID | Scenario | Expected |
|---|---|---|
| `RM-01` | Tạo phòng hợp lệ | `201`, DB có record mới |
| `RM-02` | Tạo phòng trùng tên | `409` — "Room name already exists" |
| `RM-03` | Tạo phòng `capacity = 0` | `400` |
| `RM-04` | Upload ảnh JPEG hợp lệ | `200`, `layoutMapUrl` được cập nhật với Cloudinary URL |
| `RM-05` | Upload file PDF | `400` — "Only image files are accepted" |
| `RM-06` | Upload ảnh > 5MB | `413` |
| `RM-07` | Xóa phòng không có workshop active | `204` |
| `RM-08` | Xóa phòng đang có PUBLISHED workshop | `409` |
| `RM-09` | Cập nhật tên phòng thành tên đã tồn tại | `409` |
| `RM-10` | Lấy chi tiết phòng không tồn tại | `404` |

---

## Dependencies & Blocking

| Dependency | Loại | Ghi chú |
|---|---|---|
| Schema `rooms` (`V4__...`) | Đã có | Không cần migration mới |
| Cloudinary SDK (`cloudinary-http45`) | **Cần thêm vào pom.xml** | `com.cloudinary:cloudinary-http45` |
| Env vars: `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | **Cần config** | Thêm vào `sample.env` và `application.yaml` |
| Feature: Auth | **Blocking** | Cần JWT + RBAC |
| Feature: Workshop Management | Downstream | Workshop cần chọn Room từ list này |
