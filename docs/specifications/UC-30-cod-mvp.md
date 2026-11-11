# UC-30 - Thu COD tiền mặt và bàn giao tiền: MVP

## Metadata

- **Work ID**: UC30-MVP
- **Version**: 0.4, ngày 10/11/2026
- **Owner / Decision Owner**: Product Owner (nghiệp vụ), Tech Lead (thiết kế và quyền)
- **Owning Module**: `finance`; `fulfillment` cung cấp dữ liệu đợt giao; `sales` sở hữu đơn.
- **Risk Reasons**: tiền, khai báo thu, phân quyền, concurrency, public contract, xuyên module.
- **Design Review**: APPROVED cho triển khai thử theo chỉ thị tiếp theo của người dùng: “triển khai thử không cần tạo nhiều code quá, dùng những cái có thể dùng”. Các lựa chọn kỹ thuật MVP được ghi ở đây; không khẳng định đã có một vòng review con người riêng.
- **Plan / trạng thái duy nhất**: [Delivery Brief](../../.agent/work/UC30-MVP.md), local-only.

### Điều chỉnh để triển khai gọn

- **Gia cố độc lập ngày 10/11/2026**, theo yêu cầu xử lý trước phần UC30 có thể làm trong khi chờ các UC khác: khi chuyển SUBMITTED → ACCEPTED, khóa đơn rồi khóa lại delivery và kiểm tra DELIVERED, codRequired, số COD dương/không đổi so với snapshot, cùng order/customer. Sai dữ liệu trả COD_002, không đổi version/trách nhiệm tiền hoặc tạo receipt. Không chặn REJECT vì nguồn delivery đổi. Đổi shipper không chuyển trách nhiệm khoản đã khai; khoản đã ACCEPTED tiếp tục được nhận bàn giao theo snapshot và replay không duyệt lại nguồn. Không triển khai thay UC26/39–43/63/64.

- **Quyết định mới nhất 09/11/2026: không cần ảnh COD**, cả khi khai thu từ khách và bàn giao cho kế toán. Khai số thực thu bằng JSON; kế toán kiểm tra/xác nhận thủ công và trực tiếp kiểm đếm tiền nhận.
- Ba bảng COD; dùng lại transactions, FinanceLockRepository, SalesOrderApi và IamApi. Gỡ upload/read/validation ảnh khỏi API COD. Không thêm JPA entity/mapper/service interface cho từng bảng chỉ để khớp layer.
- Quyền MVP: SHIPPER khai đúng đợt được giao; ACCOUNTANT kiểm khai báo thu và nhận tiền, cấm tự duyệt; SALES_STAFF/SHOP_OWNER chỉ xem. Không có bước upload hoặc xem ảnh.
- Dùng trigger PostgreSQL chặn mọi writer sửa tổng tiền/khách hoặc hủy đơn có COD mở, thay vì thêm public guard API và sửa nhiều service Sales.
- Công nợ có COD chưa xong trả `COD_REVIEW`, thêm codReviewRequired, codHeldAmount và customerCollectibleAmount; giữ outstandingBalance là tiền cửa hàng chưa nhận. Tạm chặn credit-check, blacklist-status, reminder và statement bằng COD_003 cho khách này. Không tự khẳng định khách là BLACKLISTED do tiền shipper giữ. Đây là giới hạn thận trọng MVP; chưa xây phân bổ theo hóa đơn UC63.
- Query MVP lọc shipperId/orderId/review status và phân trang; trạng thái bàn giao được trả ở từng dòng. Chưa có lọc ngày, dashboard tổng theo bộ lọc hoặc xuất phiếu PDF; FE có thể tích hợp danh sách hiện tại.
- Nguồn delivery là bảng deliveries đã có qua public DeliveryCodQueryApi; chỉ nhận đợt DELIVERED hợp lệ. Chưa thêm CRUD/giao hàng UC41 và chưa có màn hình FE trong repo này.

## 1. Goal and Scope

### Problem and Desired Outcome

Shipper khai đã thu tiền không có nghĩa cửa hàng đã nhận tiền. MVP lưu số thực thu do shipper khai, cho kế toán xác nhận thủ công và nhận bàn giao từng lần. Chỉ tiền cửa hàng đã xác nhận nhận mới được tính vào tổng thanh toán đơn. Nếu số khai thu được kế toán chấp nhận thể hiện khách đã trả đủ nhưng shipper nộp thiếu, khoản thiếu thuộc shipper; đơn vẫn chưa `PAID` cho đến khi cửa hàng nhận đủ.

### Nguồn và hiện trạng đã kiểm tra

- Quyết định trực tiếp mới nhất 09/11/2026 thay thế yêu cầu ảnh trước đó: không cần ảnh; giữ khai số thực thu, kế toán xác nhận, cửa hàng nhận đủ mới thanh toán hết và trách nhiệm shipper khi khoản đã thu được chấp nhận nhưng chưa nộp đủ.
- [Báo cáo nghiên cứu](../reports/UC-30-analysis-report.md), đặc biệt mục 2.1. Những phương án và quyết định mở cũ trái các quyết định trên không áp dụng cho MVP.
- SRS v1.3 tại `D:\AQS_Report-3_Software Requirement Specification_v1.3_02112026.pdf`, trang PDF 94-95: chọn shipper, xem COD từng đơn, kiểm đếm, tạo phiếu thu, nhánh thiếu. Quyết định người dùng làm rõ hậu điều kiện thiếu tiền; không đặt mọi đơn `PAID` vô điều kiện.
- `Delivery` có orderId, shipperId, batchNo, deliveryStatus, codRequired, codAmount; module fulfillment hiện chỉ có hai entity Delivery/DeliveryItem, chưa có controller/service UC-41.
- `OrderPaymentServiceImpl.record`: CASH trực tiếp tạo RECEIPT VERIFIED, chuyển khoản tạo PENDING; `summary` chỉ cộng RECEIPT VERIFIED.
- `OrderPaymentProofServiceImpl`: chứng từ chuyển khoản PENDING, lưu BYTEA, tối đa 5 MiB. Không dùng thẳng API này cho khoản COD chưa bàn giao.
- `PaymentVerificationController`: ACCOUNTANT xác minh chuyển khoản; không phải xác minh COD.
- `CustomerDebtServiceImpl`: hiện suy ra công nợ từ giao dịch đã xác minh, chưa tách tiền khách đã đưa shipper. Đây là điểm tích hợp bắt buộc để tránh nhắc khách trả lần hai.

### In Scope

- COD tiền mặt VND, shipper nội bộ, đợt giao đã có thật và được giao đúng shipper.
- Khai số thực thu bằng JSON; kế toán chấp nhận hoặc bác khai báo; không yêu cầu file.
- Danh sách COD, số dư shipper; nhận tiền từng phần, nhiều lần; một phiếu nhiều khoản cùng shipper và cùng VND.
- Tạo RECEIPT VERIFIED đúng số thực nhận được phân bổ; dùng tổng thu hiện có để cập nhật trạng thái đơn.
- Lịch sử bất biến, chống gửi trùng, chống nhận trùng, phân biệt thiếu ở khách/shipper.
- Hợp đồng cho hai màn hình: ghi thu tại đợt giao và đối soát COD; panel lịch sử tại đơn. Repository này chỉ triển khai backend; FE thuộc repo riêng.

### Out of Scope

- Đổi luồng tiền mặt tại quầy; mở khai báo thu tiền mặt cho mọi khoản thu UC-23.
- Khách chuyển khoản vào tài khoản cửa hàng: tiếp tục UC-23/UC-70; không tạo COD tiền mặt tương ứng.
- Xây toàn bộ điều phối/giao hàng UC-40/41, hãng vận chuyển ngoài, bù trừ phí, ngoại tệ, tự nhận diện tiền từ ảnh, ký số, xuất PDF phiếu thu.
- Tự động sửa/xóa/đảo khoản thu đã chấp nhận, xóa nợ hoặc tự xử lý tiền thừa. Chứng từ có sai sót chuyển xử lý ngoại lệ, không sửa số đã chốt trong MVP.
- Migration diễn giải lại COD cũ, tạo dữ liệu giao hàng giả hoặc ghi nhận tiền tự động từ trạng thái DELIVERED.

## 2. Actors and Security

Ma trận dưới đây là đề xuất MVP để Design Review duyệt, không mô tả quyền đã tồn tại.

| Actor | Allowed Operations | Required Role | Data Restrictions |
|---|---|---|---|
| Shipper | Khai thu, xem khoản và lịch sử của mình | SHIPPER | delivery.shipperId bằng actor; không nhận shipperId/actorId tùy ý từ client |
| Kế toán | Xem toàn bộ, chấp nhận/bác khai báo thu, nhận bàn giao | ACCOUNTANT | Không tự duyệt hoặc nhận khoản chính mình khai/thu dù có nhiều role |
| Bán hàng / Chủ cửa hàng | Xem COD và khai báo thu, lịch sử theo đơn | SALES_STAFF / SHOP_OWNER | Không mặc định có quyền xác nhận tiền chỉ vì có quyền xem |

Backend lấy UUID qua `IamApi`. Mọi API xem chi tiết và ghi đều kiểm tra quyền tài nguyên. Không có endpoint ảnh COD. Không log token hoặc thông tin nhạy cảm. Lưu người khai, người kiểm tra, người nhận và thời điểm do server tạo.

## 3. Business Rules

### 3.1. Thu và kiểm tra khai báo thu

1. Chỉ khai cho delivery `DELIVERED`, codRequired=true, codAmount>0, order còn hiệu lực, shipper được giao khớp actor. Việc sửa phân công không chuyển nghĩa vụ khoản đã khai sang người mới.
2. Số thực thu dương, không vượt phần COD còn chưa khai hợp lệ của đợt và phần nghĩa vụ đơn còn cho phép thu. Số tiền VND nguyên đồng, dùng BigDecimal/NUMERIC; không dùng float. Có thể khai nhiều lần cho cùng đợt, mỗi lần dùng idempotencyKey riêng.
3. Gửi application/json gồm deliveryId, amount, collectedAt, note và idempotencyKey. Không gửi multipart/file; không yêu cầu ảnh.
4. Khai thu tạo `SUBMITTED`, chưa tạo transactions RECEIPT, không đổi paymentStatus. Khoản chờ kiểm tra không phải thanh toán và không tự quy trách nhiệm đã xác minh cho shipper.
5. Kế toán kiểm tra số khai thu, đợt giao và đối chiếu thủ công với shipper rồi ACCEPT hoặc REJECT; không tự động chấp nhận khai báo. Bác bắt buộc lý do; giữ khai báo cũ, muốn khai lại tạo khoản mới, không ghi đè.
6. Chấp nhận giữ nguyên declaredAmount. Nếu số tiền sai, bác và khai lại; MVP không sửa số trong lúc duyệt. ACCEPT không tạo VERIFIED và không chuyển `PAID`.
7. Nếu đơn/đợt đã được trả bằng nguồn khác, dữ liệu đổi hoặc không đủ số dư khi duyệt, trả conflict để điều tra; không tự ghi thêm khoản thu hoặc âm thầm giảm số đã khai.

### 3.2. Số dư và trách nhiệm

Ký hiệu tính theo từng đơn, trong phạm vi không có hoàn/đảo:

```text
T = tổng nghĩa vụ đơn hiện hành
V = tổng RECEIPT VERIFIED của đơn (gồm khoản đã nhận từ shipper)
H = tổng COD ACCEPTED của đơn - tổng phân bổ bàn giao đã xác nhận cho các khoản đó
R = max(T - V, 0)                         // đơn còn thiếu tiền về cửa hàng
C = max(R - H, 0)                         // phần còn có thể yêu cầu khách trả
```

Giữ invariant `0 <= H <= R`. H thuộc trách nhiệm shipper; không cộng H vào V. Một khoản RECEIPT VERIFIED chỉ được cộng một lần. Khoản SUBMITTED hiển thị riêng là chờ kiểm tra; chưa được trừ như H. Khi còn khoản SUBMITTED, tạm dừng các hành động nhắc nợ tự động/xuất đối chiếu công nợ tới khách để kế toán xác minh trước.

| Tình huống | Trạng thái đơn / trách nhiệm |
|---|---|
| Khách trả đủ 22.220.000; khai báo thu ACCEPTED; cửa hàng nhận 0 | UNPAID nếu chưa có nguồn VERIFIED khác; R=H=22.220.000, C=0 |
| Cửa hàng nhận 22.000.000 của khoản trên | PARTIALLY_PAID; R=H=220.000, C=0; shipper còn phải nộp |
| Cửa hàng nhận thêm 220.000 | PAID, R=H=C=0 |
| Khách chỉ trả 22.000.000/22.220.000; cửa hàng nhận đủ 22.000.000 | PARTIALLY_PAID; H=0, C=220.000; thiếu ở khách |
| Đơn 30 triệu, VERIFIED cọc 10 triệu, nhận COD đợt 8 triệu | PARTIALLY_PAID; V=18 triệu, R=12 triệu; không tất toán toàn đơn |

`UNPAID`/`PARTIALLY_PAID` phản ánh tiền về cửa hàng theo quyết định người dùng. UI phải hiển thị thêm C và H, không gọi toàn bộ R là “khách còn phải trả”. Nếu khai báo thu đang tranh chấp, hiển thị “chờ kiểm tra”, không tự kết luận khách hoặc shipper đã sai.

### 3.3. Bàn giao

1. Chỉ phân bổ vào khoản ACCEPTED có số chưa nộp. Kế toán chọn cùng một shipper; mỗi dòng chỉ rõ collectionId và số nhận. Không tự phân bổ FIFO giữa nhiều đơn/khách.
2. Số mỗi dòng dương; không lặp collectionId; tổng dòng bằng đúng tiền thực nhận; mỗi dòng <= số còn giữ của nguồn. Không nhận phiếu số 0.
3. Cho nhận thiếu: tạo phiếu cho số thực nhận, phần còn lại tiếp tục mở; không reset số dư theo ca/ngày hoặc khi chuyến đóng.
4. Tiền lớn hơn số có thể phân bổ bị từ chối trước xác nhận và hướng dẫn kế toán xử lý riêng; MVP không tự gán tiền thừa vào đơn đã đủ hoặc bỏ phần thừa.
5. Mỗi dòng phân bổ sinh đúng một transaction RECEIPT, paymentType=COD, paymentMethod=CASH, status=VERIFIED; orderId/customerId/invoiceId lấy từ server. Người nhận là createdBy/approvedBy. Liên kết phiếu, nguồn thu và transaction để truy vết.
6. Sau ghi tiền, tính lại tổng đơn dưới khóa; V=0 => UNPAID, 0<V<T => PARTIALLY_PAID, V=T => PAID; không cho V>T. Cọc và chuyển khoản VERIFIED cũng góp vào V như hiện tại.
7. Nhận tiếp khoản thiếu tạo phiếu mới và transaction mới đúng số bổ sung. Không cộng lại khoản khai thu ACCEPTED vào tổng VERIFIED.

### 3.4. Các luồng hiện có phải phối hợp

- UC-23 ghi tiền và UC-70 xác minh chuyển khoản kiểm tra phần C dưới cùng khóa đơn: không nhận tiền khách lần hai cho phần H đã được chấp nhận. Riêng nhận bàn giao COD được tiêu thụ H và tăng V cùng transaction.
- UC-24 vẫn thể hiện số dư đơn R, nhưng bổ sung H và C. Các nhắc nợ, bảng đối chiếu gửi khách, số nợ quá hạn và blacklist dựa trên phần khách thực chịu C, không dùng phần H để phạt/đòi khách. Không thay V thành V+H và không tạo giao dịch giả để giảm nợ.
- MVP không xây phân bổ COD vào nhiều hóa đơn UC-63. Khi khách có khoản SUBMITTED hoặc H>0, chặn gửi reminder và xuất/gửi statement hiện có với thông báo “Cần đối soát COD trước”; danh sách nội bộ hiển thị cảnh báo và tách R/H/C. Không âm thầm phát hành chứng từ có số tiền nợ khách sai.
- Với khách có H>0, không tự blacklist do phần H; hạn mức tín dụng vẫn dựa trên tiền cửa hàng chưa nhận R theo nguyên tắc thận trọng của MVP. Nếu chưa tách chính xác overdue/blacklist theo đơn/hóa đơn, chặn quyết định tự động bị ảnh hưởng và yêu cầu kế toán xử lý, không phát hành kết quả sai. Cách bảo vệ này cần duyệt tại OD-03.
- Thay đổi/hủy/giảm tổng đơn có SUBMITTED hoặc H>0 phải bị chặn bằng lỗi “Đối soát COD trước” trong MVP; không làm mất nghĩa vụ shipper hoặc phá H<=R. Bao gồm các đường sửa đơn và điều chỉnh hóa đơn liên quan; không chỉ chặn trên UI.

### 3.5. State transitions

| From | Action | To | Conditions/Invariants |
|---|---|---|---|
| Chưa có khoản | Gửi khai thu JSON | SUBMITTED | Đủ quyền, delivery/số tiền hợp lệ, lưu khai báo và actor nguyên tử |
| SUBMITTED | Chấp nhận | ACCEPTED | Kế toán kiểm khai báo thu, khóa và kiểm lại số dư |
| SUBMITTED | Bác | REJECTED | Lý do bắt buộc; không xóa lịch sử |
| ACCEPTED | Nhận tiền | ACCEPTED | Chỉ tăng tổng đã nộp; tiền còn giữ tính từ phân bổ |

Trạng thái bàn giao là giá trị suy ra: `PENDING_REMITTANCE` khi chưa nộp, `PARTIAL_REMITTANCE` khi nộp một phần, `SETTLED` khi đã nộp đủ. Không trộn với reviewStatus hoặc paymentStatus của đơn. Phiếu nhận tiền được tạo thẳng CONFIRMED trong một transaction; chưa cần vòng đời nháp riêng.

### 3.6. Màn hình / năng lực backend cần phục vụ

| Vị trí | Nội dung / hành động |
|---|---|
| Chi tiết đợt giao của shipper | Phải thu, đã khai/chờ duyệt, form thực thu + ghi chú, gửi khai thu, lý do bị bác |
| Đối soát COD | Lọc shipper, ngày thu, trạng thái khai báo thu/bàn giao, order; tổng khai thu, đã chấp nhận, đã nhận, còn phải nộp; giữ khoản qua ngày |
| Chi tiết shipper / form nhận tiền | Chọn nguồn, kiểm tra và chấp nhận/bác khai báo, nhập tiền từng dòng, tổng kiểm đếm, lý do thiếu, xác nhận nhận tiền |
| Panel đơn | R/H/C, khai báo thu, người thu, người duyệt, các phiếu nhận tiền, mã giao dịch; cảnh báo phần shipper chịu trách nhiệm |

Chấp nhận khai báo thu và nhận tiền là hai hành động riêng. Có thể đặt cùng trang nhưng không dùng một nút “Xác minh” mơ hồ cho cả hai.

## 4. Data and Migration Decisions

Ba bảng mới thuộc finance ở V28. V29 gỡ yêu cầu NOT NULL của cột ảnh cũ để khai thu không cần ảnh; không sửa V28 hoặc xóa ảnh lịch sử nếu đã có.

| Bảng | Trường chính / ràng buộc |
|---|---|
| `cod_collections` | collection_id UUID; delivery_id/order_id/customer_id/shipper_id scalar; delivery_code, expected_amount snapshot; amount NUMERIC(15,2)>0; review_status; collected_at do khai báo, submitted_at server; reviewed_by/at, reject_reason; note; idempotency_key UNIQUE; request_hash SHA256 metadata; version. Cột ảnh legacy nullable, không dùng ở API hiện tại |
| `cod_remittances` | remittance_id UUID PK làm mã phiếu; shipper_id; received_by/at; amount>0 VND; note; idempotency_key UNIQUE; request_hash; bản ghi có nghĩa đã CONFIRMED, không thêm cột trạng thái hằng |
| `cod_remittance_items` | PK(remittance_id,collection_id); hai FK RESTRICT; amount>0; transaction_id UNIQUE FK transactions RESTRICT |

Mã đơn/khách/shipper không do client quyết định. FK database được phép; Java không liên kết entity chéo module. Index danh sách theo shipper/submitted_at, review_status/submitted_at, order_id, delivery_id và collection_id ở allocation. Các tổng liên dòng kiểm tra dưới khóa, không giả định CHECK SQL có thể bảo vệ tổng nhiều dòng.

API COD không lưu/đọc ảnh. V29 chỉ gỡ ràng buộc bắt buộc ảnh, không xóa dữ liệu cũ hoặc thay đổi order_payment_proofs của chuyển khoản. Chứng từ tài chính vẫn dùng bảng transactions, không tạo sổ thanh toán song song.

Không backfill từ cod_amount hoặc DELIVERED thành thực thu. Dữ liệu COD legacy cần danh sách xử lý thủ công trước rollout. Recovery sau deploy: tắt đường ghi UC30 nếu lỗi, giữ bảng và chứng từ; không drop bảng hoặc xóa khoản đã xác nhận để rollback ứng dụng.

## 5. Contracts

### HTTP API

Contract triển khai thử được chốt tại [Hướng dẫn API UC30](../contracts/uc30-mvp.md), base `/api/v1/finance/cod`. Payload ở `finance.internal.payload.CodPayload`, wrapper `ApiResponse<T>`; tất cả thao tác thành công trả HTTP 200, khai thu nhận JSON, không có endpoint file. Bảng năng lực dưới được đọc cùng điều chỉnh MVP đầu tài liệu; guide là hợp đồng cụ thể của phiên bản thử.

| Method / Năng lực | Authorization | Request / Validation | Success | Errors |
|---|---|---|---|---|
| POST khai thu (JSON) | SHIPPER + assigned delivery | deliveryId UUID; amount>0 nguyên VND; collectedAt; note<=1000; idempotencyKey UUID | 200 collection detail; replay cùng ID | 400 dữ liệu thiếu/sai; 403 sai shipper; 404 delivery; 409 số dư/trạng thái/key |
| GET danh sách COD | SHIPPER own; ACCOUNTANT/SALES_STAFF/SHOP_OWNER all | shipperId/orderId, status (review), page>=0, size 1..100; mặc định 20 | 200 items, page, size, totalElements, totalPages; trạng thái bàn giao từng dòng | 400 filter; 403 scope |
| GET chi tiết khoản | Quyền xem + ownership | collectionId | 200 collection detail | 403/404 |
| POST chấp nhận khai báo thu | ACCOUNTANT, khác người thu | collectionId, expectedVersion | 200 ACCEPTED, H mới; chưa có receipt | 409 conflict/đã bác; 403 self-review |
| POST bác khai báo thu | ACCOUNTANT, khác người thu | collectionId, expectedVersion, reason 1..1000 | 200 REJECTED | 400 reason; 409 đã ACCEPTED |
| POST nhận bàn giao | ACCOUNTANT, khác shipper | idempotencyKey UUID; shipperId; amount>0; 1..50 dòng collectionId, amount>0, expectedVersion; note | 200 receipt detail, line transactions; replay cùng ID; đọc balance để có số dư mới | 409 stale/overallocated/overpaid/key; 400 tổng sai; 403 |
| GET lịch sử phiếu theo khoản thu | Quyền xem + scope | collectionId | 200 danh sách phiếu gồm header, allocations, receipt transaction IDs, actor/time | 403/404 |
| GET số dư COD theo đơn | Quyền xem đơn hiện có | orderId | 200 R, H, C, pendingClaimAmount và các nguồn thu | 403/404 |

Collection detail gồm các scalar ID, snapshot đợt giao, số khai, trạng thái review, người/thời gian review, rejection reason, đã nộp, còn giữ, version. Không có trường hoặc endpoint ảnh. Request replay phải đối chiếu cả actor, metadata, số tiền, thứ tự dòng chuẩn hóa và hash metadata; cùng key khác nội dung trả 409. Review lặp cùng hành động trả kết quả cũ; hành động ngược trạng thái cuối trả 409.

### Inter-Module Java API

| Provider / Consumer | Method / DTO | Transaction / Authorization | Errors / Compatibility |
|---|---|---|---|
| fulfillment -> finance | `DeliveryCodQueryApi.find/lock(UUID deliveryId)` -> Optional<DeliveryCodSnapshot>: deliveryId, orderId, shipperId, deliveryCode, status, codRequired, codAmount | find read-only, lock MANDATORY; finance kiểm actor/quyền và lock order trước; không mutate delivery | Empty -> HTTP 404; DTO không entity; additive; tất cả writer delivery tương lai phải dùng cùng khóa |
| sales -> finance | Có sẵn `SalesOrderApi.getOrderPaymentInfo`, `lockOrderPaymentInfo`, `updatePaymentStatus` | Read-only hoặc MANDATORY theo code; khóa đơn trước đọc/ghi tiền; không thay signature | Không có đơn ->404, không payable ->409; giữ hành vi các consumer cũ |
| iam -> finance | Có sẵn `IamApi.getCurrentUserId` | Actor lấy từ authentication; không nhận actor từ body | 401/403 tại HTTP; không thay signature |
| PostgreSQL orders trigger | `guard_open_cod_order` | Kiểm dưới row lock trước update tổng/khách/hủy khi có SUBMITTED hoặc H>0 | SQLSTATE 23514 -> DB_001/409; không có COD mở giữ hành vi cũ; không thêm Java guard |

Snapshot là record `fulfillment.api.dto.DeliveryCodSnapshot`, không chứa entity. Hai method thực tế là `find(UUID)` read-only và `lock(UUID)` MANDATORY; trả Optional<DeliveryCodSnapshot>, empty ->404; finance kiểm quyền rồi giữ order lock trước delivery lock. Không import `.internal` khác module. Không thêm Domain Event mới cho đường xác nhận tiền của MVP; tránh cần hàng đợi/outbox chỉ để hoàn tất cùng PostgreSQL transaction. Audit COD tối thiểu nằm ở actor/time/trạng thái và dòng phiếu bất biến trong cùng transaction; số trước/sau tính lại từ khoản khai và các phân bổ không sửa/xóa. Không dựa riêng vào event sau commit để giữ chứng từ.

## 6. Concurrency, Transaction and Failure Safety

- Mutation cùng đơn dùng khóa `SalesOrderApi.lockOrderPaymentInfo` hiện có. Quy trình đa đơn: lấy idempotency advisory lock trước; lock tất cả order UUID tăng dần; rồi delivery/collection theo ID; không gọi lại API ghi tiền có thứ tự khóa khác sau khi đã giữ khóa đơn.
- Khai thu: sau khóa key + order + delivery, tính tổng khai không bị bác của đợt, kiểm số dư đơn và lưu collection nguyên tử. Verify: khóa order rồi collection, kiểm trạng thái/version và H<=R trước ACCEPT. Reject cũng dùng thứ tự order rồi collection.
- Nhận tiền: khóa key, order và collection; kiểm quyền, version, tổng phân bổ, H và R; tạo phiếu + dòng + transactions + cập nhật đơn cùng REQUIRED transaction. Lỗi bất kỳ bước nào rollback toàn bộ; thông báo thành công chỉ sau commit. Số dư tính từ allocations đã commit, không dùng bộ đếm do FE gửi.
- Một key có đúng một kết quả ghi bền vững; replay trả kết quả cũ trước áp dụng điều kiện version hiện tại. Không dùng key giao dịch chung tùy client để tạo receipt ngoài phiếu.
- Tái sử dụng repo/khóa/tính summary hiện có và tách helper nội bộ nếu cần; không gọi controller hay expose OrderPaymentService qua api. UC-23/70 và UC30 phải giữ chung invariant R/H/C dưới cùng khóa.
- Hai kế toán cùng nhận một khoản: một thành công; request còn lại conflict/version mới, không âm tiền. Hai dòng receipt không được tham chiếu cùng transactionId.
- Timeout sau commit: truy vấn theo key hoặc gửi lại cùng key, không tạo phiếu mới. Không cần mạng ngoài trong transaction nhận tiền. Không có tác vụ upload/file bên ngoài transaction.
- Chưa hỗ trợ chỉnh/đảo ACCEPTED hoặc CONFIRMED. Sai sót chuyển xử lý có người phụ trách; giữ nguyên dữ liệu, không delete hoặc đổi số tiền. Hạn chế này phải hiển thị trước nút xác nhận.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | Shipper khai COD bằng JSON không có file | HTTP + PostgreSQL | Thành công với dữ liệu hợp lệ; không lưu ảnh |
| AC-02 | Shipper khai đúng đợt được giao, số tiền hợp lệ | PostgreSQL integration | SUBMITTED + actor cùng commit; chưa RECEIPT, V không tăng |
| AC-03 | Kế toán xác nhận số khai thu đã đối chiếu | Integration | ACCEPTED, H tăng; V/paymentStatus chưa đổi |
| AC-04 | Bác khai thu có lý do và khai lại | Integration | Lưu lịch sử khai báo cũ; khoản bác không góp H hoặc chiếm số phải thu |
| AC-05 | Khách đủ 22.220.000; nhận 22.000.000 rồi 220.000 | Integration | Lần đầu PARTIALLY_PAID, H=220.000/C=0; lần sau PAID; receipt đúng từng lần |
| AC-06 | Khách chỉ trả 22 triệu/22,22 triệu và shipper nộp đủ | Integration | H=0, C=220.000; không gán thiếu cho shipper |
| AC-07 | Đơn 30 triệu cọc 10 triệu + COD nhận 8 triệu | Integration | V=18 triệu, vẫn PARTIALLY_PAID |
| AC-08 | Phiếu nhiều khoản, nhiều khách cùng shipper | Integration | Đúng đơn/customer từng dòng, tổng bằng quỹ thực nhận, không receipt header gán tùy một khách |
| AC-09 | Replay cùng key / key khác nội dung | Integration | Cùng kết quả hoặc 409, không receipt trùng |
| AC-10 | Hai kế toán nhận cùng khoản đồng thời | PostgreSQL concurrency | Không overallocate; một kết quả hoặc conflict an toàn |
| AC-11 | UC70 hoặc thu tại quầy chạy đồng thời với ACCEPT/nhận COD | PostgreSQL concurrency | Không V>T hoặc H>R; không thu khách lại phần H |
| AC-12 | Lỗi sau tạo transaction trước cập nhật đơn | Fault injection integration | Rollback phiếu/dòng/transaction/trạng thái cùng nhau |
| AC-13 | Shipper xem khoản thu người khác, giả actor, tự duyệt qua đa role | Security integration | Backend từ chối; không lộ metadata |
| AC-14 | Khoản qua ngày, đợt giao đóng | Query integration | Khoản chưa nộp vẫn xuất hiện |
| AC-15 | Tiền thừa/tổng dòng khác thực nhận/nhận 0 | Integration | Không commit chứng từ sai; hướng dẫn xử lý ngoài MVP |
| AC-16 | Khách đã trả đủ có ACCEPTED nhưng shipper còn thiếu | Debt regression | Không nhắc/statement đòi lại khách; hiển thị R/H/C; không blacklist vì H |
| AC-17 | Sửa/hủy/giảm đơn có SUBMITTED hoặc H>0 | Sales integration | Từ chối dưới khóa; không phá invariant |
| AC-18 | Thu tiền mặt tại quầy và chuyển khoản cũ, không liên quan COD | Existing regression | Hành vi hiện tại giữ nguyên; UC70 không lẫn hàng đợi COD |
| AC-19 | Nhập quá số COD còn lại hoặc dùng delivery không hợp lệ | Integration | Từ chối tại server; không dựa dữ liệu FE |
| AC-20 | Kiểm tra boundary và migration | ArchUnit + PostgreSQL | Chỉ api liên module; FK/index/check đúng; chạy trên DB mới và DB có dữ liệu không mất dữ liệu |

Kết quả kiểm chứng thực tế được ghi trong Delivery Brief. PostgreSQL thật/Testcontainers cho khóa, rollback và migration; mock không chứng nhận concurrency.

## 8. Open Decisions

| Decision | Owner | Required Before | Status / Resolution |
|---|---|---|---|
| OD-01: ownership, hai hành động kiểm khai báo thu/nhận tiền, chỉ ACCOUNTANT ghi nhận, cấm tự duyệt | PO + Tech Lead | Design Review | Chọn quyền mặc định MVP tại mục điều chỉnh theo chỉ thị triển khai thử |
| OD-02: nguồn delivery thật và điều kiện DELIVERED | Fulfillment owner | Implementation / rollout | Chưa có UC41 controller/service. Cần hợp đồng nguồn dữ liệu hợp lệ; MVP không tự tạo giao hàng giả. Kiểm thử được bằng fixture, chưa chứng nhận end-to-end production |
| OD-03: nợ còn ở đơn nhưng trách nhiệm shipper; bảo vệ UC24/statement/blacklist và hạn mức | PO + kế toán | Design Review | PAID/trách nhiệm đã chốt; MVP dùng COD_REVIEW và chặn hành động công nợ khi COD mở |
| OD-04: bỏ ảnh, giữ xác nhận thủ công của kế toán | PO + kế toán | Design Review | Người dùng bỏ ảnh ngày 09/11/2026; POST collections dùng JSON, gỡ endpoint proof |
| OD-05: URI/error codes, schema additive, lock order và guard sửa đơn | Tech Lead | Design Review | Contract thử tại guide, V28 ba bảng và trigger; đã có PostgreSQL tests |
| OD-06: tiền thừa, sai chứng từ đã chốt và COD legacy xử lý thủ công | PO + kế toán | Release | Đề xuất giới hạn MVP; phải có đầu mối xử lý ngoại lệ trước rollout |

Các lựa chọn triển khai thử cho OD-01/03/04/05 được thay bằng mục điều chỉnh MVP đầu tài liệu theo chỉ thị người dùng. OD-02 được đáp ứng ở tầng backend bằng đọc delivery hiện có và fixture trong PostgreSQL tests; chưa chứng nhận hành trình giao hàng/FE production. OD-06 vẫn là điều kiện rollout production: lỗi tiền thừa/đảo phiếu hoặc COD cũ cần quy trình riêng, không tự xóa hoặc sửa dữ liệu.
