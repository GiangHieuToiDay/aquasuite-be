# WORK-015 — Import Brands from Excel

## Metadata

- **Work ID**: `WORK-015`
- **Feature / Use Case**: Import Brands from Excel
- **Version**: `0.1.0`
- **Owner**: Catalog team
- **Owning Module**: `catalog`
- **Risk Reasons**: public HTTP contract, authorization, concurrency và data integrity
- **Design Review**: Trạng thái tại Delivery Brief là nguồn duy nhất; cần review lại theo phạm vi mới.
- **Decision Owner**: Product Owner / Tech Lead / Security Owner

## 1. Goal and Scope

Nhập nhiều Brand mới từ `.xlsx`, không cập nhật hoặc ghi đè. Người dùng nhận jobId để xem tiến độ và báo cáo tạm thời; trong điều kiện bình thường các dòng đã xử lý được phân loại thành công, bỏ qua hoặc lỗi. Work item độc lập với WORK-013; Brand `INACTIVE` vẫn giữ code/name.

**In scope:** template, upload bất đồng bộ, progress, report, quyền và ownership; tái sử dụng hạ tầng Excel kỹ thuật hiện tại khi phù hợp. Cấu hình multipart chung và tác động đến Product/avatar được mô tả ở §5. **Out of scope:** tạo Category, Product, SKU, inventory hoặc event; khôi phục Brand; PostgreSQL job/result, hàng đợi bền vững, lease/fencing, recovery đa instance và tự replay. Không sửa WORK-013. Không đưa business logic Brand vào `share`.

## 2. Actors and Security

| Actor | Allowed Operations | Required Permission | Data Restrictions |
|---|---|---|---|
| Người tạo Brand | Tải template, upload | `CATALOG_BRAND_CREATE` | Owner UUID lấy từ authenticated principal, không nhận từ client |
| Chủ job | Xem progress, tải report | `CATALOG_BRAND_CREATE` còn hiệu lực và UUID khớp owner đã lưu | Không có admin override hoặc truy cập job khác |

Mỗi request kiểm tra lại quyền và owner; jobId không là credential. Chưa xác thực trả 401, thiếu quyền 403; job không tồn tại, hết hạn hoặc không thuộc actor trả cùng 404. Nếu Redis không đọc được, trả 503, không bỏ qua kiểm tra owner. Redis key Brand có namespace riêng, owner cùng metadata phải được lưu khi khởi tạo; Redis phải có kiểm soát truy cập theo cấu hình triển khai. Không lộ token, tên file gốc, description, nội dung file hoặc exception thô trong log; report ghi giá trị dưới dạng text để tránh formula injection.

## 3. Business Rules

1. Sheet đầu, header dòng 1 gồm đúng `code`, `name`, `description`, `status` theo thứ tự, so sánh sau trim không phân biệt hoa thường; thiếu, trùng, sai thứ tự hoặc có header thừa không rỗng là lỗi job. Dữ liệu từ dòng 2; bỏ qua dòng hoàn toàn trống; ô ngoài bốn cột có dữ liệu là lỗi dòng. Code cần được nhập dạng text để giữ số 0 đầu; ô formula, lỗi hoặc không phải text được báo `INVALID_CELL`. Khả năng nhận diện của FastExcel phải được chứng minh bằng test; nếu không thể phân biệt chính xác thì điều chỉnh parser trước release, không âm thầm nhận sai.
2. Áp dụng tương đương create CRUD: raw name tối đa 100 và raw description tối đa 5000 ký tự Java UTF-16 trước trim, `@NotBlank` cho code/name; service dùng `String.trim()`, code uppercase `Locale.ROOT`, dài 1–50, khớp `^[A-Z0-9][A-Z0-9_]*$`; name trim bắt buộc tối đa 100; description trim, blank thành null, tối đa 5000; status trim, blank mặc định `ACTIVE`, chỉ chấp nhận `ACTIVE` hoặc `INACTIVE` đúng hoa thường.
3. Dòng validation lỗi là `ERROR`. Dòng hợp lệ trùng code hoặc name với Brand bất kỳ, kể cả `INACTIVE`, là `SKIPPED`. Trùng trong file theo thứ tự dòng, chỉ dòng hợp lệ chiếm khóa, kể cả dòng đầu đã skip do DB; dòng lỗi không chiếm khóa. Ưu tiên lý do trùng file nếu trùng cả file và DB. Ghi riêng dòng đầu trùng code/name nếu chúng khác nhau. So sánh theo expression/collation của PostgreSQL unique indexes, không giả định Java lowercase tương đương; test Unicode và whitespace trên PostgreSQL thực tế.
4. File tối đa **5 MiB = 5.242.880 bytes**. Giới hạn thiết kế ban đầu **10.000 dòng dữ liệu không trống**, quét header và đếm trước khi insert; vượt giới hạn hoặc không có dòng là `FAILED` nếu Redis còn khả dụng. Đây là giới hạn ứng dụng cần test năng lực, không chứng minh giới hạn bộ nhớ parser: workbook có thể chứa shared strings lớn hoặc nén rất cao trước khi đếm dòng.
5. FastExcel `ReadableWorkbook` dùng sheet đầu và stream row trong importer hiện tại; chưa có bằng chứng kiểm soát tổng bytes giải nén, shared strings, XML hoặc zip bomb. Không khẳng định bảo đảm 50 MiB giải nén. Workbook hỏng, không mở/đọc được hoặc mã hóa mà thư viện không đọc được: bắt lỗi parser, ngừng job, cố cập nhật `FAILED / INVALID_FILE`, không lộ exception. Lỗi phát sinh trong pass xử lý sau scan có thể xảy ra sau một số commit. Trước release phải thử file hỏng, ZIP bất thường, dữ liệu nén cao, shared strings lớn và đo tài nguyên; Tech Lead chọn biện pháp và giới hạn có thể enforce được, hoặc chặn release nếu không kiểm soát rủi ro.

| From | Action | To | Conditions/Invariants |
|---|---|---|---|
| `QUEUED` | Worker bắt đầu | `PROCESSING` | Job và owner đã lưu trong Redis trước parse |
| `QUEUED` / `PROCESSING` | Dispatch, parse hoặc xử lý thất bại | `FAILED` | Best effort khi Redis khả dụng; không bảo đảm chuyển trạng thái khi crash/outage |
| `PROCESSING` | Xử lý xong | `COMPLETED` / `COMPLETED_WITH_ERRORS` | Theo số skip/error được lưu trong điều kiện bình thường |

## 4. Data and Migration Decisions

- Không có bảng job/result, migration hay retention bền vững mới. Brand được lưu vào bảng hiện có; `uq_brands_code_lower` và `uq_brands_name_lower` bảo vệ uniqueness bao gồm INACTIVE. Không sửa index hoặc dữ liệu Brand hiện hữu.
- JobId, owner UUID, trạng thái, tổng, counters và thông tin report cần thiết được lưu tạm Redis với TTL **1 giờ**, làm mới khi cập nhật như tracker hiện tại; sau hết hạn trả 404. Không lưu description hoặc toàn bộ file trong Redis. Không bảo đảm job sống đủ một giờ sau lần cập nhật cuối nếu Redis mất dữ liệu/eviction; long-running job có thể hết hạn khi không có cập nhật. Không coi Redis là nguồn sự thật về số Brand đã commit.

## 5. Contracts

### HTTP API

JSON dùng `ApiResponse<T>`; file trả XLSX cùng `Content-Disposition: attachment`. Các endpoint là thiết kế chờ review.

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| `GET /api/v1/brands/import-template` | CREATE | Không có | `200` XLSX | `401`, `403` |
| `POST /api/v1/brands/import-jobs` | CREATE | multipart `file`, `.xlsx`, không rỗng, <= 5 MiB | `202` data `{jobId,status}` sau staging và lưu job Redis, trước parse | `400` thiếu/rỗng/sai extension; `413` vượt giới hạn; `503` staging/Redis không sẵn sàng trước chấp nhận |
| `GET /api/v1/brands/import-jobs/{jobId}` | CREATE + owner | UUID | `200` data `jobId,status,totalRows,processedRows,createdRows,skippedRows,errorRows,percentage,message,startedAt,finishedAt` | `400`, `401`, `403`, `404`, `503` |
| `GET /api/v1/brands/import-jobs/{jobId}/error-report` | CREATE + owner | UUID | `200` XLSX snapshot terminal, kể cả khi rỗng | `400`, `401`, `403`, `404`, `409` chưa terminal, `503` Redis lỗi |

- `totalRows` và `percentage` null trước khi scan xong. Trong điều kiện bình thường `processedRows = createdRows + skippedRows + errorRows`; percentage là floor `100 * processedRows / totalRows`, null nếu chưa biết total. `FAILED` không ngụ ý 100%. Job chỉ cung cấp snapshot Redis; nếu server/Redis gặp sự cố, snapshot có thể thiếu, mất hoặc kẹt tại `QUEUED`/`PROCESSING`, kể cả sau DB commit. Khi còn truy cập được, client phải xem status và message, không coi snapshot là đối soát bền vững.
- Tạo job `QUEUED` kèm owner **trước mọi đọc workbook/header**; staging thành công và lưu Redis thành công trước 202. Dispatch bị từ chối sau lưu job: cố đánh dấu `FAILED`, trả 202 với jobId; nếu Redis không cập nhật được có thể còn `QUEUED`. Nếu không thể lưu job trước chấp nhận thì xóa staging best effort và trả 503, không trả jobId. Lỗi multipart trước controller không tạo job.
- Mã JSON: `400/SYS_002` request/UUID sai; `401/AUTH_001`; `403/AUTH_002`; `404/SYS_004`; `409/BRIMP_001` report chưa terminal; `413/BRIMP_002` Brand vượt 5 MiB; `413/SYS_005` multipart chung vượt giới hạn trước controller; `503/BRIMP_003` Redis/staging không khả dụng; `500/SYS_001` lỗi khác. `SYS_005` và `BRIMP_*` là mã cần bổ sung; proxy từ chối trước ứng dụng có thể không dùng envelope.
- HTTP **413**: handler multipart chung trả 413 trung tính; avatar quá 5 MiB đến được service trả `413/MSG17`, loại file/rỗng giữ contract hiện hành. Cấu hình repository đặt `max-file-size=6MB`, `max-request-size=7MB`; người dùng chấp nhận Product có thể nhận file lớn hơn trước tùy override deployment, cùng thay đổi 413. Brand vẫn giới hạn nghiệp vụ 5 MiB; giới hạn nghiệp vụ avatar không đổi. Mặc định Spring Boot trước cấu hình là 1 MB/file, 10 MB/request, không chứng minh giá trị đã có hiệu lực tại từng deployment. Operations xác minh override Spring/proxy và overhead, kiểm thử Brand, avatar và Product qua container/proxy trước release.

### Report Contract

- Report gồm `Summary` (`jobId`, `status`, `jobReason`, `totalRows`, `processedRows`, `createdRows`, `skippedRows`, `errorRows`) và `Rows` (`rowNumber`, `code`, `result`, `reasonCodes`, `firstDuplicateCodeRow`, `firstDuplicateNameRow`). `Rows` chứa snapshot các dòng `SKIPPED`/`ERROR` đã ghi nhận, theo rowNumber 1-based tăng dần; các dòng thành công được biểu diễn bằng counter. Header luôn có kể cả không lỗi. Hai tham chiếu duplicate nullable và độc lập. Không có description; ô text không là formula.
- Mã dòng: `FILE_DUPLICATE_CODE`, `FILE_DUPLICATE_NAME`, `DB_DUPLICATE_CODE`, `DB_DUPLICATE_NAME`, `INVALID_CODE`, `INVALID_NAME`, `INVALID_DESCRIPTION`, `INVALID_STATUS`, `INVALID_CELL`, `EXTRA_CELL`; nhiều mã phân cách bằng dấu phẩy theo thứ tự này, một dòng chỉ tăng một counter. Lỗi job: `INVALID_FILE`, `INVALID_HEADER`, `EMPTY_DATA`, `ROW_LIMIT`, `PROCESSING_FAILED`. Không hứa danh sách đầy đủ dòng chưa xử lý hay report sau outage; nếu total đã biết, `totalRows - processedRows` chỉ là số chưa được ghi nhận trong snapshot, không khẳng định chưa commit.

### Inter-Module Java API and Domain Event

Không thêm Java API hoặc event liên module; nếu cần identity liên module, dùng public contract IAM, không import internal module khác.

## 6. Concurrency, Transaction and Failure Safety

- Sau scan, xử lý theo thứ tự file; đề xuất transaction DB độc lập cho mỗi dòng hợp lệ. Chỉ tăng `createdRows` trong Redis **sau khi commit DB thành công**, không đếm dòng rollback. Validation/duplicate được ghi nhận sau khi phân loại. Không thể commit Redis và PostgreSQL nguyên tử: crash hoặc lỗi Redis giữa commit và update làm thiếu counter/report; không được tuyên bố số liệu đầy đủ sau sự cố. Lỗi Redis khi worker đang chạy phải dừng tiếp tục xử lý, đóng file và dọn best effort; trạng thái FAILED chỉ có thể cập nhật nếu Redis hồi phục, không có cơ chế recovery tự động.
- Precheck chỉ tối ưu; unique indexes là chốt cuối khi CRUD hoặc job khác đồng thời ghi. Khi unique violation, rollback transaction dòng, lấy constraint name có cấu trúc từ exception và ghi `SKIPPED` với mã tương ứng; không suy đoán từ đọc lại DB vì Brand có thể đổi tên đồng thời. Nếu không xác định constraint hoặc lỗi DB khác, dừng job và cố đánh dấu `FAILED`. Không khẳng định cả hai khóa trùng khi chỉ có bằng chứng một constraint.
- `FAILED` sau partial commit không rollback Brand đã commit; các dòng chưa được xử lý không báo thành công. Không tự replay; người dùng kiểm tra Brand hiện có trước khi gửi job mới. Tiến độ bị mất/thiếu hoặc job treo không thể tái dựng đầy đủ trong phiên bản này.
- Product dùng `AbstractTwoPassExcelImporter`, FastExcel, temp file, Redis tracker, POI report và `@Async`: tracker khởi tạo sau pass header nên lỗi sớm có thể gặp job missing; TTL 1 giờ, read-modify-write DTO và errors, batch mặc định 500, update Redis sau transaction DB; temp local xóa best effort; executor chung 4/10 threads, queue 100 không durable. Tái dùng parser/generator/temp manager hoặc mở rộng kỹ thuật an toàn; không dùng nguyên orchestration nếu không đáp ứng init sớm, owner, counters và commit semantics. Không thay đổi Product endpoint, DTO hoặc key; tác động multipart chung 6 MB và 413 được chấp nhận như §5; thêm regression test.
- Temp file chỉ local, có thể tồn tại sau crash; cleanup best effort lúc kết thúc và từ chối dispatch. Single-instance là **giả định thiết kế chưa xác minh**, không khẳng định topology thực tế; Operations xác minh routing, Redis, lưu trữ staging, cleanup và executor trong deployment trước release. Không có recovery đa instance, fencing hay bảo đảm job hoàn tất sau restart.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | Template, 5 MiB, 10.000 dòng, job trước parse | Test header sai/thiếu, file hỏng/mã hóa, rỗng, ngưỡng, > ngưỡng và dispatch rejection | Không insert trước scan xong; sau 202 cố ghi FAILED nếu Redis hoạt động |
| AC-02 | Validation/default như CRUD | Tests raw/trim/Unicode/length, status, cell/formula | Kết quả tương đương CRUD và cell rule |
| AC-03 | Duplicate file/DB kể cả INACTIVE | PostgreSQL tests case/whitespace/collation, hai tham chiếu, concurrent rename | Chỉ skip có bằng chứng; không overwrite |
| AC-04 | Commit và counters | Injection lỗi trước/giữa flush/commit và Redis update; concurrent create | Không đếm thành công cho rollback; unique bảo vệ DB; sau commit trước Redis update cho phép thiếu counter |
| AC-05 | Failure safety | Process/Redis outage trước và sau commit, restart, TTL, temp cleanup | Không replay; Brand đã commit giữ lại; job có thể mất/thiếu/treo; không hứa report bền vững |
| AC-06 | Security/report | Hai actor, thiếu/quyền thu hồi, Redis outage, UUID khác, report terminal/formula | Không lộ job khác; report snapshot an toàn; Redis lỗi fail closed |
| AC-07 | Product/avatar và multipart | Multipart thật qua container/proxy: 5 MiB, vượt ngưỡng, overhead, avatar, Product; Product regression | 413 đã chốt; giới hạn avatar không đổi; Product có thể nhận file lớn hơn do multipart chung 6 MB tùy deployment |
| AC-08 | Parser và operational capacity | File nén cao, shared strings lớn, sparse rows, hỏng, concurrent jobs; đo heap/CPU/disk và xác minh deployment | Có bằng chứng giới hạn có thể enforce; rủi ro chưa kiểm soát chặn release |
| AC-09 | Boundary/gates | Full suite, module boundary, diff/secret check | Không tạo Category/Product/inventory; không có Brand logic trong share |

## 8. Open Decisions and Release Conditions

| Decision | Owner | Required Before | Status/Resolution |
|---|---|---|---|
| Redis TTL 1 giờ, partial commit, chấp nhận mất/thiếu tiến độ, không replay; không có PostgreSQL job/result hoặc fencing | User | Design Review | `RESOLVED` — quyết định phạm vi mới |
| CREATE + owner; 5 MiB; HTTP 413; XLSX binary | User | Design Review | `RESOLVED` |
| Tính khả thi 10.000 dòng và cơ chế xử lý file nén cao/shared strings với FastExcel | Tech Lead / Operations | Release | `PENDING` — kiểm thử và đo thực tế; không coi 50 MiB giải nén là bảo đảm |
| Topology, multipart/proxy, Redis, staging, cleanup, executor và capacity | Operations / Tech Lead | Release | `PENDING` — single-instance chỉ là giả định cần xác minh |

## 9. Known Limitations

Redis không giao dịch cùng PostgreSQL; status/counter/report là tạm thời, có thể thiếu hoặc mất sau sự cố, TTL hay eviction. Executor và staging cục bộ không durable, có thể để file mồ côi; job có thể kẹt mà không được tự đánh dấu FAILED. Không bảo đảm liệt kê toàn bộ dòng chưa xử lý hoặc đối soát đầy đủ sau crash. Không có test parser, benchmark, deployment hoặc implementation được chạy trong lượt lập kế hoạch này.
