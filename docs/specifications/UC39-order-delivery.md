# UC39 — Tạo và xác nhận Delivery từ Order

## Metadata
- Work ID: UC39-ORDER-DELIVERY
- Feature: UC39, nối UC29/UC36 với giao hàng đơn giản WORK-025 và COD UC30.
- Version: 1.0 — 2026-11-12.
- Owner: Product Owner; implementation owner chưa phân công.
- Owning module: fulfillment; Sales/Inventory/Finance sở hữu contract của mình.
- Risk: L — tồn kho, tiền, phân quyền, đồng thời, public contract liên module.
- Design Review: PENDING. Đây là thiết kế đề xuất, chưa cho phép triển khai hoặc ghi DB.

## 1. Mục tiêu và phạm vi
Từ một order đủ điều kiện, tạo Delivery nháp; xác nhận phiếu sẽ xuất hàng thật rồi chuyển sang DISPATCHED để dùng ngay API phân công hiện có.
Một Delivery là phiếu xuất kiêm đợt giao của một order. batch_no phân biệt các đợt. Không tạo GoodsIssue/Trip/Stop/Vehicle/DeliveryAttempt hoặc bảng tương đương.

Trong phạm vi: xem lượng còn xuất, tạo nháp, hủy nháp, xác nhận xuất, số lượng từng đợt, snapshot người nhận, COD từng đợt, chống trừ tồn lặp. Nháp không giữ thêm hàng và không trừ tồn.
Ngoài phạm vi: tự hoàn tất toàn order, thay paymentStatus, thu tiền tự động, nhập hoàn sau giao thất bại, giao lại phiếu đã FAILED, thay đổi UC26 hoặc các API thanh toán cũ.

## 2. Actors và bảo mật
ACCOUNTANT và SHOP_OWNER được tạo nháp, xác nhận xuất và hủy nháp. Kế toán và chủ cửa hàng tự trao đổi quyết định xuất ngoài hệ thống; không thêm yêu cầu phê duyệt riêng. ACCOUNTANT/SHOP_OWNER tiếp tục xem/phân công theo WORK-025.
Actor lấy từ IamApi; không nhận createdBy/confirmedBy/shipperId/batchNo/status từ client. Shipper không được tạo/xác nhận xuất. Thông tin người nhận chỉ cho nhân viên có quyền; không log toàn bộ payload/ảnh.

## 3. Quy tắc nghiệp vụ
1. Giai đoạn đầu đề xuất chỉ WH_MAIN; server xác minh kho. Chưa tuyên bố hỗ trợ đa kho chỉ vì entity có warehouseCode.
2. Chỉ order PROCESSING; DRAFT chờ duyệt nợ, CANCELLED, COMPLETED bị chặn. Sales kiểm tra lại ở thời điểm xác nhận, gồm quyết định hủy/giảm đang chờ xử lý và revision của dòng hàng.
3. Số còn xuất của dòng = quantity hiện hành - tổng delivery_items.quantity thuộc các Delivery đã xác nhận xuất. FAILED vẫn đã xuất, không tự cho xuất lại. DRAFT/CANCELLED chưa xác nhận không tính. Legacy có status xuất mà thiếu confirmedAt: chặn xác nhận mới cho order liên quan để đối chiếu, không âm thầm bỏ khỏi tổng.
4. Một request chỉ chứa orderItemId thuộc order và quantity >0, tối đa 2 số thập phân, không trùng dòng. Server suy ra variantId, inventoryId; không tin ID kho/tồn do client khai. Nếu combo đã bung thành dòng SKU thì dùng các dòng thực xuất; không xuất đồng thời dòng cha và thành phần. Cần xác nhận cách nhận diện dòng thực trong Sales contract.
5. Tạo nháp không bảo đảm hàng sẽ còn đến lúc xác nhận. Mọi điều kiện phải kiểm tra lại khi confirm; nháp lỗi có thể hủy và tạo lại. Không cần API sửa nháp ở bản đầu.
6. Tạo nháp dùng deliveryId UUID do client tạo làm khóa retry (không phải bằng chứng quyền). Server tạo mã DL-<UUID đầy đủ>, cấp batch_no = max của order +1 dưới khóa order; không tái dùng số của nháp hủy. Cùng ID + cùng actor + cùng nội dung trả bản cũ; khác nội dung/actor báo conflict. Payload đối chiếu từ dữ liệu nháp đã lưu; danh sách dòng/serial được chuẩn hóa.
7. Xác nhận: DRAFT -> DISPATCHED; ghi confirmedBy/confirmedAt/dispatchDate, trừ tồn thật và chuyển heldQty sang consumedQty tương ứng. Không gọi release thay consume vì release làm hàng quay lại khả dụng. Không gọi lại adjustPhysicalStock sau khi API xuất đã trừ kho.
8. Khi có reservation của chính dòng/kho, tiêu thụ held trước; lượng vượt held chỉ được lấy từ available không thuộc người khác nếu chính sách thanh toán cho phép. Tuyệt đối không dùng aggregate legacy reservedQty để nhận là reservation của order này; bản đầu chặn SKU có legacy holds chưa đối chiếu.
9. Serial-tracked SKU: số serial duy nhất bằng số lượng nguyên; Inventory khóa và xác nhận serial đúng SKU/kho, sẵn sàng, không đã xuất. API serial chưa có nên đây là dependency cần bổ sung; nếu chưa hoàn thành phải từ chối SKU tracking, không nhận serial như text tự do.
10. Hủy chỉ nháp chưa confirmedAt: DRAFT -> CANCELLED, không tác động tồn/reservation/tiền. Phiếu đã xác nhận không được xóa/hủy hoặc sửa items, kho, COD, orderId qua API này.
11. Quyết định người dùng: kế toán và chủ cửa hàng tự trao đổi việc cho xuất. Không thêm bước owner duyệt, không chặn xuất chỉ vì chưa cọc hoặc chưa thanh toán đủ; cho phép đơn COD/đã cọc/chưa cọc khi ACCOUNTANT hoặc SHOP_OWNER xác nhận. Vẫn bắt buộc order PROCESSING, không bỏ qua quy trình duyệt công nợ đang khiến order ở DRAFT, không bỏ kiểm tra tồn/số lượng/reservation/serial. Xác nhận xuất ghi actor và thời gian thật, không giả rằng owner đã phê duyệt.
12. COD: người có quyền nhập số cần thu của đợt, số nguyên VND >=0. codRequired suy ra từ codAmount>0. Không suy ra toàn bộ dư nợ là COD. Tổng COD còn cam kết chưa thu của các phiếu đã xác nhận + pending COD đã khai báo không vượt dư còn phải thu sau VERIFIED. Trừ số COD đã khai báo khỏi cam kết của đúng delivery để tránh tính hai lần; VERIFIED đã phản ánh trong dư nợ, REJECTED không coi là đã thu. FAILED vẫn giữ cam kết đến khi nghiệp vụ kho/Finance xử lý rõ, không tự chuyển sang đợt mới.
13. Trước xác nhận cần Finance public contract trả sức chứa COD theo delivery, dùng khóa order chung với UC30/payment. Nếu khoản thanh toán khác làm dư nợ giảm sau xuất, shipper phải xem số còn được phép thu tại thời điểm thu và UC30 vẫn kiểm tra balance; không chỉ tin codAmount snapshot.
14. Xác nhận xuất không sửa trạng thái hoàn tất/payment của order. Tiến độ xuất được đọc từ phiếu xác nhận; mọi ghi tiến độ vào Sales chỉ qua contract riêng đã duyệt, không invent enum PARTIALLY_FULFILLED chưa có.

## 4. Dữ liệu và migration
Dùng nguyên deliveries và delivery_items, scalar IDs qua module. Đã có deliveryId/code, orderId, warehouseCode, batchNo, recipient, COD, createdBy, confirmedBy/At, dispatchDate, trạng thái, items/serial_numbers.
Không cần bảng nghiệp vụ mới. Cân nhắc migration additive thêm UNIQUE(order_id,batch_no) khi batch_no NOT NULL và UNIQUE(delivery_id,order_item_id) nếu mỗi dòng chỉ một inventory trong WH_MAIN. Kiểm tra dữ liệu trùng trước; không tự sửa legacy hoặc migration cũ. Version chọn tại lúc triển khai, không đặt trước V36.
stock_movements và reservation activities hiện hữu phải ghi tham chiếu deliveryId/deliveryItemId để truy vết và retry; xác minh unique/idempotency hiện hữu trước khi chọn DDL bổ sung. Không hứa không cần migration nếu chưa audit constraints.
Giữ dữ liệu legacy; khi tồn/serial không đối chiếu được thì chặn xuất, không backfill giả. Rollback deploy giữ chứng từ đã xác nhận và ảnh.

## 5. Contracts đề xuất
Base /api/v1/fulfillment, ApiResponse; mutation trả detail/summary theo kiểu hiện có.

| Method/path | Quyền đề xuất | Hành vi |
|---|---|---|
| GET /orders/{orderId}/delivery-candidates | ACCOUNTANT, SHOP_OWNER | Order snapshot/revision, từng dòng ordered/issued/remaining/ownHeld/available, thông tin nhận, lý do chưa xuất được; chỉ tham khảo |
| POST /orders/{orderId}/deliveries | ACCOUNTANT, SHOP_OWNER | Tạo nháp; HTTP 200 để khớp mutation hiện hữu |
| POST /deliveries/{deliveryId}/confirm-issue | ACCOUNTANT, SHOP_OWNER | Body rỗng; kiểm tra lại và xuất atomic; retry cùng người xác nhận trả kết quả cũ |
| POST /deliveries/{deliveryId}/cancel-draft | ACCOUNTANT, SHOP_OWNER | Body rỗng; chỉ hủy nháp; retry cùng người tạo hợp lệ an toàn |

Request tạo nháp: {deliveryId,warehouseCode,recipientName,recipientPhone,deliveryAddress,scheduledDate,codAmount,note,items:[{orderItemId,quantity,serialNumbers:[]}]}. Giới hạn đề xuất 100 dòng, trường text theo entity; scheduledDate ISO offset date-time. quantity decimal; tiền nguyên VND. Không gửi giá, tồn hoặc quyền phê duyệt từ frontend.
Danh sách nháp dùng GET /deliveries?status=DRAFT hiện hữu; detail và các đợt theo order giữ nguyên. Nháp bất biến nên confirm không cần expectedVersion; các thay đổi từ order luôn kiểm tra lại dưới khóa.
Lỗi dự kiến: 400 payload sai; 403 thiếu quyền; 404 không có order/phiếu; 409 order chưa đủ điều kiện, vượt remaining/tồn/COD, serial conflict, quyết định UC26 chưa xong, replay khác payload hoặc legacy không đối chiếu. Chọn mã lỗi FUL mới còn trống khi triển khai, không đổi FUL_001..004.

### Public Java contracts cần thêm (chưa tồn tại)
- Sales: OrderDispatchApi.getSnapshot/lockSnapshot(orderId): status, revision, dòng thực xuất và số lượng, địa chỉ nhận, quyết định thay đổi chưa hoàn tất, điều kiện thương mại; không trả entity. lockSnapshot giữ khóa order đến commit caller.
- Inventory: InventoryDispatchApi.issue(command): operationId=deliveryItemId, deliveryId, orderId/orderItemId, variantId, warehouseCode, quantity, serials, actor. API tự khóa inventory/reservation/serial theo thứ tự ổn định, trừ onHand, tiêu thụ held đúng nguồn, cập nhật serial và stock_movements; trả inventoryId và lượng issued/consumed. REQUIRED, không REQUIRES_NEW; tất cả dòng rollback chung. Không sửa nghĩa reserve/release hiện hữu.
- Finance: DeliveryCodCapacityApi.read/validate kế hoạch COD dưới khóa order; không để fulfillment truy vấn finance.internal. Thống nhất thuật toán pending/verified/rejected và cam kết theo delivery trước code. Chống vòng Finance <-> Fulfillment: fulfillment cung cấp snapshot cam kết của các phiếu cho Finance trong command; Finance không callback fulfillment khi đang validate. Quy tắc khóa tương thích UC30 order -> delivery.
- IAM/Catalog public API đang có tiếp tục dùng. Không import repository/entity internal của Sales/Inventory/Finance.
Không thêm domain event bắt buộc cho xuất thành công: tài chính/tồn/chứng từ cần cùng transaction đồng bộ. Audit tích hợp qua contract audit thật, không dùng updatedAt làm lịch sử bất biến.

## 6. Transaction và đồng thời
Tạo/hủy/xác nhận đều khóa order trước delivery để thống nhất UC30. Với request bắt đầu từ deliveryId: đọc scalar orderId, khóa Sales order, rồi khóa delivery và kiểm tra lại liên kết. Sau đó khóa inventory/reservation/serial theo UUID/thứ tự thống nhất do Inventory định nghĩa; phải rà soát cả UC29/UC36 và luồng khóa variant hiện hữu trước duyệt triển khai.
Confirm trong một DB transaction: lock order -> lock delivery -> validate quantities/COD/order state -> issue từng dòng qua Inventory REQUIRED -> cập nhật confirmed fields/status -> commit. Mọi lỗi rollback stock, reservation, serial, movement và delivery cùng lúc. Không upload ảnh trong confirm.
Hai nháp cùng order cùng hàng chỉ một được confirm nếu tổng vượt ordered. Request confirm lặp không issue lần hai. Việc đổi trạng thái UC26/đơn phải dùng cùng order lock để không duyệt hủy đồng thời với xuất. Khi chưa bảo đảm lock chung, không nghiệm thu concurrency.

## 7. Acceptance và verification
- Owner tạo nháp từ PROCESSING; thiếu quyền/đơn không đủ điều kiện bị chặn; chưa trừ tồn.
- Cấp batch_no riêng, retry cùng request không sinh phiếu mới; thay payload báo conflict.
- Xuất 5/15: remaining=10, onHand giảm5, reservation cùng dòng consumed tăng tương ứng, stock movement chỉ một lần.
- Hai confirm đồng thời không vượt ordered/tồn, không ăn reserved của order khác; nháp khác vẫn kiểm tra lại.
- Lỗi một dòng/serial/ghi chứng từ rollback toàn bộ.
- FAILED đã xuất không làm tăng remaining, không tự hoàn kho hoặc cho xuất lại.
- COD nhiều batch không vượt sức chứa; kiểm tra pending/verified/rejected và concurrent payment/claim/confirm.
- UC29 retry không phục hồi hàng đã consumed; UC36 không release consumed; legacy holds không được nhận nhầm.
- Phiếu DISPATCHED+confirmedAt phân công/start/complete với ảnh local như hiện có; không sửa paymentStatus hoặc tự hoàn tất order.
- Không bảng Trip/GIN mới; không import internal chéo module; không đổi API cũ. Unit/security và PostgreSQL concurrency/rollback/migration; frontend E2E riêng. Không cần test ở task chỉ thiết kế này.

## 8. Quyết định cần chốt
| Quyết định | Đề xuất | Trạng thái |
|---|---|---|
| Ai tạo/xác nhận xuất? | ACCOUNTANT và SHOP_OWNER | CONFIRMED: người dùng chốt |
| Điều kiện tiền/cọc trước xuất | Kế toán và owner trao đổi ngoài hệ thống; không yêu cầu cọc hoặc thanh toán đủ, không thêm bước duyệt. Giữ chặn order DRAFT/CANCELLED/COMPLETED và mọi kiểm tra tồn/COD | CONFIRMED: người dùng chốt |
| Phạm vi kho | WH_MAIN trước | Cần xác nhận |
| COD từng đợt | Người có quyền nhập; Finance kiểm tra tổng và balance khi thu | Cần chốt |
| Serial và combo | Inventory quản lý serial thật; Sales cung cấp dòng thực xuất | Cần chủ module xác minh contract |
| Public contracts/khóa và audit | Additive; không sửa hành vi chức năng khác | Design review pending |

Không triển khai khi các quyết định ảnh hưởng tồn/tiền còn mở. Người dùng đã chốt batch_no, không trip/stop, nhập hoàn thuộc kho, ảnh local tạm, hoàn tất giao và xác nhận COD riêng; không hỏi lại các điểm này.
