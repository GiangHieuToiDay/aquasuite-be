# WORK-017 — Import Category từ Excel

## Metadata

- **Work ID**: WORK-017
- **Owner module**: catalog
- **Risk**: L — HTTP contract, authorization, concurrent hierarchy writes
- **Design Review**: Theo trạng thái Delivery Brief `.agent/work/WORK-017.md`.
- **Decision owner**: Product Owner / Tech Lead / Security Owner

## 1. Mục tiêu và phạm vi

Nhập Category mới từ XLSX, tái sử dụng kỹ thuật import Brand phù hợp. Chỉ tạo mới, không cập nhật, khôi phục hoặc di chuyển Category có sẵn. Không thay đổi contract/behavior Brand, Product, quyền hiện có hoặc schema. Category cha phải có trong database trước khi job được chấp nhận; dữ liệu vừa tạo trong job không dùng làm cha trong cùng job.

## 2. Người dùng và bảo mật

Mọi endpoint yêu cầu `CATALOG_CATEGORY_CREATE`. Owner UUID lấy từ IAM public API, không lấy từ client. Mỗi lần đọc progress/report kiểm tra lại quyền hiện tại và owner; job không tồn tại, hết hạn hoặc owner khác trả cùng 404. Redis lỗi khi kiểm tra owner phải fail closed. Không log nội dung file hoặc secret. Báo cáo ghi dữ liệu người dùng bằng cell text, không tạo formula.

## 3. Quy tắc nghiệp vụ

1. Sheet đầu, header dòng 1 theo thứ tự `code,name,parent_code,description,status`; dữ liệu từ dòng 2. Bỏ qua dòng hoàn toàn trống; validate cell, header và giới hạn như Brand import hiện tại, tối đa 5 MiB và 10.000 dòng.
2. Code trim, uppercase Locale.ROOT, regex `^[A-Z0-9][A-Z0-9_]*$`, 1–50 ký tự. Name bắt buộc, trim, tối đa 100; description nullable, trim, tối đa 5000; status rỗng mặc định ACTIVE, chỉ ACTIVE/INACTIVE. Xử lý validation trước/sau trim tương đương `CreateCategoryRequest` và service hiện tại.
3. `parent_code` rỗng tạo root; nếu có, chuẩn hóa như code và tra trong snapshot mã/ID các Category tồn tại tại mốc chốt tại §6. Category INACTIVE được phép làm cha, phù hợp CRUD hiện tại. Cha không có trong snapshot => `ERROR/PARENT_NOT_FOUND`, kể cả được tạo sau mốc chốt hoặc nằm trong cùng file.
4. Snapshot hoàn tất trước dispatch và response 202, gồm tất cả status và chỉ code/ID tối thiểu. Không dispatch snapshot từng phần. Khi insert, lấy category advisory transaction lock như CRUD, xác nhận parent ID/code còn tồn tại và ancestry không corrupt; nếu không thì báo lỗi phù hợp. Không tự tạo hoặc khôi phục cha.
5. Code duy nhất toàn hệ thống; tên duy nhất trong cùng parent, bao gồm root và INACTIVE. So sánh theo PostgreSQL index/collation hiện có. Quy tắc thứ tự và giữ khóa trong file tại bảng dưới đây. Một dòng chỉ tăng một counter.
6. Không thay đổi code hoặc dữ liệu đã có. Một dòng lỗi không ngăn các dòng hợp lệ, ngoại trừ lỗi hệ thống làm job FAILED. Ưu tiên tính đúng nghiệp vụ và báo cáo theo số dòng Excel.

### Quy tắc trùng trong file

Xử lý theo thứ tự dòng Excel. Khóa code là code đã chuẩn hóa; khóa name là `(parentId, name theo semantics index PostgreSQL)`, với root là một parent đặc biệt. Tên giống nhau dưới hai parent khác nhau là hai khóa. `firstCodeRow` và `firstNameRow` là dòng hợp lệ đầu tiên đã giữ từng khóa; có thể khác nhau.

| Kết quả dòng hiện tại | Có giữ khóa code/name chưa xuất hiện? | Lý do |
|---|---|---|
| `ERROR`: sai cell/validation hoặc parent không hợp lệ | Không giữ khóa nào | Không ảnh hưởng dòng hợp lệ phía sau |
| Hợp lệ nhưng trùng một hoặc cả hai khóa file | Giữ cả hai khóa chưa xuất hiện | `SKIPPED`, ưu tiên lý do file; ghi các tham chiếu code/name riêng nếu trùng |
| Hợp lệ, không trùng file nhưng trùng DB | Giữ cả hai khóa | `SKIPPED` với lý do DB có bằng chứng; dòng sau trùng được báo theo file |
| Hợp lệ, insert thành công | Giữ cả hai khóa | `CREATED` |
| Lỗi hệ thống hoặc rollback không xác định | Không công bố kết quả dòng | Job `FAILED`, dừng xử lý |

Không thay số dòng đầu đã giữ khóa. Một dòng trùng cả code và name với hai dòng trước khác nhau hiển thị cả hai lý do/tham chiếu, nhưng chỉ tăng `skippedRows` một lần. Test phải bao phủ root, hai parent khác nhau, skip do trùng code nhưng tên mới, skip do DB và dòng ERROR trước dòng hợp lệ.

## 4. Dữ liệu và migration

Không thay đổi schema. Sử dụng bảng categories và indexes `uq_categories_code_lower`, `uq_categories_root_name_lower`, `uq_categories_parent_name_lower`; FK và check hiện hữu là chốt toàn vẹn. Parent relationship thuộc cùng module. Job tạm và owner lưu Redis namespace riêng; TTL một giờ tính từ lần cập nhật gần nhất như Brand.

## 5. HTTP contract đề xuất

| Method/Path | Quyền | Kết quả |
|---|---|---|
| `GET /api/v1/catalog/categories/import-template` | CREATE | XLSX header mẫu |
| `POST /api/v1/catalog/categories/import-jobs` | CREATE | Multipart `file`; 202 với jobId/status sau khi job, file tạm và snapshot được chấp nhận |
| `GET /api/v1/catalog/categories/import-jobs/{jobId}` | CREATE + owner | Progress JSON `ApiResponse` |
| `GET /api/v1/catalog/categories/import-jobs/{jobId}/error-report` | CREATE + owner | XLSX report khi job terminal; 409 khi chưa terminal |

File rỗng/extension sai: 400; vượt 5 MiB: 413; unauthenticated 401, thiếu quyền 403, job không tồn tại/không thuộc owner 404, storage không khả dụng 503. Multipart bị từ chối trước controller theo handler chung hiện tại. File sai header/hỏng sau 202 chuyển job FAILED. Progress gồm total, processed, created, skipped, error, percentage/status; report có số dòng, code, parent_code, kết quả và lý do, không chứa description. Không bổ sung Java API/event liên module.

## 6. Transaction và an toàn lỗi

Xử lý tuần tự; mỗi dòng ghi trong transaction độc lập. Dùng đúng `pg_advisory_xact_lock(1128354892, 1)` trước đọc/kiểm tra/mutation Category, giữ lock tới commit. Unique indexes là chốt cuối khi concurrent CRUD/import; unique violation rollback transaction trước khi phân loại theo constraint xác định được, không đoán nguyên nhân. Chỉ tăng created sau khi commit; Redis và PostgreSQL không atomic, vì vậy snapshot progress có thể thiếu/mất/treo khi server hoặc Redis lỗi. Không tự replay; các Category đã commit vẫn giữ, người dùng kiểm tra trước khi import lại. Dọn file tạm best effort. Không hứa recovery đa instance hoặc trạng thái bền vững qua restart.

### Mốc chốt snapshot và nhận job

1. Kiểm tra permission, owner, file rỗng/extension/5 MiB; lưu file tạm. Chưa tạo job hoặc ghi Category nếu các bước này thất bại.
2. Trong transaction PostgreSQL `READ COMMITTED`, lấy advisory lock dùng chung với CRUD trước, sau đó đọc toàn bộ `(category_code, category_id)` của mọi status bằng **một câu SELECT**. Mốc chốt là snapshot MVCC của câu SELECT đó, không phải thời điểm trả HTTP; cha commit sau mốc này không hợp lệ cho job dù đã tồn tại trước HTTP 202. Chỉ công bố snapshot hoàn chỉnh sau khi transaction đọc thành công và lock được giải phóng.
3. Tạo job `QUEUED` cùng owner trong Redis; truyền snapshot bất biến hoàn chỉnh và file tạm trực tiếp cho một worker trong cùng instance. Snapshot không là nguồn dữ liệu bền vững; không dispatch khi chỉ có một phần. Nếu chụp snapshot hoặc lưu job thất bại, dọn file tạm best effort, không dispatch và trả 503; không trả 202.
4. Nếu executor từ chối sau khi lưu job, thử ghi `FAILED/PROCESSING_FAILED`, dọn file tạm và trả 202 với jobId đã tạo. Nếu Redis không thể ghi trạng thái thất bại, job có thể còn `QUEUED` đến TTL; đây là giới hạn Redis tạm đã chấp nhận. Worker được dispatch chỉ một lần, không tự replay.
5. Ở mỗi transaction dòng, giữ advisory lock từ trước các bước kiểm tra parent/ancestry/unique đến commit. Cần test race tạo parent ngay trước/sau mốc SELECT, sửa hoặc xóa cha sau snapshot, job/dispatch thất bại và snapshot không hoàn chỉnh. Nếu parent snapshot không còn tồn tại hoặc hierarchy corrupt, báo lỗi dòng phù hợp; không thay bằng category khác cùng code.

### Kiểm tra XLSX và giới hạn đã chấp nhận

Theo quyết định của người dùng, phiên bản này kiểm tra file rỗng/extension và kích thước 5 MiB trước khi nhận; sau đó đọc bằng cơ chế tương tự Brand, kiểm tra header và tối đa 10.000 dòng không trống trước khi insert. Workbook không đọc được => `FAILED/INVALID_FILE`; vượt số dòng => `FAILED/ROW_LIMIT`; sai header => `FAILED/INVALID_HEADER`. Kiểm thử file hợp lệ, sai header, file hỏng và giới hạn được hỗ trợ.

Không triển khai riêng cho Category bộ quét ZIP/XML, giới hạn bytes giải nén/entries/cells hoặc cam kết chống zip bomb trong WORK-017. Mức file nén 5 MiB và giới hạn dòng **không bảo đảm** mức cấp phát bộ nhớ, đặc biệt với XLSX nén cao hoặc shared strings lớn; chưa có bằng chứng an toàn với file độc hại. Tăng bảo vệ Excel cho Brand, Category và Product được để cho công việc dùng chung sau, không coi là đã thực hiện. Cần đánh giá rủi ro này trước khi release và kiểm soát đối tượng được phép upload.

## 7. Acceptance criteria và verification

| AC | Kết quả quan sát | Kiểm thử |
|---|---|---|
| 01 | File mẫu, header, 5 MiB/10.000 dòng, job trước parse | HTTP/parser, file hỏng |
| 02 | Validation, status, normalization như CRUD | Unit và PostgreSQL |
| 03 | Root/cha đã tồn tại, kể cả INACTIVE; cha cùng file hoặc tạo sau snapshot bị lỗi | PostgreSQL + đồng bộ job |
| 04 | Code toàn cục; tên duy nhất cùng cha/root, cả trong file và DB | PostgreSQL, Unicode/collation của môi trường test |
| 05 | Advisory lock, rollback, concurrent create/rename/delete; counter sau commit | PostgreSQL concurrency và failure injection |
| 06 | Permission/owner, report và Redis failure | Security + Redis |
| 07 | Brand/Product không bị regression | Targeted regression, module boundary |
| 08 | Full suite và diff | Ghi kết quả thực tế, không đánh dấu PASS khi chưa chạy |

## 8. Quyết định và giới hạn

Người dùng chọn phương án A: cha có trước job; không tự tạo cha cùng file. Người dùng cũng chọn mức kiểm tra XLSX như Brand và hoãn tăng bảo vệ Excel dùng chung cho công việc sau. Đây là thay đổi đối với phần an toàn của thiết kế đã review; cần review lại phần thay đổi trước implementation. Redis tạm, proxy và topology có giới hạn nêu trên; không khẳng định file nén bất thường được xử lý an toàn.
