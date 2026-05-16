# Đặc tả: AI Summary

## Mô tả

Ban tổ chức (Admin) có thể upload file PDF giới thiệu về workshop. Hệ thống tự động xử lý, tách nội dung, làm sạch văn bản và gửi sang mô hình AI (LLM) để tạo **bản tóm tắt** và **trích xuất tên diễn giả** (nếu có). Kết quả được lưu vào bảng `workshops`:
- `description` ← nội dung tóm tắt (luôn cập nhật nếu AI trả về)
- `speaker` ← tên diễn giả (chỉ cập nhật nếu AI tìm thấy trong PDF; nếu không có thì **giữ nguyên** giá trị cũ)

Tính năng này được thiết kế **hoàn toàn bất đồng bộ** và **non-blocking** — hệ thống chính (Spring Boot) không bị ảnh hưởng nếu AI service gặp sự cố.

**Kiến trúc tham chiếu từ `design.md`:**
- Spring Boot Core gọi FastAPI qua **REST (Async)** — không dùng Message Broker (ADR: giảm độ phức tạp vận hành).
- FastAPI chạy **độc lập** trong Docker internal network, xử lý PDF nặng không làm ngắt luồng chính.
- LLM Provider: Gemini / Groq / DeepSeek API.

---

## Luồng chính

### Luồng Upload PDF → AI tóm tắt

```
Admin POST /api/workshops/{id}/ai-summary (multipart/form-data)
           │
           ▼
[Controller]
   1. Kiểm tra workshop tồn tại (id) → 404 nếu không có
   2. Validate file: phải là PDF, tối đa 10MB → 413 nếu vượt
   3. Trả 202 Accepted ngay cho client
           │
           ▼  @Async (thread riêng, không block request thread)
[WorkshopAiService.generateSummaryAsync(workshopId, file)]
   4. Gọi POST http://ai-service:8000/summarize (multipart)
           │
      ┌────┴────┐
    OK (200)  ERROR (timeout / 4xx / 5xx)
      │          └─ Log WARNING, return (không throw, không ảnh hưởng hệ thống)
      ▼
[Spring Boot]
   5. UPDATE workshops SET description = '{summary}' WHERE id = {workshopId}
   6. Xóa file tạm (nếu có)
```

**Chi tiết từng bước:**

1. **Admin upload PDF:**
   - Client gửi `POST /api/workshops/{id}/ai-summary` với JWT role `ADMIN`.
   - Request body: `multipart/form-data`, field `file`.

2. **Controller validate ngay lập tức:**
   - Kiểm tra `workshop_id` tồn tại trong DB → `404` nếu không có.
   - Kiểm tra file type phải là `application/pdf` → `400` nếu sai định dạng.
   - Kiểm tra file size ≤ 10MB → `413 Payload Too Large` nếu vượt.

3. **Trả `202 Accepted` ngay:**
   - Response body: `{ "message": "AI summary đang được xử lý, description sẽ được cập nhật sớm." }`
   - Client **không cần chờ** kết quả AI.

4. **Xử lý bất đồng bộ (@Async):**
   - Spring Boot chuyển sang thread riêng.
   - Gửi file PDF sang FastAPI microservice qua REST: `POST http://ai-service:8000/summarize`.
   - Timeout gọi FastAPI: **30 giây**.

5. **FastAPI nhận và xử lý:**
   - Trích xuất text từ PDF (sử dụng thư viện xử lý PDF: `PyPDF2`, `pdfplumber`, ...).
   - Làm sạch văn bản: loại bỏ header/footer lặp, khoảng trắng thừa, ký tự đặc biệt.
   - Gọi LLM Provider (Gemini/Groq) với prompt **2-in-1** để tạo tóm tắt và trích xuất diễn giả cùng một lần.
   - Trả về `{ summary, speaker }` cho Spring Boot — `speaker` là `null` nếu PDF không đề cập.

6. **Spring Boot cập nhật DB:**
   - Nếu `summary` hợp lệ (non-null, non-blank) → `UPDATE workshops SET description = '{summary}' WHERE id = {workshopId}`.
   - Nếu `speaker` hợp lệ (non-null, non-blank) → `UPDATE workshops SET speaker = '{speaker}' WHERE id = {workshopId}`.
   - Nếu `speaker = null` → **không cập nhật** cột `speaker` (giữ nguyên giá trị Admin đã nhập).
   - Nếu FastAPI lỗi → log WARNING, giữ nguyên cả `description` lẫn `speaker`.

---

## API Endpoints

### Backend Core (Spring Boot)

| Method | Endpoint | Role | Mô tả |
|---|---|---|---|
| `POST` | `/api/workshops/{id}/ai-summary` | ADMIN | Upload PDF → trigger AI tóm tắt bất đồng bộ |

### AI Microservice (FastAPI — Docker Internal Only)

| Method | Endpoint | Mô tả |
|---|---|---|
| `POST` | `/summarize` | Nhận file PDF, trả về chuỗi summary |

> **Lưu ý:** FastAPI `/summarize` chỉ bind trên **Docker internal network**, không có route public. Không thể gọi từ bên ngoài hệ thống.

---

## Request / Response Schema

### POST `/api/workshops/{id}/ai-summary` (Spring Boot)

**Request:** `multipart/form-data`
- Field `file`: file PDF (≤ 10MB, content-type `application/pdf`)

**Response `202 Accepted`:**
```json
{
  "message": "AI summary đang được xử lý, description sẽ được cập nhật sớm."
}
```

### POST `/summarize` (FastAPI Internal)

**Request:** `multipart/form-data`
- Field `file`: PDF bytes
- Field `workshop_id`: Long — ID workshop cần tóm tắt

**Response `200 OK`:**
```json
{
  "workshop_id": 1,
  "summary": "Workshop tập trung vào các nguyên tắc Clean Code trong Java, bao gồm: đặt tên biến có ý nghĩa, viết hàm ngắn gọn, xử lý lỗi đúng cách, và các kỹ thuật refactoring phổ biến.",
  "speaker": "Nguyen Van A"
}
```

> **Lưu ý:** Nếu PDF không đề cập đến diễn giả, field `speaker` trả về `null` — Spring Boot sẽ **không ghi đè** cột `speaker` trong DB.

**Response `400 Bad Request` (PDF không đọc được):**
```json
{
  "error": "Không thể trích xuất nội dung từ file PDF."
}
```

---

## Kịch bản lỗi

| Tình huống | Xử lý phía Spring Boot | Ảnh hưởng hệ thống |
|---|---|---|
| File > 10MB | Reject ngay tại Controller, trả `413` | Không — request bị chặn trước khi vào async |
| File không phải PDF | Reject ngay tại Controller, trả `400` | Không |
| `workshop_id` không tồn tại | Trả `404` ngay tại Controller | Không |
| FastAPI timeout > 30s | Log WARNING, giữ `description` cũ | **Không** — hệ thống chính vẫn chạy bình thường |
| FastAPI trả lỗi 4xx/5xx | Log WARNING, giữ `description` cũ | **Không** — `@Async` thread riêng, không ảnh hưởng request thread |
| LLM API rate limit (429) | FastAPI trả lỗi → Spring Boot log, bỏ qua | **Không** |
| LLM API internal error (500) | FastAPI trả lỗi → Spring Boot log, bỏ qua | **Không** |
| PDF không chứa text (scan/ảnh) | FastAPI trả `400` → Spring Boot log, bỏ qua | **Không** |
| FastAPI hoàn toàn down | `@Async` thread catch exception, log ERROR | **Không** — đây là thiết kế **non-blocking** |

> **Nguyên tắc cốt lõi:** Mọi lỗi từ AI service đều được **nuốt** (catch) ở tầng async — KHÔNG BAO GIỜ propagate exception lên làm ảnh hưởng hệ thống Core. Đây là yêu cầu từ `design.md`: *"Service xử lý AI chạy độc lập để xử lý các file PDF nặng không làm ngắt luồng chính"*.

---

## Ràng buộc

- **Bất đồng bộ bắt buộc:** Sử dụng `@Async` của Spring Boot. Không được gọi FastAPI đồng bộ trong request thread.
- **Timeout:** Gọi FastAPI tối đa 30 giây. Quá hạn → log và bỏ qua.
- **File size:** Giới hạn 10MB — validate tại tầng Controller bằng Spring `MultipartFile.getSize()`.
- **File type:** Chỉ chấp nhận `application/pdf` — validate MIME type.
- **Security:** Endpoint `POST /api/workshops/{id}/ai-summary` yêu cầu JWT với role `ADMIN`. FastAPI chỉ accessible từ Docker internal network.
- **Non-blocking:** Core system (Spring Boot + PostgreSQL + Redis) phải vẫn hoạt động 100% khi FastAPI down.
- **Idempotency:** Upload lại PDF cho cùng workshop → ghi đè `description` mới, không tạo bản ghi trùng.

---

## Thiết kế kỹ thuật

### FastAPI Microservice

```
ai-service/
├── main.py              # FastAPI app entry point
├── routers/
│   └── summarize.py     # POST /summarize endpoint
├── services/
│   ├── pdf_extractor.py # Trích xuất text từ PDF
│   ├── text_cleaner.py  # Làm sạch văn bản
│   └── llm_client.py    # Gọi LLM API (Gemini/Groq)
├── requirements.txt
└── Dockerfile
```

### Luồng xử lý trong FastAPI

1. **Nhận file PDF** từ request multipart.
2. **Trích xuất text** — sử dụng `PyPDF2` hoặc `pdfplumber`:
   - Đọc từng trang, nối text lại.
   - Nếu không có text (PDF scan/ảnh) → trả `400`.
3. **Làm sạch văn bản:**
   - Loại bỏ header/footer lặp.
   - Chuẩn hóa khoảng trắng, xuống dòng.
   - Cắt nếu text quá dài (giới hạn context window của LLM).
4. **Gọi LLM API (prompt 2-in-1):**
   - Gửi một prompt duy nhất yêu cầu LLM trả về **JSON** với 2 trường `summary` và `speaker`.
   - Quy tắc trong prompt cho trường `speaker`:
     - **Chỉ điền** nếu tài liệu **rõ ràng ghi nhãn** người trình bày (ví dụ: `Diễn giả:`, `Speaker:`, `Presented by:`, `Báo cáo viên:`, ...).
     - **Không điền** nếu tên người chỉ xuất hiện trong tài liệu tham khảo, trích dẫn, ví dụ minh họa, danh sách người tham dự, hoặc lời cảm ơn.
     - Nếu không xác định được rõ ràng → trả `null`.
   - LLM Provider: cấu hình qua biến môi trường (`LLM_PROVIDER`, `LLM_API_KEY`).
5. **Trả kết quả** về Spring Boot.

### Spring Boot side — `WorkshopAiService`

```java
@Service
public class WorkshopAiService {

    @Async
    @Transactional
    public void generateSummaryAsync(Long workshopId, byte[] fileBytes, String filename) {
        try {
            // 1. Gọi FastAPI
            AiSummaryResponse aiResp = callFastApiSummarize(workshopId, fileBytes, filename);

            // 2. Cập nhật description (luôn cập nhật nếu có)
            if (aiResp.getSummary() != null && !aiResp.getSummary().isBlank()) {
                workshopRepository.updateDescription(workshopId, aiResp.getSummary());
            }

            // 3. Cập nhật speaker CHỈ KHI AI tìm thấy trong PDF
            if (aiResp.getSpeaker() != null && !aiResp.getSpeaker().isBlank()) {
                workshopRepository.findById(workshopId).ifPresent(w -> {
                    w.setSpeaker(aiResp.getSpeaker());
                    workshopRepository.save(w);
                });
            }
            // Nếu speaker = null → KHÔNG ghi đè, giữ nguyên giá trị Admin đã nhập

        } catch (Exception e) {
            // Nuốt mọi exception — KHÔNG throw ra ngoài
            log.warn("AI Summary failed for workshop {}: {}", workshopId, e.getMessage());
        }
    }
}
```

### Docker Compose — Network isolation

```yaml
services:
  ai-service:
    build: ./ai-service
    networks:
      - internal
    # KHÔNG expose port ra ngoài host
    environment:
      - LLM_PROVIDER=gemini
      - LLM_API_KEY=${LLM_API_KEY}

  backend:
    build: ./backend
    networks:
      - internal
      - public
    environment:
      - AI_SERVICE_URL=http://ai-service:8000

networks:
  internal:
    internal: true    # Không accessible từ bên ngoài
  public:
```

---

## Tiêu chí chấp nhận

### Unit Tests (Service Layer)

| Test ID | Scenario | Expected |
|---|---|---|
| `AI-UT-01` | Upload PDF hợp lệ, FastAPI trả `summary` + `speaker` thành công | `description` và `speaker` đều được cập nhật trong DB |
| `AI-UT-02` | FastAPI trả `summary` nhưng `speaker = null` | `description` cập nhật, `speaker` **không thay đổi** |
| `AI-UT-03` | FastAPI timeout (> 30s) | Log WARNING, `description` + `speaker` không đổi, không throw |
| `AI-UT-04` | FastAPI trả lỗi 500 | Log WARNING, không thay đổi DB, không throw |
| `AI-UT-05` | FastAPI hoàn toàn down (connection refused) | Log ERROR, không throw |
| `AI-UT-06` | Upload lại PDF cho cùng workshop | `description` bị ghi đè bằng summary mới; `speaker` cập nhật nếu AI tìm thấy |

### Integration Tests (Controller Layer)

| Test ID | Endpoint | Scenario | Status |
|---|---|---|---|
| `AI-IT-01` | `POST /api/workshops/{id}/ai-summary` | Admin JWT, PDF ≤ 10MB | `202` |
| `AI-IT-02` | `POST /api/workshops/{id}/ai-summary` | Student JWT | `403` |
| `AI-IT-03` | `POST /api/workshops/{id}/ai-summary` | PDF > 10MB | `413` |
| `AI-IT-04` | `POST /api/workshops/{id}/ai-summary` | File không phải PDF | `400` |
| `AI-IT-05` | `POST /api/workshops/{id}/ai-summary` | Workshop ID không tồn tại | `404` |
| `AI-IT-06` | `POST /api/workshops/{id}/ai-summary` | Không có JWT | `401` |

### FastAPI Tests

| Test ID | Scenario | Expected |
|---|---|---|
| `AI-FT-01` | PDF có text → gọi LLM thành công | Trả `200` kèm `summary` |
| `AI-FT-02` | PDF không chứa text (scan/ảnh) | Trả `400` kèm thông báo lỗi |
| `AI-FT-03` | LLM API lỗi 429 (rate limit) | Trả `503` hoặc `500` |
| `AI-FT-04` | LLM API lỗi 500 | Trả `500` |

---

## Dependencies & Blocking

| Dependency | Loại | Ghi chú |
|---|---|---|
| Feature: Workshop Management | **Blocking** | Cần bảng `workshops` và CRUD endpoints |
| Feature: Auth | **Blocking** | Cần JWT + RBAC để bảo vệ endpoint upload |
| FastAPI running | **Non-blocking** | Core vẫn chạy nếu FastAPI down |
| LLM API Key | **Blocking** | Cần API key hợp lệ (Gemini/Groq) để test AI |
| Docker Compose | Setup | Cần để chạy FastAPI trên internal network |
