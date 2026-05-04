# Đặc tả: Workshop Management

## Mô tả

Ban tổ chức (Admin) có thể tạo, xem danh sách, chi tiết, chỉnh sửa, xóa workshop và xem thống kê đăng ký realtime. Tính năng này chỉ dành riêng cho Admin.

Feature này là **nền tảng (P0)** của toàn hệ thống — mọi feature khác (Registration, Payment, Check-in) đều phụ thuộc vào dữ liệu workshop.

## Schema tham chiếu (`V1__init_database.sql`)

```sql
CREATE TABLE rooms (
   id          BIGSERIAL       PRIMARY KEY,
   name        VARCHAR(100)    UNIQUE NOT NULL,
   layout_map  TEXT,
   capacity    INTEGER         NOT NULL
);
```

```sql
CREATE TABLE workshops (
   id                        BIGSERIAL       PRIMARY KEY,
   title                     VARCHAR(255)    NOT NULL,
   description               TEXT,                           -- nullable, AI sẽ điền
   room_id                   BIGINT          NOT NULL,
   speaker                   VARCHAR(255)    NOT NULL,
   status                    VARCHAR(20)     NOT NULL,       -- DRAFT/PUBLISHED/CANCELLED/COMPLETED
   total_slots               INTEGER         NOT NULL,
   remaining_slots           INTEGER         NOT NULL,       -- đồng bộ với Redis
   price                     BIGINT          DEFAULT 0,
   start_time                TIMESTAMP       NOT NULL,
   end_time                  TIMESTAMP       NOT NULL,
   registration_start_time   TIMESTAMP       NOT NULL,
   registration_end_time     TIMESTAMP       NOT NULL
);
```

**Lưu ý quan trọng từ schema thật:**
- `id` kiểu **`BIGSERIAL`** → Java entity dùng `Long`.
- `price` kiểu **`BIGINT DEFAULT 0`** → dùng `Long` và default `0L` trong Entity.
- `description` **nullable** → Admin có thể tạo workshop trước rồi AI điền sau (xem spec `ai-summary.md`).
- Schema **không có** constraint `CHECK` trên DB → validation xử lý ở **Service / Bean Validation**.

---

## Luồng chính

### 1. Xem danh sách workshop (Admin)

1. Client gửi `GET /api/workshops` với JWT role `ADMIN`.
2. Service truy vấn tất cả workshop từ DB.
3. Với mỗi workshop, đọc `remaining_slots` từ Redis key `workshop:{id}:slots`:
   - Nếu key tồn tại → dùng giá trị Redis (realtime).
   - Nếu key không tồn tại → fallback về cột `remaining_slots` trong DB.
4. Trả về `200 OK` kèm danh sách workshop.

### 2. Xem chi tiết workshop (Admin)

1. Client gửi `GET /api/workshops/{id}` với JWT role `ADMIN`.
2. Service tìm workshop theo `id`.
3. Nếu không tìm thấy → trả `404`.
4. Đọc `remaining_slots` từ Redis (fallback DB).
5. Trả về `200 OK` kèm chi tiết workshop.

### 3. Tạo workshop (Admin)

1. Client gửi `POST /api/workshops` với JWT role `ADMIN`.
2. Controller validate request body (Bean Validation):
   - `title`: not blank, max 255 ký tự.
   - `room_id`: not null, room tồn tại.
   - `speaker`: not blank.
   - `total_slots`: > 0.
   - `price`: >= 0.
   - `start_time`, `end_time`: not null.
   - `registration_start_time`, `registration_end_time`: not null.
3. Service validate thêm:
   - `end_time` phải sau `start_time`. (Phía frontend cũng nên có ràng buộc này khi người dùng lựa cho ở frontend)
   - `registration_start_time` phải trước `registration_end_time`.
   - `registration_start_time` phải trước `start_time`.
   - `total_slots <= rooms.capacity`.
4. Set `remaining_slots = total_slots`, `status = DRAFT` nếu không truyền, `price` default `0` nếu không truyền.
5. INSERT workshop vào DB.
6. Set Redis key: `SET workshop:{id}:slots {total_slots}`.
7. Trả về `201 Created` kèm workshop vừa tạo.

### 4. Cập nhật workshop (Admin)

1. Client gửi `PUT /api/workshops/{id}` với JWT role `ADMIN`.
2. Nếu `id` không tồn tại → trả `404`.
3. Cho phép cập nhật: `title`, `description`, `room_id`, `speaker`, `status`, `total_slots` (nếu chưa có đăng ký), `price`, `start_time`, `end_time`, `registration_start_time`, `registration_end_time`.
4. **Không cho phép** thay đổi `total_slots` nếu đã có bản ghi trong bảng `registrations` với `workshop_id` này.
5. Khi cập nhật `total_slots` hoặc `room_id`, phải đảm bảo `total_slots <= rooms.capacity`.
6. Nếu vi phạm rule trên → trả `409 Conflict`.
6. Trả về `200 OK` kèm workshop đã cập nhật.

### 5. Hủy workshop (Admin)

1. Client gửi `PUT /api/workshops/{id}/cancel` với JWT role `ADMIN`.
2. Nếu `id` không tồn tại → trả `404`.
3. Kiểm tra trạng thái hiện tại của workshop:
   - Nếu `status` đã là `COMPLETED` hoặc `CANCELLED` → trả `400 Bad Request` (Không thể hủy một workshop đã xong hoặc đã hủy).
4. Thực hiện Hủy:
   - Cập nhật `status = 'CANCELLED'` trong bảng `workshops`.
   - Các bản ghi trong `registrations` giữ nguyên (để làm lịch sử hoặc hỗ trợ hoàn tiền sau này nếu có).
   - Xóa key Redis `workshop:{id}:slots` để chặn tuyệt đối các request đăng ký mới đang in-flight.
   - Phải có thông báo xác nhận hoàn trả tiền đối với workshop có phí (@TODO ở tính năng hệ thống thông báo)
5. Trả về `200 OK` kèm thông báo đã hủy.

### 6. Xóa workshop (Admin)

1. Client gửi `DELETE /api/workshops/{id}` với JWT role `ADMIN`.
2. Nếu `id` không tồn tại → trả `404`.
3. **Chỉ cho phép xóa (Hard Delete)** nếu workshop có `status = DRAFT`. 
4. Nếu vi phạm (đã có người đăng ký) → trả `409 Conflict` kèm thông báo: "Chỉ được xóa Draft Workshop".
5. Khi xóa thành công:
   - `DELETE` record trong DB.
   - `DEL workshop:{id}:slots` trên Redis.
6. Trả về `204 No Content`.

### 7. Xem thống kê workshop (Admin)

1. Client gửi `GET /api/workshops/{id}/stats` với JWT role `ADMIN`.
2. Nếu `id` không tồn tại → trả `404`.
3. Service tính toán:
   - `total_slots`: từ DB.
   - `remaining_slots`: từ Redis key `workshop:slots:{id}` (realtime).
   - `registered_count`: `COUNT` từ bảng `registrations` có `workshop_id` này và `status = 'SUCCESS'`.
   - `fill_rate`: `(total_slots - remaining_slots) / total_slots * 100` (%).
4. Trả về `200 OK`.

---

## API Endpoints

| Method | Endpoint | Role | Mô tả |
|---|---|---|---|
| `GET` | `/api/workshops` | ADMIN | Lấy danh sách workshop |
| `GET` | `/api/workshops/{id}` | ADMIN | Lấy chi tiết workshop |
| `POST` | `/api/workshops` | ADMIN | Tạo workshop mới |
| `PUT` | `/api/workshops/{id}` | ADMIN | Cập nhật thông tin workshop |
| `PUT` | `/api/workshops/{id}/cancel` | ADMIN | Hủy workshop (Đổi status) |
| `DELETE` | `/api/workshops/{id}` | ADMIN | Xóa vĩnh viễn workshop |
| `GET` | `/api/workshops/{id}/stats` | ADMIN | Xem thống kê đăng ký (realtime) |

---

## Request / Response Schema

### POST `/api/workshops`

**Request Body (`application/json`):**
```json
{
  "title": "Workshop: Clean Code với Java",
  "description": null,
   "room_id": 1,
   "speaker": "Nguyen Van A",
   "status": "DRAFT",
  "total_slots": 60,
   "price": 0,
  "start_time": "2026-05-10T08:00:00",
   "end_time": "2026-05-10T12:00:00",
   "registration_start_time": "2026-05-05T08:00:00",
   "registration_end_time": "2026-05-10T07:30:00"
}
```

**Response `201`:**
```json
{
  "id": 1,
  "title": "Workshop: Clean Code với Java",
  "description": null,
   "room_id": 1,
   "speaker": "Nguyen Van A",
   "status": "DRAFT",
  "total_slots": 60,
   "remaining_slots": 60,
   "price": 0,
  "start_time": "2026-05-10T08:00:00",
   "end_time": "2026-05-10T12:00:00",
   "registration_start_time": "2026-05-05T08:00:00",
   "registration_end_time": "2026-05-10T07:30:00"
}
```

### GET `/api/workshops/{id}/stats`

**Response `200`:**
```json
{
  "workshop_id": 1,
  "title": "Workshop: Clean Code với Java",
  "total_slots": 60,
  "remaining_slots": 42,
  "registered_count": 18,
  "fill_rate": 30.0
}
```

---

## Kịch bản lỗi

| Tình huống | HTTP Status | Xử lý |
|---|---|---|
| `GET /api/workshops/{id}` — ID không tồn tại | `404` | Trả thông báo "Workshop not found" |
| `GET /api/workshops` — Không có JWT hoặc không phải Admin | `401` / `403` | Spring Security chặn |
| `POST /api/workshops` — Thiếu `title` hoặc `total_slots` | `400` | Bean Validation trả danh sách lỗi |
| `POST /api/workshops` — `end_time` trước `start_time` | `400` | Service throw `IllegalArgumentException` |
| `POST /api/workshops` — `total_slots = 0` hoặc âm | `400` | Bean Validation (`@Positive`) |
| `POST /api/workshops` — `total_slots > rooms.capacity` | `409` | Service throw `ConflictException` |
| `POST /api/workshops` — `room_id` không tồn tại | `404` | Service throw `ResourceNotFoundException` |
| `POST /api/workshops` — Student JWT | `403` | Spring Security chặn |
| `PUT /api/workshops/{id}` — Thay đổi `total_slots` khi đã có đăng ký | `409` | Service throw `ConflictException` |
| `DELETE /api/workshops/{id}` — Có đăng ký `status = 'SUCCESS'` | `409` | Service throw `ConflictException` |
| Redis không khả dụng khi đọc `remaining_slots` | — | Fallback về cột `remaining_slots` trong DB |

---

## Ràng buộc

- **Security:** Mọi endpoints (kể cả GET) đều yêu cầu `@PreAuthorize("hasRole('ADMIN')")` tại Controller. Tính năng này dành riêng cho Admin.
- **Redis:** Key pattern `workshop:{id}:slots`. Không đặt TTL (tồn tại vĩnh viễn đến khi xóa thủ công). Feature **Registration** (downstream) sẽ dùng Redis Lua Script để `DECR` atomic trên key này.
- **Validation:** `end_time > start_time` cần xử lý ở **Service layer** vì Bean Validation không hỗ trợ cross-field validation trên record.
- **Room capacity:** `total_slots <= rooms.capacity` bắt buộc ở Service layer.
- **Status:** Chỉ cho phép `DRAFT`, `PUBLISHED`, `CANCELLED`, `COMPLETED`.
- **Flyway:** Schema `workshops` đã tồn tại trong `V1__init_database.sql`. Seed data mẫu có trong `V2__seed_user_workshop.sql` (2 workshop mẫu). Migration tiếp theo nên đánh số **`V4__...`** (V3 đã được sử dụng cho feature Auth: `V3__modify_users_for_activation.sql`).

---

## Entity & DTO (Java)

### Entity `Workshop`

> **Convention:** Sử dụng Lombok (`@Getter`, `@Setter`, `@Builder`, ...) theo convention hiện tại của project — tham khảo `User.java`.

```java
@Entity
@Table(name = "workshops")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Workshop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;              // BIGSERIAL → Long

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;   // nullable

   @Column(name = "room_id", nullable = false)
   private Long roomId;

   @Column(nullable = false)
   private String speaker;

   @Column(nullable = false, length = 20)
   private String status; // DRAFT/PUBLISHED/CANCELLED/COMPLETED

    @Column(name = "total_slots", nullable = false)
    private Integer totalSlots;

    @Column(name = "remaining_slots", nullable = false)
    private Integer remainingSlots;

   @Column(columnDefinition = "BIGINT DEFAULT 0")
   @Builder.Default
   private Long price = 0L; // BIGINT DEFAULT 0 → Long

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
   private LocalDateTime endTime;

   @Column(name = "registration_start_time", nullable = false)
   private LocalDateTime registrationStartTime;

   @Column(name = "registration_end_time", nullable = false)
   private LocalDateTime registrationEndTime;
}
```

### DTO `WorkshopRequest`

```java
public record WorkshopRequest(
    @NotBlank @Size(max = 255)
    String title,

    String description,

   @NotNull
   Long roomId,

   @NotBlank @Size(max = 255)
   String speaker,

   @NotBlank
   String status,

    @NotNull @Positive
    Integer totalSlots,

   @NotNull @Min(0)
   Long price,

    @NotNull
    LocalDateTime startTime,

   @NotNull
   LocalDateTime endTime,

   @NotNull
   LocalDateTime registrationStartTime,

   @NotNull
   LocalDateTime registrationEndTime
) {}
```

> **Lưu ý:** Validation `end_time > start_time` cần được xử lý ở **Service layer** vì Bean Validation không hỗ trợ cross-field validation trên record. Service phải throw `IllegalArgumentException` nếu vi phạm.

---

## Tiêu chí chấp nhận

### Unit Tests (Service Layer)

| Test ID | Scenario | Expected |
|---|---|---|
| `WM-UT-01` | Tạo workshop hợp lệ | INSERT DB, `remaining_slots = total_slots`, Redis SET |
| `WM-UT-02` | Tạo với `end_time` trước `start_time` | Throw `IllegalArgumentException` |
| `WM-UT-03` | Tạo với `total_slots = 0` | Throw `ConstraintViolationException` |
| `WM-UT-04` | Xóa workshop có đăng ký `SUCCESS` | Throw `ConflictException` (409) |
| `WM-UT-05` | Xóa workshop không có đăng ký | DELETE DB, DEL Redis key |
| `WM-UT-06` | Cập nhật `total_slots` khi đã có đăng ký | Throw `ConflictException` |
| `WM-UT-07` | Lấy chi tiết workshop không tồn tại | Throw `ResourceNotFoundException` (404) |

### Integration Tests (Controller Layer)

| Test ID | Endpoint | Scenario | Status |
|---|---|---|---|
| `WM-IT-01` | `POST /api/workshops` | Admin JWT, body hợp lệ | `201` |
| `WM-IT-02` | `POST /api/workshops` | Student JWT | `403` |
| `WM-IT-03` | `POST /api/workshops` | Thiếu `title` | `400` |
| `WM-IT-04` | `GET /api/workshops` | Không có JWT | `401` |
| `WM-IT-08` | `GET /api/workshops` | Admin JWT | `200` |
| `WM-IT-05` | `DELETE /api/workshops/{id}` | Admin, có đăng ký SUCCESS | `409` |
| `WM-IT-06` | `GET /api/workshops/{id}` | ID không tồn tại | `404` |
| `WM-IT-07` | `GET /api/workshops/{id}/stats` | Admin JWT, ID hợp lệ | `200` |

---

## Dependencies & Blocking

| Dependency | Loại | Ghi chú |
|---|---|---|
| `V1__init_database.sql` |  Đã có | Bảng `workshops` đã tồn tại |
| `V2__seed_user_workshop.sql` |  Đã có | Dữ liệu mẫu sẵn có để test |
| Redis running | **Blocking** | Cần để set/get slots |
| Feature: Auth | **Blocking** | Cần JWT + RBAC cho write endpoints |
| Feature: AI Summary | **Non-blocking** | Xem spec `ai-summary.md` — cập nhật `description` bất đồng bộ |
| Feature: Registration | Downstream | Đọc `workshop:slots:{id}` do feature này init |
