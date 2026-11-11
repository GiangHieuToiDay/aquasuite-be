# UC-29 — Đặt cọc để giữ hàng theo yêu cầu

## Metadata

| Thuộc tính | Nội dung |
|---|---|
| Work ID / Feature | UC29-UC36-SPEC / FE-06 |
| Phiên bản | 0.5 — 03/11/2026; inventory-first, paid_amount, đơn mới theo yêu cầu giữ |
| Owner module | sales; finance sở hữu tiền, inventory sở hữu reservation |
| Risk / Design Review | L; đã duyệt bước paid_amount + API inventory nội bộ ngày 03/11/2026. Tích hợp order/cọc chưa thuộc bước này |
| Phạm vi | Mọi khách hàng có yêu cầu giữ hàng; không bắt buộc công trình hoặc đơn chính |

## 1. Mục tiêu và phạm vi

### Cập nhật triển khai ngày 03/11/2026 — làm giữ hàng trước, cọc sau

Yêu cầu mới của người dùng: bổ sung `orders.paid_amount` và xây năng lực giữ hàng trước; đợi luồng order hoàn thiện mới tích hợp cọc. Phần này thay phạm vi triển khai bản thử trong mục 10 cho công việc tiếp theo, không tuyên bố bản thử bộ nhớ đã thành production. Chính sách cọc 20% khi khách yêu cầu giữ vẫn giữ nguyên; chưa mở REST cho phép khách/nhân viên bỏ qua điều kiện cọc.

#### Thiết kế bước triển khai đã duyệt ngày 03/11/2026

| Phần | Quyết định đề xuất |
|---|---|
| `orders.paid_amount` | `NUMERIC(15,2) NOT NULL DEFAULT 0`, không âm; Java `BigDecimal`. Tổng tiền thực nhận đã xác minh và phân bổ cho đơn, bao gồm cọc và các lần thanh toán sau. Không phải trường chỉ chứa tiền cọc. |
| `payment_status` | `UNPAID` nếu paid_amount = 0; `PARTIALLY_PAID` nếu 0 < paid_amount < total_amount; `PAID` nếu paid_amount >= total_amount. Cho phép trả dư; tiền còn phải thu = max(total_amount - paid_amount, 0). Đơn giá trị 0 cần ưu tiên quy tắc paid_amount = 0 -> UNPAID trong bước này, không tự đổi trạng thái đơn cũ. |
| Nguồn tiền | Finance vẫn sở hữu giao dịch. paid_amount là số tổng hợp theo đơn, không thay `transactions`, không cho client PATCH số tiền tùy ý. UC-24 hiện đang tính từ giao dịch VERIFIED; khi tích hợp thanh toán phải cập nhật/tái đối chiếu cùng nguồn, tránh hai kết quả khác nhau. |
| Migration | V6 bổ sung paid_amount; V5 của develop giữ nguyên mặc định 0. UC-29 bắt đầu bằng đơn mới, chưa có giao dịch trước đó theo xác nhận của người dùng; không cần backfill giao dịch cũ cho đơn UC-29. Không sửa payment_status cũ hoặc thay cách UC-24 tính từ transactions. Không coi giá trị mặc định trên đơn cũ là số đã đối soát. |
| Phạm vi code giữ hàng trước | Public Java API `StockReservationApi` trong inventory, persistent service + JDBC repository và migration reservation (không cần JPA entity cho bảng chỉ được truy cập qua repository JDBC). Chưa mở REST tạo giữ cho người dùng khi sales chưa kiểm tra đơn/dòng và điều kiện cọc; không tạo đơn giả để giữ hàng thật. |
| Đầu vào tạo giữ | requestId; sourceType/sourceId/sourceLineId để tương lai sales truyền ORDER/orderId/orderItemId; variantId, warehouseCode, requestedQty và actor lấy từ ngữ cảnh đáng tin cậy. Các ID chéo module là scalar, không JPA association chéo module. |
| Phân bổ | requestedQty là mục tiêu tích lũy của dòng nguồn, không phải số cộng thêm mỗi lần. Giữ tối đa phần khả dụng; trả heldQty và waitingQty. Gọi lại không được giữ vượt mục tiêu. Giữ không giảm on_hand_qty. Không tự nhập hàng hoặc cấp hàng cho phần thiếu. |
| Tích hợp sau | Sales xác thực đơn/dòng, số lượng, giá, quyền và paid_amount đạt cọc trước khi gọi inventory. Thời điểm xác minh/cập nhật paid_amount và thu cọc không triển khai trong bước này. |
| API dùng chung UC-36 | Định nghĩa chi tiết dữ liệu, query, giải phóng và transaction tại [UC-36 mục 11](UC-36-stock-reservation-management.md#11-thiết-kế-bước-giữ-hàng-trước-ngày-03112026). UC-29 dùng cùng service, không xây bộ reservation riêng. |

#### AC bổ sung cho bước này

- Repository đọc riêng của UC-29 ánh xạ orders.paid_amount, có giá trị mặc định 0 cho đơn mới; không sửa Order.java của develop; chưa có endpoint thu cọc hoặc cập nhật tiền thủ công.
- API inventory phân bổ đúng SKU/kho và dòng nguồn; giữ được phần có sẵn, báo phần thiếu; không thay tồn vật lý.
- Retry không cộng trùng; cùng requestId khác payload bị từ chối. Hai yêu cầu tranh tồn không làm reserved vượt khả dụng.
- Service giữ hàng chưa được expose thành HTTP write route bỏ qua kiểm tra đơn/cọc; sales sẽ gọi sau qua public Java API.
- Verification gồm PostgreSQL migration/locking/concurrency, test service/API và regression inventory hiện có. Người dùng đã xác nhận triển khai bước này; phần order/cọc và HTTP write cần tích hợp sau.

Bảng dưới đây là nội dung nghiệp vụ để copy vào Report. Các phần sau mô tả code, tham chiếu dùng chung và giới hạn bản thử.

| Thành phần | Nội dung |
|---|---|
| **Use Case ID** | UC-29 |
| **Use Case Name** | Đặt cọc để giữ hàng theo yêu cầu (Manage Deposits for Requested Stock Reservations) |
| **Primary Actors** | Sales Staff, Store Owner |
| **Related Actor / Use Cases** | Kế toán xác minh tiền qua UC-70/63; tạo đơn UC-21/28; quản lý reservation UC-36 |
| **Description** | Nhân viên ghi nhận yêu cầu giữ sản phẩm của khách, tạo đơn mới khi khách yêu cầu giữ hàng, thu cọc tối thiểu 20% giá trị phần hàng yêu cầu giữ và xác lập reservation sau khi cọc được xác minh. Áp dụng cho mọi khách; công trình không tự làm phát sinh yêu cầu cọc. |
| **Trigger** | Khách yêu cầu cửa hàng giữ một phần hoặc toàn bộ hàng trong đơn. |
| **Preconditions** | 1. Nhân viên đã đăng nhập và có quyền xử lý bán hàng/giữ hàng.<br>2. Khách hàng được xác định.<br>3. Sản phẩm, số lượng và giá sau chiết khấu đã được chốt/duyệt theo quy trình bán hàng.<br>4. Bắt đầu bằng yêu cầu giữ mới; đơn được tạo với paid_amount=0, UNPAID và chưa có giao dịch trước đó. |
| **Normal Flow** | 1. Khách yêu cầu giữ hàng; nhân viên chọn chức năng “Đặt cọc & Giữ hàng”.<br>2. Nhân viên lập danh sách hàng khách muốn mua/giữ.<br>3. Nhân viên chọn các dòng, số lượng yêu cầu giữ, kho và lịch nhận/giao dự kiến.<br>4. Hệ thống kiểm tra thông tin, giá đã duyệt, số lượng và quyền thao tác; tính giá trị phần hàng yêu cầu giữ.<br>5. Hệ thống tính cọc tối thiểu = 20% giá trị phần yêu cầu giữ; nếu giữ cả đơn thì tính trên toàn bộ giá trị đơn.<br>6. Hệ thống dùng nghiệp vụ tạo đơn UC-21/28 để tạo Order và Order Items; paid_amount=0, UNPAID. Retry dùng lại chính đơn vừa tạo, không tạo trùng.<br>7. Nhân viên ghi nhận khoản cọc và thông tin thanh toán qua UC-23, gắn đúng đơn vừa tạo.<br>8. Tiền được xác minh/ghi nhận qua UC-70/63. Chỉ tiền hợp lệ đã xác minh được tính vào mức cọc.<br>9. Khi cọc đạt yêu cầu, hệ thống dùng service inventory tạo hoặc chuyển reservation hiện có cho đúng các dòng/số lượng được chọn. Giữ phần tồn phân bổ được; phần thiếu chờ bổ sung, không giảm tồn vật lý.<br>10. Hệ thống hiển thị mã đơn, giá trị yêu cầu giữ, cọc tối thiểu/đã nhận, số còn phải thu, hàng đang giữ/phần chờ và lịch giao; ghi lịch sử. Reservation được quản lý tiếp tại UC-36. |
| **Alternative Flow — Không yêu cầu giữ** | Tiếp tục mua hàng theo UC bán hàng thông thường; UC-29 không áp nghĩa vụ cọc 20% và không tự tạo reservation. |
| **Alternative Flow — Retry** | Dùng lại đơn mới đã tạo cho cùng yêu cầu UC-29; không tạo trùng đơn hoặc giao dịch. Đơn cũ đã thanh toán không phải điểm bắt đầu của luồng này. |
| **Alternative Flow — Chờ xác minh hoặc cọc thiếu mức** | Ghi nhận đúng khoản khách đã nộp và trạng thái tiền; chưa kích hoạt giữ có cọc. Tiếp tục xác minh/thu bổ sung trên cùng đơn; không tạo lại đơn hoặc khoản thu khi retry. |
| **Alternative Flow — Không đủ hàng** | Thông báo lượng giữ được và phần chờ bổ sung; có thể giao nhiều đợt theo thỏa thuận. Cọc tối thiểu vẫn tính trên phần khách yêu cầu giữ, không tự hạ theo tồn đang có. |
| **Alternative Flow — Đã có reservation** | Đối chiếu đúng đơn/dòng/kho; tái sử dụng/chuyển giữ còn hiệu lực cho phạm vi đã chọn. Giữ đã hết hạn phải kiểm tra tồn lại; không cộng trùng hoặc chuyển giữ của dòng chưa được chọn. |
| **Alternative Flow — Lỗi thanh toán/giữ hàng** | Tiền không hợp lệ không kích hoạt giữ. Nếu tiền đã xác minh nhưng cập nhật giữ lỗi, thể hiện trạng thái chờ xử lý và retry cùng mã yêu cầu; không thu lại hoặc xóa giao dịch đã xác minh. |
| **Alternative Flow — Sửa/hủy/giảm** | Thực hiện UC-26 để có quyết định hợp lệ; UC-36 dùng service giải phóng đúng lượng. Hoàn tiền qua finance; hàng đã xuất/giao xử lý theo UC-39/37, không coi là giải phóng reservation. |
| **Postconditions** | 1. Có một Order mới cho yêu cầu, được dùng lại khi retry.<br>2. Khoản cọc gắn đúng đơn và có trạng thái xác minh rõ.<br>3. Khi đạt điều kiện cọc, chỉ lượng thực phân bổ được mới được ghi giữ; phần thiếu được hiển thị riêng.<br>4. Tồn vật lý không giảm khi giữ.<br>5. Có lịch sử yêu cầu/thu cọc/giữ và dữ liệu dùng tiếp ở UC-36.<br>6. Nếu chưa đủ điều kiện, đơn/khoản tiền đang chờ không được hiển thị như đã giữ thành công. |

## 2. Actors và quyền

| Actor | Thao tác | Ràng buộc |
|---|---|---|
| Sales Staff | Tạo yêu cầu giữ, tạo/tái sử dụng đơn, nhập cọc và lịch | Bản thử chỉ cho xử lý đơn do mình phụ trách; không tự xác minh tiền |
| Store Owner | Xử lý các đơn được phép và xem toàn bộ bản thử | Không có quyền tự hạ tỷ lệ cọc dưới 20% trong UC này |
| Kế toán / người có quyền UC tiền | Xác minh qua UC-70/63 | Không cấp quyền kho mới hoặc viết lại quy trình finance trong sales |

Giữ quyền hiện hành và boundary api/internal; không ghi credential hoặc bằng chứng thanh toán nhạy cảm vào log. Permission production và quyết định giải phóng xem [UC-36 mục 8](UC-36-stock-reservation-management.md#8-quyết-định-còn-mở).

## 3. Quy tắc và luồng nghiệp vụ

| Mã | Quy tắc UC-29 đặc tả; UC-36 tham chiếu dùng lại |
|---|---|
| SR-02 | Chỉ giữ dòng/số lượng khách đã chọn. Khả dụng = tồn thực − lượng còn giữ; phần thiếu là chờ hàng, không tăng reserved_qty. |
| SR-03 | UC-29 mới không tạo giữ tạm miễn cọc. Request chưa xác minh đủ cọc chỉ là yêu cầu chờ, chưa khóa tồn. Các giữ tạm của UC khác vẫn theo chính sách nguồn; nếu tồn tại thì có thể chuyển hợp lệ theo SR-05. |
| SR-04 | Cọc tối thiểu = 20% giá trị phần hàng yêu cầu giữ sau chiết khấu đã duyệt. Giữ cả đơn thì cơ sở là tổng đơn; không phụ thuộc khách lẻ/công trình. Chỉ cộng tiền hợp lệ phân bổ đúng đơn, không chuyển cọc giữa các đơn của cùng khách/công trình. |
| SR-05 | Tái sử dụng giữ hợp lệ của đúng dòng đã chọn; chuyển loại không tăng lại lượng cũ. Không chuyển giữ của các dòng khác chỉ vì cùng orderId. Nếu lượng đang giữ vượt phạm vi mới, cần xử lý thay đổi hợp lệ trước. |
| SR-06 | Giữ có cọc không tự hết hạn do quá lịch giao hoặc còn nợ. Giữ hàng không đồng nghĩa được phép xuất; business flow 2.6 tiếp tục kiểm tra điều khoản thanh toán. |
| SR-10 | RequestId chống tạo trùng Order/khoản thu/reservation; retry dùng lại đúng payload. Key mới cũng không được giữ quá nhu cầu đã chọn. Giá/đơn thay đổi sau yêu cầu phải được rà soát lại, không âm thầm tính theo giá mới. |

Các quy tắc SR-01/07/08/09 về sở hữu dữ liệu, giải phóng, tồn và lịch sử nằm tại [UC-36 mục 3](UC-36-stock-reservation-management.md#3-quy-tắc-và-luồng-nghiệp-vụ), không tạo file dùng chung riêng.

### 3.1. Luồng chính

Tham chiếu Normal Flow trong bảng mục 1. Đơn mới bắt đầu paid_amount=0/UNPAID, không có giao dịch trước đó; sau khi xác minh cọc mới cập nhật tiền và giữ hàng. Retry trả lại cùng đơn của yêu cầu. Lịch giao là dữ liệu thỏa thuận, không phải hạn tự hết của giữ có cọc.

### 3.2. Luồng thay thế và ngoại lệ

Tham chiếu các dòng Alternative Flow mục 1. Bản thử đóng băng phạm vi đã chọn; sửa phạm vi hoặc giá sau đó trả conflict để rà soát theo UC-26, không cho thu cọc của phần nhỏ rồi giữ toàn đơn. Các khoản trả thêm ở đơn khác không được cộng vào điều kiện cọc của đơn hiện tại.

## 4. Dữ liệu và migration

| Dữ liệu | Owner / sử dụng |
|---|---|
| Order / OrderItem | sales; tạo qua năng lực tạo đơn hoặc dùng lại đơn có sẵn; projectId là tùy chọn |
| Yêu cầu giữ | sales; lưu các orderItemId, số lượng chọn, giá trị cơ sở và snapshot giá đã chốt |
| Tiền cọc/xác minh | finance; Order ID là tham chiếu bắt buộc cho khoản cọc này |
| Reservation/ledger | inventory; cùng dữ liệu và service tại [UC-36 mục 4](UC-36-stock-reservation-management.md#4-dữ-liệu-và-migration) |
| Lịch giao | sales/fulfillment theo nguồn đã chốt; đợt đã lập sửa qua UC-40 |

Không tạo module cọc hoặc payment riêng. Bản thử không có migration/ghi DB; Order mới cũng là Order mô phỏng trong sales, chưa ghi bảng orders. Production còn phải chốt persistence, transaction và backfill dữ liệu giữ cũ.

## 5. Contracts và tái sử dụng

### 5.1. Service tạo/chuyển giữ và kết quả cọc

| Mã | Service/năng lực | Tham chiếu và dùng lại |
|---|---|---|
| SC-02 | ProjectReservationApi.allocate | inventory; UC-29 truyền đúng dòng/số lượng chọn. UC-36 dùng lại khi nối xử lý chuyển giữ, không tạo service giữ thứ hai |
| SC-04 | ProjectDepositPaymentApi | finance; ghi nhận/xem khoản tiền đúng đơn. Xác minh qua UC-23/70/63, UC-36 tham chiếu UC-29 khi cần thu/xác minh |
| OC-29 | Năng lực tạo Order của UC-21/28 | sales; khi chưa có đơn, tạo Order/OrderItem trước rồi liên kết payment. Bản thử dùng DemoProjectReservationOrderFactory; retry cùng request trả cùng Order |

SC-01 tra cứu và SC-03 giải phóng được đặc tả tại [UC-36 mục 5](UC-36-stock-reservation-management.md#5-contracts-và-tái-sử-dụng). UC-29 dùng cùng contract/service, không gọi controller UC-36 và không import internal của module khác.

### 5.2. Code hiện có có thể tái sử dụng

| Code | Cách dùng |
|---|---|
| ProjectDepositService | Điều phối yêu cầu chọn hàng, cơ sở cọc 20%, tiền đã xác minh, retry và trạng thái chờ |
| DemoProjectReservationOrderFactory | Adapter tạo Order demo trong sales, tra khách/SKU qua CrmApi/CatalogApi; giá lấy server, không nhận đơn giá từ client |
| Inventory.getAvailableQty / InventoryApi | Nền công thức khả dụng và public boundary của inventory; contract reservation mới vẫn cần hoàn thiện khi triển khai thật |
| Transaction / ProjectDepositPaymentApi | Nền tiền trong finance, không đưa entity finance sang sales |

Chưa có endpoint production được phê duyệt; API thử xem [hướng dẫn UC-29](../contracts/uc29-demo.md). Giữ tên class ProjectDeposit… hiện tại để tránh migration package không liên quan; tên class không giới hạn nghiệp vụ vào công trình.

## 6. Concurrency, transaction và lỗi

| Tình huống | Yêu cầu |
|---|---|
| Tạo đơn/thu tiền retry | Cùng key và payload trả đúng đơn/khoản thu cũ; không tạo đơn mới vì cọc chưa được xác minh |
| Tiền chờ hoặc mất phản hồi | Không kích hoạt giữ; phần tiền chưa biết đã nhận hay chưa phải được đối soát/retry cùng key, không thu vượt tổng đơn |
| Hai yêu cầu tranh tồn | Phân bổ trong khả dụng và số lượng đã chọn; khóa/cập nhật lượng tổng, chi tiết và lịch sử theo [UC-36 mục 6](UC-36-stock-reservation-management.md#6-concurrency-transaction-và-lỗi) |
| Giá/phạm vi thay đổi | Từ chối snapshot cũ, cần rà soát nghiệp vụ; không dùng cọc cũ để giữ thêm lượng ngoài yêu cầu |
| Đã xác minh nhưng inventory lỗi | Tiền giữ nguyên kết quả finance; reservation chờ xử lý, retry không thu lại |

Prototype chỉ khóa một JVM và lưu bộ nhớ. Quyết định atomic/SAGA, idempotency bền vững và cơ chế recovery production vẫn cần Design Review chung dự án.

## 7. Acceptance Criteria

| AC | Kết quả cần chứng minh |
|---|---|
| 29-01 | Khách không có projectId vẫn dùng được; đơn không yêu cầu giữ có reservationRequested=false, không phát sinh cọc |
| 29-02 | Chọn toàn đơn 100 triệu → cọc tối thiểu 20 triệu |
| 29-03 | Chọn phần hàng 40 triệu trong đơn 100 triệu → cọc tối thiểu 8 triệu; chỉ giữ phần đã chọn |
| 29-04 | Cọc PENDING/bị từ chối hoặc chưa đủ mức không kích hoạt giữ |
| 29-05 | Chọn 10, kho có 6: giữ 6/chờ 4, không hạ mức cọc theo 6 và không giảm tồn thực |
| 29-06 | Không có đơn: tạo đúng một Order và khoản cọc chờ; retry không nhân bản; đã có đơn thì dùng lại |
| 29-07 | Không chọn dòng lạ, trùng dòng hoặc quá số lượng đơn; giá trị tính server-side theo snapshot đã duyệt |
| 29-08 | Chỉ chuyển giữ tạm của các dòng đã chọn; UC-36 thấy cùng dữ liệu/lịch sử |
| 29-09 | Retry, lỗi phản hồi tiền, lỗi inventory, quyền truy cập và profile production được kiểm tra |

## 8. Quyết định còn mở

| Mã | Trạng thái |
|---|---|
| SD-01 | Đã chốt 20%, mọi khách, chỉ khi yêu cầu giữ. Chưa chốt ngoại lệ hạ tỷ lệ; bản này không hỗ trợ ngoại lệ |
| SD-02 | UC-29 tạo mới hoặc tái sử dụng đơn khi có yêu cầu; không tự giữ mọi đơn/khách công trình. UC khác có tạo giữ tạm riêng hay không vẫn theo spec nguồn |
| SD-06 | Các nội dung cũ về bắt buộc công trình, 30% và giữ miễn cọc ở UC-29 đã được thay bởi quyết định người dùng ngày 02/11/2026; cần cập nhật Report/BA đồng bộ |

SD-03/04/05 về permission production, schema, transaction, mốc xuất còn tại [UC-36 mục 8](UC-36-stock-reservation-management.md#8-quyết-định-còn-mở). Không đánh dấu production đã được duyệt.

## 9. Nguồn và giới hạn thay đổi của dự án

Tuân thủ [kiến trúc](../architecture/Architecture-Aquasuite.md), [module contracts](../architecture/module-contracts.md), [HTTP conventions](../contracts/api-conventions.md), [workflow](../workflow/feature-delivery.md). [Báo cáo đối chiếu ngày 31/10](../reports/project-deposit-reservation-business-review-2026-10-31.md) là lịch sử trước quyết định mới; các nội dung công trình/30% trong báo cáo đó không còn là chính sách UC-29 hiện hành.

Giữ nguyên UC-21/27/28 về tạo đơn/giá, UC-23/70/63 về tiền, UC-26 về thay đổi/hủy, UC-36 về reservation và UC-39–43 về xuất/giao. Không tạo hệ thống giữ hàng thứ hai hoặc chuyển business logic sang share.

## 10. Bản triển khai thử UC-29

| Nội dung | Hiện trạng |
|---|---|
| Profile | uc29-demo và không có prod/production; giữ hàng theo yêu cầu với cọc cố định 20% |
| Order hiện có | Đọc repository thật, không yêu cầu projectId; chỉ đơn còn hợp lệ và thuộc quyền truy cập |
| Order mới | DemoProjectReservationOrderFactory tạo Order/OrderItem bộ nhớ, tra giá catalog và khách CRM qua api; không nhận giá/discount tùy ý từ client |
| Selection | Dòng/số lượng chọn được đóng băng cùng giá; tổng cuối của đơn phân bổ theo tỷ trọng line_total để tính phần chọn sau chiết khấu; chọn toàn đơn khớp tổng đơn |
| Tiền/tồn | Adapter mô phỏng thuộc finance/inventory; tiền luôn bắt đầu PENDING, stock mặc định 0 và cần fixture; không ghi tiền/tồn thật |
| Lưu trữ | Đơn demo, selection, lịch và operation keys mất khi restart; không có migration hoặc khóa đa JVM |
| Chưa có | UC-36 scheduler/giải phóng, UC-39 xuất, UC-40 lập đợt, negotiated-price override và tích hợp đối soát/persistence production |

Xem [API và cách thử](../contracts/uc29-demo.md). Phải hoàn thiện adapter/persistence và review trước production, không chỉ bỏ điều kiện profile.

## 11. Kết quả bước inventory-first ngày 03/11/2026

- `orders.paid_amount` và migration V6 đã bổ sung; dùng `OrderDepositBalanceRepository` để đọc, giữ nguyên entity `Order` của develop. Chưa tự cập nhật payment_status hoặc paid_amount từ giao dịch; phần đó chờ tích hợp order/finance.
- Public Java `StockReservationApi` lưu PostgreSQL, không phải bộ nhớ và không bị giới hạn profile demo. Có reserve, query, history và release dùng chung UC-36; không thêm REST write hoặc tạo order giả.
- Quy tắc persistence và cách dùng xem [UC-36 mục 11](UC-36-stock-reservation-management.md#11-thiết-kế-bước-giữ-hàng-trước-ngày-03112026). API chỉ dành cho caller nội bộ đáng tin cậy sau này đã xác thực đơn/dòng, giá, cọc, quyền và actor.
- Các phần cũ mô tả Order hiện có và adapter `ProjectReservationApi` bộ nhớ là lịch sử bản demo, không phải điểm vào production của bước mới. Không tự nối demo với kho thật.

Ví dụ điểm nối sales sau khi order/cọc được triển khai và xác thực đầy đủ:

```java
var result = stockReservationApi.reserve(new StockReservationApi.Reserve(
        requestId, orderId, orderItemId, variantId, warehouseCode, requestedQty, actorId));
// result.heldQty(): lượng đang giữ tại kho này.
// result.waitingQty(): lượng còn thiếu của dòng trên tất cả kho.
```

Kiểm chứng bước này: 39 test pass, gồm 12 test PostgreSQL giữ hàng/concurrency/rollback, 3 test Flyway và 24 test hồi quy demo/boundary. Migration chỉ được chạy trên database Docker dùng để test. Chưa phải xác nhận hoàn tất toàn bộ UC-29 thu cọc.

## 12. Tương thích develop 2524776

Theo yêu cầu merge và giữ nguyên code develop: `Order.java`, `OrderItem.java`, `SalesOrderApi`, implementation UC-21 và migration V1/V5 giữ nguyên bản upstream. Migration UC-29 chuyển từ V5 sang V6 để không trùng version. `paid_amount` chưa tồn tại trong entity/contract của upstream mới nhất, nên UC-29 dùng repository JDBC đọc riêng trong sales; không thêm field hoặc method vào các contract upstream. Repository chỉ đọc số tổng hợp đã lưu; trạng thái suy ra và outstanding là dữ liệu tính cho UC-29, không tự thay payment_status hoặc ghi giao dịch. Phần thu/xác minh cọc vẫn chưa được nối.

Migration V6 chỉ được chạy trên database test mới. Nếu đã chạy V5 local cũ trên database riêng thì phải xử lý migration history theo môi trường trước khi nâng cấp; không sửa checksum/baseline hoặc migration upstream để che lệch lịch sử.

## 13. Bản cơ bản trên đơn có sẵn — 04/11/2026

Người dùng đã chọn triển khai trên đơn có sẵn. Phạm vi duyệt: chọn dòng, tính 20%, đọc tiền VERIFIED, giữ PostgreSQL và xem kết quả; không tạo/xác minh khoản thu hoặc tạo đơn mới.

Làm rõ trạng thái theo yêu cầu người dùng: kho 10 chiếc, đủ cọc giữ 3 chiếc thì phần 3 chiếc của reservation có `status=HELD`, phần 7 chiếc còn lại `AVAILABLE`. Trạng thái được suy ra từ số lượng lưu PostgreSQL, cập nhật ngay khi giữ/giải phóng, không thêm một enum trạng thái catalog cho toàn SKU. Không tạo 10 bản ghi vật phẩm hoặc tự gán serial. UC36 cung cấp GET `/api/v1/inventory/stock-reservations/variants/{variantId}` trả số lượng và status theo kho; cùng quyền đọc owner/sales của lát cắt hiện tại. Chỉ read-only, không expose lệnh reserve/release bỏ qua UC29. Không có migration mới hoặc thay code shared inventory/catalog.

- HTTP riêng, không đụng route demo: `GET /api/v1/sales/orders/{orderId}/stock-reservations`, `POST .../requests`, `POST .../synchronize`.
- POST requests: requestId, warehouseCode, lines[{orderItemId,quantity}]. Chốt bất biến phạm vi và snapshot giá, lưu DB kể cả chưa đủ cọc. Giá trị chọn phân bổ theo line_total/tổng cuối đơn. Cọc làm tròn lên 2 số. Không chọn dòng lạ/trùng/vượt lượng; không dùng projectId làm điều kiện.
- POST synchronize: cùng hình dạng body, bắt buộc trùng selection; key mới được bổ sung phần thiếu. Cùng key khác order/payload trả SALES_DEPOSIT_004 (409). Giá/phạm vi đổi trả SALES_DEPOSIT_008 (409).
- GET trả selection, giá trị/cọc, tiền đã xác minh, số còn thu, trạng thái và balances từng dòng; chưa có request vẫn xem được.
- Finance `OrderVerifiedPaymentApi.verifiedReceipts(orderId,customerId)` chỉ đọc, REQUIRED read-only; caller sales đã kiểm quyền. Chỉ RECEIPT VERIFIED đúng customer; order_id trực tiếp ưu tiên, nếu không có thì ánh xạ invoice ISSUED qua SalesReceivableApi như UC24. Không nhận entity hoặc số tiền client. Contract additive; lỗi nguồn được propagate để rollback, không coi là 0.
- paid_amount là projection: cập nhật từ kết quả finance trong POST cùng transaction; GET chỉ đọc nguồn finance, không ghi DB. Không sửa payment_status bằng UC29.
- Quyền giữ nguyên như demo: SHOP_OWNER/ADMIN/SUPER_ADMIN hoặc SALES_STAFF được giao đơn; actor từ IAM. Đơn phải CONFIRMED/PROCESSING và tổng dương. Không thu/xác minh tiền ở UC29.
- V8 thêm sales-owned selection/pricing/operation keys. Chốt request trước, khóa Order FOR UPDATE; cùng DB transaction với inventory, mọi dòng hoặc rollback tất cả. Nhiều dòng sắp xếp SKU/dòng ổn định. Lịch sử mutation dùng ledger inventory với actor và before/after.
- `StockReservationApi.reserveForDeposit` cùng dữ liệu reserve, khóa như reserve và từ chối SKU có reserved_qty không khớp tổng held ledger ở bất kỳ kho nào. Không đoán chủ sở hữu giữ cũ; trả INV_RES_005 (409), cần đối chiếu trước. Entry point reserve cũ giữ tương thích. Theo chỉ đạo chỉ sửa UC29/bundle/UC36, phần bảo vệ thêm trong service inventory dùng chung đã thu lại; release/dispatch của các UC khác chưa được tích hợp ledger, chưa chứng nhận chạy chung các luồng đó.
- Bản cơ bản không tự chuyển giữ legacy, không mở release/cancel/dispatch, không tạo lịch giao. Chưa phải hoàn thành toàn bộ UC29/36. Tiền phải có giao dịch thật đã xác minh qua luồng finance bên ngoài UC29.

## 14. Tự giữ sau xác minh — quyết định ngày 07/11/2026

Người dùng xác nhận luồng SRS UC29: xác nhận đơn và phần hàng/kho cần giữ → xác minh đủ cọc 20% → tự giữ các dòng đã chọn. Lựa chọn bấm giữ thủ công trong bản cơ bản được thay bằng tự kích hoạt sau commit thanh toán; synchronize vẫn dùng để xử lý lại/bổ sung hàng. Dùng nguyên API xác minh UC70 từ SCRUM19, không tạo API xác minh cọc riêng.

Thiết kế và AC của phạm vi này tại [WORK-020](WORK-020-auto-deposit-reservations.md); hợp đồng request/response hiện tại tại [UC29 cơ bản](../contracts/uc29-basic.md). Migration V20 bổ sung kho đã chốt và hàng đợi phân bổ có retry sau restart. Dòng hàng trong đơn có trạng thái giữ theo ledger; trạng thái danh mục sản phẩm và tồn vật lý không đổi. Tiền VERIFIED được giữ nguyên nếu phân bổ lỗi.

Đơn mới đi theo UC29 gửi `reserveStockAfterDeposit=true` khi tạo/chuyển báo giá để không giữ tồn legacy trước cọc. Không tự nhận hoặc xóa lượng giữ của các đơn legacy. Các phần demo/lịch giao/hoàn tiền và quản lý release/dispatch chưa triển khai vẫn nằm ngoài phạm vi WORK-020.


## 15. Quyết định mới nhất: tái sử dụng Finance

Người dùng xác nhận UC29 quản lý đơn đã có thỏa thuận cọc từ báo giá/order. Các quy tắc tính lại 20% selection ở phần triển khai trước được thay bằng `OrderReceivableApi.getOrderReceivable(orderId)`: dùng nguyên depositRequired, verifiedPaid, pendingAmount, remaining và depositSatisfied. Chỉ giữ khi boolean đủ cọc là true; không tự tạo ngưỡng, không giữ một phần vì đóng thiếu cọc. Response dùng `payment: OrderReceivableView`; xem contract uc29-basic.md. Không sửa luồng báo giá, order hoặc xác minh tiền trong lần điều chỉnh này.
