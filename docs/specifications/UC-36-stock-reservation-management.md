# UC-36 — Quản lý hàng giữ

## Metadata

- Work ID: UC29-UC36-SPEC. Version: 0.4, 02/11/2026. Status: **DRAFT**.
- Feature: FE-07. Owner module: `inventory`.
- Risk: L — tồn kho, concurrency, contract xuyên module. Design Review: **PENDING**.
- Decision Owner: Product Owner/BA, Inventory Lead và Tech Lead. Đọc cùng [UC-29](UC-29-project-deposit-reservation.md), đặc biệt mục 9 về giới hạn kiến trúc dự án.

## 1. Mục tiêu và phạm vi

Tra cứu và quản lý các reservation đã tồn tại theo khách, chứng từ, SKU và kho; xử lý hạn giữ tạm, theo dõi lịch giao của giữ có cọc và giải phóng đúng căn cứ.

Điểm vào: màn “Quản lý hàng giữ”; có thể mở từ UC-29 với bộ lọc đơn hiện tại. Danh sách rỗng là kết quả hợp lệ, không bắt buộc đã có reservation mới cho mở màn hình.

Không tạo luồng thu/xác minh/hoàn tiền, thay đổi giá, duyệt hủy đơn hay xuất hàng độc lập. Giữ theo báo giá/đơn online có thể được tra cứu nếu nguồn đó đã tồn tại; không áp chính sách giữ tạm của một UC cho mọi reservation và không tự tạo thêm khả năng giữ từ báo giá khi SD-02 chưa chốt.

## 2. Actors và quyền

| Actor | Thao tác | Ràng buộc |
|---|---|---|
| Store Owner / Sales Staff có quyền | Tra cứu, xem lịch sử, mở đơn, yêu cầu xử lý phù hợp | Xem được reservation không có nghĩa được giải phóng mọi lượng |
| Kế toán | Duyệt hủy/giảm theo phạm vi đã được xác nhận tại UC-26 | Không tự thêm quyền nhập/xuất kho cho Kế toán trong UC-36 |
| System job | Xử lý giữ tạm đủ điều kiện hết hạn | Áp cùng SR-03/04/07/09/10, ghi actor hệ thống |

Ma trận quyền cuối cùng là **SD-03**, không lấy quyền cũ của UC-36 để bỏ qua quyết định duyệt của UC-26. Dữ liệu tiền/chứng từ hiển thị theo quyền finance.

## 3. Quy tắc và luồng nghiệp vụ

### 3.0. Quy tắc nghiệp vụ và phần tham chiếu

| Mã | Quy tắc | Áp dụng |
|---|---|---|
| SR-01 | Một nguồn reservation do inventory sở hữu; giữ theo dòng chứng từ, SKU và inventory/kho cụ thể. Đơn bổ sung không tự dùng cọc/quyền giữ của đơn chính | Cả hai UC |
| SR-07 | Giải phóng chỉ trong lượng còn giữ của đúng đối tượng, có lý do và căn cứ hợp lệ. Hủy/giảm đã duyệt tại UC-26; UC-36 thực hiện/hiển thị tác động, không duyệt thay hoặc tự hoàn tiền | UC-36; UC-29 dẫn sang |
| SR-08 | Giữ, chuyển loại và giải phóng không thay tồn thực. Xuất tiêu thụ reservation thuộc UC-39; mốc vật lý còn chờ thống nhất với flow/schema chung. Không trừ lần nữa khi xác nhận giao | Cả hai UC |
| SR-09 | Ghi lịch sử loại thao tác, lượng trước/sau, chứng từ, người thực hiện/hệ thống, thời điểm, lý do và khóa xử lý. “Gia hạn/đổi lịch” là hành động, không thay trạng thái đang giữ | Cả hai UC |

Các quy tắc còn lại được định nghĩa tại [UC-29, mục 3](UC-29-project-deposit-reservation.md#3-quy-tắc-và-luồng-nghiệp-vụ); dùng lại trực tiếp, không tạo logic thứ hai.

### 3.1. Luồng chính

1. Người dùng mở danh sách; hệ thống đọc **SC-01**.
2. Hiển thị mã reservation, chứng từ/dòng nguồn, khách/công trình nếu có, SKU/kho, loại giữ, lượng còn giữ/đã xuất/đã giải phóng, hạn giữ tạm hoặc lịch giao tham chiếu và tình trạng cọc từ nguồn có thẩm quyền.
3. Người dùng lọc theo khách/đơn/SKU/kho/loại; bộ lọc thời gian phân biệt **giữ tạm sắp hết hạn** và **giữ có cọc sắp đến/quá lịch giao**.
4. Người dùng mở chi tiết để xem lịch sử theo SR-09 và chọn hành động theo quyền.
5. Nếu cần thu/xác minh cọc hoặc sửa lịch dự kiến, mở đúng đơn tại [UC-29, mục 3.1–3.2](UC-29-project-deposit-reservation.md#31-luồng-chính); nếu đợt giao đã lập, chuyển UC-40. Quay lại thì tải dữ liệu mới, không thực hiện lại thu/giữ.
6. Nếu cần giải phóng, thực hiện mục 3.2; nếu hết hạn tự động, thực hiện mục 3.3. Hành động xem/lọc không làm thay đổi lượng giữ.

### 3.2. Giải phóng có căn cứ

1. Người dùng mở quyết định hủy/giảm đã duyệt từ UC-26 hoặc căn cứ xử lý khác đã được phê duyệt theo SD-03. Nếu chưa có căn cứ hợp lệ, chỉ cho tạo/chuyển yêu cầu đến UC sở hữu; không giải phóng trực tiếp giữ có cọc.
2. Hiển thị lượng còn giữ và lượng được phép giải phóng theo từng dòng/kho. Phần chờ hàng không được tính là lượng giữ để giải phóng.
3. Người dùng xác nhận lượng/lý do; hệ thống kiểm tra lại quyền, phiên bản quyết định và dữ liệu hiện tại rồi dùng **SC-03** theo SR-07/10.
4. Cập nhật lượng giữ tổng/chi tiết, khả dụng và lịch sử; không thay tồn thực, không tạo chứng từ nhập kho. Giải phóng một phần không đóng toàn bộ reservation.
5. Hiển thị kết quả. Việc hoàn tiền vẫn thuộc UC-26 phối hợp finance; không hiển thị “đã hoàn cọc” chỉ vì đã giải phóng hàng.

Nếu người dùng bắt đầu từ UC-29, UC-29 tham chiếu đúng luồng này; phía backend cùng dùng năng lực SC-03 trong inventory, không phụ thuộc người dùng phải thao tác lại ở cả hai màn.

### 3.3. Hết hạn giữ tạm

1. Job hoặc thao tác nghiệp vụ phát hiện deadline của giữ tạm theo chính sách nguồn.
2. Kiểm tra lại loại giữ, hiệu lực, lượng còn giữ và kết quả chuyển có cọc. Trường hợp có cọc VERIFIED nhưng thiếu mức/chờ đối soát chỉ được xử lý khi SD-01/05 đã xác định chính sách.
3. Với giữ tạm đủ điều kiện giải phóng, dùng cùng **SC-03**, ghi lý do hết hạn và actor hệ thống; xử lý lặp không giải phóng thêm.
4. Nếu đã chuyển giữ có cọc thành công thì bỏ qua; quá lịch giao của giữ có cọc chỉ cảnh báo.

### 3.4. Ngoại lệ và kết thúc

| Tình huống | Xử lý |
|---|---|
| Nhận thêm cọc/thanh toán | Chuyển [UC-29 bước 4–5](UC-29-project-deposit-reservation.md#31-luồng-chính); UC-36 không nhận tiền để “gia hạn” |
| Quá lịch giao giữ có cọc | Cảnh báo, liên hệ khách, chuyển sửa lịch đúng nguồn; giữ nguyên lượng |
| Lượng đang xem đã được UC-39 xuất/UC-26 giảm | Tải lại và từ chối thao tác lỗi thời; không lấy hàng giữ của dòng khác bù |
| Giải phóng quá lượng/không đúng chủ sở hữu | Từ chối, không clamp về 0 |
| Hàng đã xuất/giao | Không xử lý bằng giải phóng; tham chiếu UC-39/37 và quyết định hủy phần còn lại |
| Lỗi ghi lịch sử hoặc cập nhật số tổng | Không để biến động reservation thành công một nửa; theo mục 6 của UC-36 |

Kết thúc: số hiển thị phản ánh đúng nguồn; biến động được phép có lịch sử và đúng lượng; không sửa tiền, giá hoặc tồn thực ngoài UC sở hữu.

### 3.5. Vòng đời nghiệp vụ tối thiểu

| Từ | Hành động | Kết quả |
|---|---|---|
| Chưa có giữ | UC-29 có yêu cầu giữ và cọc 20% đã được xác minh | Giữ có cọc phần có thể phân bổ; các giữ tạm của UC khác vẫn theo chính sách nguồn |
| Giữ tạm còn hiệu lực | UC-29 xác nhận đủ điều kiện cọc | Giữ có cọc, lượng giữ cũ không đổi |
| Giữ tạm đủ điều kiện hết hạn | UC-36 xử lý hết hạn | Hết lượng giữ tương ứng, ghi lý do hết hạn |
| Còn giữ | UC-26 có quyết định giảm/hủy; UC-36 thực hiện SR-07 | Giảm đúng phần còn giữ, kết thúc khi không còn lượng |
| Còn giữ | UC-39 xuất một phần | Giảm lượng còn giữ theo lượng thực xuất; lưu phần đã tiêu thụ |

Đây là mô tả trạng thái nghiệp vụ, chưa ấn định enum Java/DB. Không ghi toàn bộ reservation Released nếu mới giải phóng một phần.

## 4. Dữ liệu và migration

| Dữ liệu | Owner | Hai UC sử dụng thế nào |
|---|---|---|
| Đơn, dòng đơn, giá đã duyệt, lịch giao dự kiến của đơn | sales | UC-29 quản lý trong phạm vi được phép; UC-36 xem/tham chiếu cùng nguồn |
| Tiền thực nhận, xác minh, phân bổ, hoàn tiền | finance | UC-29 điều phối các UC tiền; UC-36 chỉ đọc/tham chiếu, không có bản sao có thẩm quyền riêng |
| Reservation, lượng còn giữ, đã xuất/giải phóng, hạn giữ tạm, lịch sử | inventory | Một bộ dữ liệu cho cả hai UC |
| Lịch và kết quả đợt xuất/giao đã lập | fulfillment | Hai UC hiển thị từ nguồn có thẩm quyền; không tạo lịch giao thứ hai |
| Khách/công trình, quyền, audit | crm / iam / audit | Dùng public API/hạ tầng của module, không nhân bản nghiệp vụ |

Schema hiện có `inventories`, `orders`, `transactions`, `deliveries` làm nền; cần thêm dữ liệu reservation chi tiết. Lịch dự kiến ở UC-29 phải tham chiếu lịch hiện có của đơn; đợt đã lập thuộc fulfillment, sửa qua UC-40. Chưa quyết định cấu trúc lưu lịch mới. Không sửa baseline V1 hoặc suy ra được backfill chủ sở hữu reservation từ reserved_qty tổng.

UC-29 tham chiếu mô hình reservation tại mục này. Không có migration trong phạm vi soạn spec; quyết định SD-04 tại mục 8.

## 5. Contracts và tái sử dụng

### 5.1. Service tra cứu và giải phóng

Các mã mô tả năng lực dự kiến, chưa ấn định endpoint/DTO/chữ ký Java.

| Mã | Năng lực cần dùng chung | Owner / caller | Kết quả cần có |
|---|---|---|---|
| SC-01 | Tra cứu giữ theo chứng từ/dòng/kho | inventory / UC-29, UC-36 | Lượng còn giữ, đã tiêu thụ/giải phóng, loại, hạn, lịch sử |
| SC-03 | Giải phóng theo căn cứ hoặc hết hạn | inventory / UC-36, luồng hủy/giảm UC-26 | Đúng lượng, lý do và lịch sử; không thay tồn thực |

UC-29 hiển thị tab hoặc xử lý tác động hủy/giảm hợp lệ phải dùng lại SC-01/03 ở mục này qua InventoryApi. Tạo/chuyển giữ SC-02, kết quả cọc SC-04 và kiểm tra khả dụng được đặc tả tại [UC-29, mục 5](UC-29-project-deposit-reservation.md#5-contracts-và-tái-sử-dụng); UC-36 tham chiếu lại, không xây service thứ hai. Điều hướng UI không phải gọi controller chéo module.

### 5.2. Code hiện có có thể tái sử dụng

| Code hiện có | Phần tái sử dụng | Giới hạn/bổ sung cần thiết |
|---|---|---|
| [InventoryServiceImpl.releaseReservedStock](../../src/main/java/com/aquasuite/ims/module/inventory/internal/service/impl/InventoryServiceImpl.java) | Ý tưởng giảm lượng giữ tổng | Phải kiểm tra chủ sở hữu/lượng còn giữ; không dùng clamp về 0 để che giải phóng quá lượng |
| [InventoryRepository](../../src/main/java/com/aquasuite/ims/module/inventory/internal/repository/InventoryRepository.java) | PESSIMISTIC_WRITE và truy vấn tồn | Khóa đúng inventory/kho; truy vấn một dòng chỉ bằng SKU không đủ khi có nhiều kho |

Công thức khả dụng, service tạo giữ, biên InventoryApi và nền Transaction được đánh giá tại [UC-29, mục 5](UC-29-project-deposit-reservation.md#5-contracts-và-tái-sử-dụng). UC-36 dùng cùng implementation inventory, không sao chép. Cần bổ sung ghi lịch sử theo SR-09 dùng cho cả tạo/chuyển của UC-29 và giải phóng/hết hạn của UC-36.

Hiển thị tiền/lịch từ nguồn có thẩm quyền theo thiết kế composition/API/event được duyệt; chưa tạo mới dependency inventory → sales/finance hoặc sửa chữ ký public hiện có. Contract chính thức còn SD-05.

## 6. Concurrency, transaction và lỗi

- Cập nhật reservation chi tiết, reserved_qty tổng và lịch sử phải nhất quán trong transaction inventory. Khóa đúng inventory/kho theo thứ tự ổn định khi nhiều dòng; áp dụng cho cả SC-02 của UC-29.
- Job, giải phóng thủ công và UC-39 tiêu thụ phải bảo vệ cùng lượng giữ; không lấy hàng của đơn khác hoặc để số âm. SC-03 xử lý lặp không giải phóng thêm, áp SR-10 tại [UC-29, mục 3](UC-29-project-deposit-reservation.md#3-quy-tắc-và-luồng-nghiệp-vụ).
- Kiểm tra deadline cả lúc thao tác liên quan, không chỉ chờ job; tần suất/dung sai cần chốt, job nửa đêm chưa chứng minh đáp ứng tối đa chính xác 48 giờ.
- Chuyển có cọc đua hết hạn và lỗi sau nhận tiền dùng yêu cầu tại [UC-29, mục 6](UC-29-project-deposit-reservation.md#6-concurrency-transaction-và-lỗi), không viết cơ chế xác minh tiền thứ hai trong inventory.
- Lỗi cập nhật tổng/chi tiết/lịch sử không được để thao tác thành công một phần; cơ chế transaction/retry liên module còn SD-05.

## 7. Acceptance Criteria

| AC | Kết quả quan sát được | Kiểm chứng khi implementation |
|---|---|---|
| 36-01 | Reservation tạo/chuyển từ UC-29 hiển thị cùng ID, lượng và lịch sử | Integration hai UC |
| 36-02 | Lọc sắp hết hạn giữ tạm không coi giữ có cọc trễ lịch là hết hạn | Query/integration |
| 36-03 | Hủy/giảm có duyệt giải phóng đúng lượng; on_hand không đổi | Inventory/security integration |
| 36-04 | Thiếu căn cứ hoặc giải phóng quá lượng bị từ chối | Security/validation |
| 36-05 | Job chạy lặp không giải phóng hai lần; không giải phóng giữ đã chuyển có cọc | Retry/concurrency |
| 36-06 | Sửa lịch/thu tiền chuyển đúng UC-29/40; UC-36 không tạo lịch/thu riêng | Manual/integration |
| 36-07 | Xuất một phần và giải phóng đồng thời không tạo số âm hoặc lấy lượng của đơn khác | Concurrency |

Các AC chưa phải test đã chạy.

## 8. Quyết định còn mở

| Mã | Nội dung | Người xác nhận / thời hạn |
|---|---|---|
| SD-03 | Quyền xem/sửa lịch/giải phóng, phạm vi Kế toán duyệt hủy/giảm và ngoại lệ ngoài UC-26 | Owner/BA/IAM trước Design Review |
| SD-04 | Lịch giao, reservation theo phạm vi chọn và backfill dữ liệu tổng cũ; project là tùy chọn của UC-29 | CRM/Sales/Inventory/Lead trước Design Review |
| SD-05 | Contract, atomicity, xử lý hết hạn đua xác minh, retry và mốc tiêu thụ khi xuất | Lead/owners liên quan trước Design Review |

SD-01 về cọc/thiếu mức/chờ đối soát, SD-02 về điểm tạo giữ và SD-06 về nguồn chuẩn được định nghĩa tại [UC-29, mục 8](UC-29-project-deposit-reservation.md#8-quyết-định-còn-mở). UC-36 dùng lại, không tự chốt chính sách khác. Spec vẫn DRAFT, không ghi đè quy định chung dự án.

## 9. Điểm nối với bản thử UC-29

SC-01/02 có bản mô phỏng `ProjectReservationApi` thuộc inventory và được UC-29 dùng qua public api. Lịch sử được trả bằng cùng contract. Đây là adapter bộ nhớ chỉ trong profile demo, không phải reservation persistent để UC-36 vận hành thực tế. UC-36 chưa có màn/API giải phóng hay scheduler trong lần triển khai này; khi thực hiện UC-36 cần dùng cùng contract/service sau khi hoàn thiện, không tạo một kho giữ hàng thứ hai. Chi tiết giả định tại [UC-29, mục 10](UC-29-project-deposit-reservation.md#10-bản-triển-khai-thử-uc-29).

## 10. Đồng bộ UC-29 mới ngày 02/11/2026

UC-29 áp dụng cho mọi khách có yêu cầu giữ, cọc tối thiểu 20% giá trị phần chọn (toàn đơn nếu chọn toàn đơn), không tạo giữ miễn cọc mới và không yêu cầu công trình. UC-36 tham chiếu [UC-29 mục 3](UC-29-project-deposit-reservation.md#3-quy-tắc-và-luồng-nghiệp-vụ) để kiểm tra loại/quyền giữ và điều kiện cọc; giữ tạm từ UC khác chỉ được chuyển cho đúng dòng/số lượng được cọc bao phủ. SC-02 của UC-29 không được chuyển mọi dòng cùng đơn một cách mặc định. Khi không đủ hàng, chỉ phần thực phân bổ được là hàng giữ; còn chờ không làm hạ cọc của phạm vi đã chọn.

## 11. Thiết kế bước giữ hàng trước ngày 03/11/2026

Design Review: APPROVED cho bước inventory-first theo yêu cầu triển khai 03/11/2026; các bước UC-36 REST/scheduler vẫn hoãn. Phạm vi cọc và paid_amount nằm ngay trong [UC-29 mục 1](UC-29-project-deposit-reservation.md#1-mục-tiêu-và-phạm-vi); không tạo file quy tắc dùng chung riêng. Bước này xây năng lực inventory nội bộ để sales dùng sau, chưa mở REST tạo giữ khi chưa có kiểm tra đơn/cọc.

| Thành phần | Thiết kế đề xuất |
|---|---|
| Public contract | `inventory.api.StockReservationApi`: reserve theo mục tiêu lượng, query theo nguồn/dòng, release có requestId/lý do/actor. UC-29 gọi reserve/query; UC-36 dùng query/release cùng service. Chưa có giao tiếp inventory -> sales/finance. |
| Dữ liệu | Bảng inventory-owned reservations: ID, inventoryId, sourceType/sourceId/sourceLineId, variantId, warehouseCode, requestedQty, heldQty, consumedQty, releasedQty, trạng thái, createdAt/updatedAt. source IDs là UUID scalar. |
| Chống trùng | Duy nhất nguồn/dòng/inventory; request ledger có requestId duy nhất, payload chuẩn hóa và kết quả đã xử lý. Retry payload giống trả lại kết quả cũ; payload khác trả conflict. Request mới cũng không cộng vượt mục tiêu dòng. |
| Lịch sử | History/ledger inventory ghi thao tác, lượng trước/sau, nguồn, actor, requestId, thời điểm, lý do; không ghi thành stock_movements xuất/nhập vật lý. |
| Khóa | Khóa inventory theo variantId + warehouseCode; nhiều dòng khóa theo thứ tự ổn định. Cập nhật detail, reserved_qty và history/ledger trong một transaction. Bảo vệ cả trường hợp hai requestId khác nhau tạo cùng reservation. |
| Giải phóng | Chỉ giảm lượng còn giữ đúng reservation, không clamp số vượt về 0, không giảm on_hand_qty. Quyết định hủy/giảm/hoàn cọc thuộc UC-26 và finance; API kỹ thuật không tự cấp quyền duyệt nghiệp vụ. |
| Hết hạn/tiêu thụ | Không tự thêm scheduler 48 giờ hoặc endpoint xuất hàng vào bước này. Giữ có cọc không tự hết hạn. UC-39 integration tiêu thụ và quyết định giữ tạm sẽ làm sau theo nguồn nghiệp vụ. |
| Dữ liệu cũ | reserved_qty đang có vẫn được tính khi phân bổ, không đoán chúng thuộc đơn nào để tạo detail giả. Không thay các caller legacy reserveStock/releaseReservedStock trong bước này; trước tích hợp sales/fulfillment phải chuyển caller sang cùng ledger để tránh phá nhất quán. |
| Quyền | Chưa expose HTTP write route. Caller nghiệp vụ sau này phải kiểm tra quyền/căn cứ và lấy actor từ IAM, không lấy actor tùy ý từ client HTTP. UC-36 REST và ma trận quyền vẫn cần scope riêng. |

Bước này đã có implementation PostgreSQL trong StockReservationService/StockReservationStore và migration V6; không có HTTP write route. PostgreSQL tests phải chứng minh: giữ một phần, retry, trùng nguồn, concurrency tranh tồn, đúng kho, rollback khi ledger/history lỗi, giải phóng đúng lượng và tồn vật lý không đổi. Migration additive mới; không sửa V1–V5 và không thay đổi dòng lưu trữ workflow cũ.

### 11.1. Contract đã triển khai

- `StockReservationApi.reserve(Reserve)`: requestId, orderId, orderItemId, variantId, warehouseCode, requestedQty, actorId. Caller nội bộ chịu trách nhiệm xác thực nguồn và điều kiện cọc theo UC-29. Chỉ ORDER được hỗ trợ, không nhận báo giá tùy ý.
- `requestedQty` là mục tiêu cố định của một dòng trên tất cả kho. Đổi SKU hoặc mục tiêu sau lần tạo đầu bị từ chối. Cùng requestId/payload chuẩn hóa trả snapshot kết quả cũ; key mới có thể giữ tiếp phần thiếu khi hàng về.
- Kết quả từng kho chứa heldQty, consumedQty, releasedQty. waitingQty là số còn chờ của TOÀN DÒNG trên mọi kho, không cộng waitingQty giữa các record của cùng dòng. releasedQty là phần đã hủy, không được tự giữ lại bằng request mới.
- `release(Release)`: requestId, reservationId, quantity, reason, actorId. Chỉ primitive nội bộ; caller phải có căn cứ được duyệt. Giảm đúng lượng còn giữ, không tự hoàn tiền.
- `findByOrder(UUID)` và `history(UUID)` đọc cùng dữ liệu cho cả hai UC. Ledger stock_reservation_operations giữ payload, snapshot kết quả, actor, lý do và lượng trước/sau. Trạng thái được suy ra từ số lượng, không lưu enum trạng thái trùng lặp.
- Lỗi: INV_RES_001 invalid; INV_RES_002 conflict; INV_RES_003 not found; INV_RES_004 vượt lượng giải phóng; INV_RES_005 số tổng cần đối chiếu. Không clamp lỗi về 0.
- Mỗi mutation khóa transaction-scoped request key, rồi source-line key, rồi inventory đúng SKU/kho. JPA inventory và JDBC detail/ledger dùng cùng transaction; ledger lỗi thì rollback toàn bộ. Không có in-memory lock hoặc yêu cầu chỉ chạy một instance.
- Migration tạo paid_amount, stock_reservations và stock_reservation_operations. Dùng JDBC repository nội bộ cho reservation; không phát sinh entity association chéo module. Chưa có đường xuất tiêu thụ hoặc chuyển giữ tạm; consumedQty khởi tạo 0 để nối UC-39 sau.
- Phải chuyển các caller legacy reserveStock/releaseReservedStock/adjustPhysicalStock sang ledger khi tích hợp order/xuất kho; hiện các caller đó chưa theo dõi chủ sở hữu reservation. Không coi primitive này là luồng UC-29 hoàn chỉnh.

## 12. Trạng thái phần số lượng SKU đang giữ

Người dùng làm rõ phạm vi code được sửa: UC29, bundle, UC36. Khi kho có 10 chiếc và khách đủ cọc giữ 3 chiếc, 3 chiếc được đánh dấu HELD trong reservation, 7 chiếc còn lại AVAILABLE. Đây là hai phần số lượng của cùng SKU/kho, không phải trạng thái catalog của toàn SKU. Không tạo identity/serial giả cho từng chiếc.

- `StockReservationApi.Reservation.status`: HELD khi heldQty>0; WAITING khi chưa giữ được và còn waitingQty; CONSUMED/RELEASED khi không còn giữ hoặc chờ. Khi giữ được một phần, status HELD áp cho heldQty, phần thiếu vẫn là waitingQty, không gộp thành hàng đã giữ.
- `StockReservationApi.stockByVariant(UUID)` là query read-only trả `List<SkuReservationStock>` theo kho: onHandQty, heldQty (=reserved_qty tổng), availableQty=max(on_hand_qty-reserved_qty,0), quantities gồm HELD/AVAILABLE và lượng tương ứng. Caller nghiệp vụ chịu trách nhiệm quyền; thiếu ID trả INV_RES_001; SKU chưa có tồn trả danh sách rỗng. Không trả entity hoặc tạo dependency inventory sang sales/finance.
- HTTP GET `/api/v1/inventory/stock-reservations/variants/{variantId}`: JWT theo security hiện có; owner/admin hoặc SALES_STAFF; trả ApiResponse theo convention, thiếu quyền AUTH_002. Không mở lệnh reserve/release trong UC36.
- Trạng thái là projection của lượng persistent, không thêm cột trạng thái có nguy cơ lệch với lượng và không thêm migration. Ledger cũ thiếu property status vẫn đọc/replay được; property JSON mới chỉ đọc. Query luôn đọc số lượng hiện tại, không cache trạng thái trong JVM.
- Giữ 3/10: giữ nguyên tồn vật lý 10; tổng giữ 3; khả dụng 7. Request tiếp theo 8 chỉ giữ thêm tối đa 7 và chờ 1; khác kho vẫn tách riêng. Giải phóng 1 thì còn giữ 2, khả dụng 8; giải phóng hết thì reservation RELEASED và khả dụng 10.
- Không sửa InventoryServiceImpl, lifecycle enum của catalog hoặc các luồng khác. Các caller kho cũ đã dùng available_qty tiếp tục loại lượng đang giữ; việc tích hợp release/dispatch ngoài UC29/UC36 vẫn cần owner tương ứng.
