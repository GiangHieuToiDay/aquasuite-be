# Module Contracts and Integration Conventions

## Purpose and Scope

Tài liệu này quy định các tiêu chuẩn kỹ thuật khi thiết kế và hiện thực hóa các hợp đồng giao tiếp giữa các feature trong hệ thống AquaSuite. Giao tiếp giữa các module bao gồm hai hình thức chính: **Synchronous Java API** (gọi hàm trực tiếp qua Interface) và **Domain Events** (giao tiếp tách rời hướng tới tính nhất quán sau cùng - eventual consistency).

---

## Synchronous Contract Specification

### Java API Design Rules

1. **Vị trí và Đóng gói**:
   - Mọi Interface công khai phải được đặt trong package `com.aquasuite.ims.module.<module_name>.api`.
   - Các DTO dùng làm tham số đầu vào (`Request`) hoặc kết quả trả về (`Response`) phải nằm trong `com.aquasuite.ims.module.<module_name>.api.dto`.
   - Các class triển khai nội bộ phải nằm trong `com.aquasuite.ims.module.<module_name>.internal..`.
   - **Tuyệt đối không** dùng JPA Entity làm kiểu dữ liệu tham số hoặc trả về trong public API.
2. **Nguyên tắc Thiết kế và Ranh giới Kiến trúc**:
   - Giao diện được tối ưu hóa theo nhu cầu của Consumer, không phơi bày toàn bộ cấu trúc bảng dữ liệu bên trong.
   - Luôn định nghĩa rõ ràng các mã ngoại lệ nghiệp vụ có thể ném ra (`AppException` với `BaseErrorCode`).
   - Interface chỉ thực hiện các thao tác đọc dữ liệu hoặc các tác vụ ghi mang tính bắt buộc đồng bộ cao (như kiểm tra điều kiện tiên quyết).
   - **Phân biệt giữa Java Visibility và Architectural Visibility**: Một số class hoặc method trong `.internal` có thể bắt buộc phải khai báo `public` trong mã nguồn Java do yêu cầu của Spring Bean lifecycle, CGLIB proxies, hoặc JPA runtime reflection. Tuy nhiên, về mặt kiến trúc, các type này hoàn toàn là private đối với các module khác. Cấm các module bên ngoài import bất kỳ type nào từ `.internal`.
   - **Kiểm định Kiến trúc qua ArchUnit (Future Roadmap)**: Để bảo đảm ranh giới `.api` và `.internal` không bị vi phạm trong quá trình phát triển dài hạn, dự án định hướng sử dụng ArchUnit để tự động hóa việc kiểm tra quy tắc phụ thuộc giữa các module. Việc thêm dependency ArchUnit và thiết lập bộ test kiến trúc sẽ được triển khai trong một feature riêng biệt được phê duyệt; tài liệu hiện tại chỉ đóng vai trò đặc tả quy tắc và roadmap.

### Synchronous Interface Template

```java
package com.aquasuite.ims.module.sample.api;

// Template pseudocode minh họa - ngoại lệ kế thừa BaseErrorCode (hiện trạng: com.aquasuite.ims.share.exception.AppException)
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.module.sample.api.dto.SampleQueryRequest;
import com.aquasuite.ims.module.sample.api.dto.SampleResponse;

/**
 * Public Contract Interface mẫu cho Module Sample (Architecture Template).
 * Được hiện thực hóa bởi service trong package internal của Module Sample.
 */
public interface SampleApi {

    /**
     * Tra cứu dữ liệu mẫu phục vụ Consumer.
     *
     * @param request Thông tin truy vấn tối thiểu
     * @return DTO kết quả đã được đóng gói
     * @throws AppException khi tài nguyên không tồn tại hoặc dữ liệu không hợp lệ
     */
    SampleResponse getSampleData(SampleQueryRequest request);
}
```

### Required Fields in Synchronous Contract Documentation

Khi lập đặc tả cho một phương thức Java API giữa các module, bản đặc tả bắt buộc phải thể hiện các yếu tố sau:

| Field Name | Description | Mandatory |
|---|---|---|
| **Contract Name** | Tên interface và tên phương thức | Yes |
| **Caller / Consumer** | Tên module hoặc thành phần gọi hàm | Yes |
| **Callee / Provider** | Module sở hữu và thực thi interface | Yes |
| **Input Parameters** | DTO đầu vào, các trường bắt buộc, ràng buộc validation | Yes |
| **Return Value** | DTO trả về, giá trị nullability | Yes |
| **Error Conditions** | Danh sách lỗi nghiệp vụ (`ErrorCode`), HTTP status tương ứng nếu nổi lên controller | Yes |
| **Authorization Context** | Yêu cầu về quyền (`Permission`), ngữ cảnh người dùng hiện tại (`CurrentUser`) | Yes |
| **Transaction Behavior** | Chế độ transaction (`SUPPORTS`, `REQUIRED`, hay Read-Only) | Yes |
| **Versioning & Compatibility** | Chiến lược tương thích khi thay đổi DTO đầu vào/đầu ra | Yes |

---

## Domain Event Specification (Decoupled Communication)

### Event Design Rules

1. **Vị trí và Kiểu dữ liệu**:
   - Tất cả Domain Events công khai phải được khai báo bằng Java `record` và đặt trong `com.aquasuite.ims.module.<module_name>.api.event`.
   - Tên event phải ở thể quá khứ (Past Tense), ví dụ: `OrderConfirmedEvent`, `DeliveryBatchDispatchedEvent`.
2. **Payload tối thiểu (Minimal Payload)**:
   - Event chỉ chứa các khóa định danh chính (`orderId`, `customerId`), dấu thời gian (`occurredAt`), và các thông tin cần thiết nhất cho tác vụ kế tiếp.
   - Tránh nhồi nhét toàn bộ snapshot dữ liệu lớn hoặc thông tin nhạy cảm (như mật khẩu, dữ liệu thẻ) vào payload.
3. **Idempotency & Deduplication**:
   - Mỗi event bắt buộc phải có một mã định danh duy nhất (`eventId` kiểu `UUID`) hoặc khóa nghiệp vụ kết hợp (`orderId` + `phase`) để Consumer kiểm tra trùng lặp trước khi xử lý.
4. **Phân biệt Chế độ Luồng và Giai đoạn Transaction**:
   - **Chế độ thực thi luồng**: Mặc định trong Spring Framework, Application Events chạy **đồng bộ trên cùng thread của Publisher**. Việc thực thi bất đồng bộ đa luồng (multi-threaded async) đòi hỏi cấu hình `TaskExecutor` và `@Async` riêng biệt.
   - **Giai đoạn kích hoạt Transaction**: Annotation `@TransactionalEventListener(phase = AFTER_COMMIT)` chỉ bảo đảm listener chạy sau khi transaction nguồn commit thành công. Nó **không biến event thành hàng đợi tin nhắn bền vững (durable message queue)**. Do đó, nếu tiến trình gặp sự cố, event có thể bị mất nếu không có hàng đợi hoặc bảng outbox chuyên dụng.

### Domain Event Template

```java
package com.aquasuite.ims.module.sample.api.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Public Domain Event phát ra khi một sự kiện nghiệp vụ trong Module Sample hoàn tất.
 */
public record SampleBusinessCompletedEvent(
        UUID eventId,
        UUID aggregateId,
        String businessCode,
        Instant occurredAt
) {
    public SampleBusinessCompletedEvent(UUID aggregateId, String businessCode) {
        this(UUID.randomUUID(), aggregateId, businessCode, Instant.now());
    }
}
```

### Required Fields in Event Contract Documentation

Mỗi Domain Event trong hệ thống khi được đặc tả bắt buộc phải có đầy đủ các thông tin:

| Field Name | Description | Mandatory |
|---|---|---|
| **Event Name** | Tên đầy đủ của Java Record | Yes |
| **Publisher** | Module phát sự kiện | Yes |
| **Expected Consumers** | Danh sách các module lắng nghe sự kiện này | Yes |
| **Payload Attributes** | Danh sách các trường dữ liệu trong record, kiểu dữ liệu và ý nghĩa | Yes |
| **Publish Timing** | Thời điểm chính xác sự kiện được bắn ra trong use case của Publisher | Yes |
| **Transaction Phase** | Giai đoạn transaction kích hoạt (mặc định: `TransactionPhase.AFTER_COMMIT`) | Yes |
| **Idempotency Key** | Trường dữ liệu được Consumer sử dụng để lọc bản tin trùng lặp | Yes |
| **Retry & Replay Policy** | Chính sách thử lại khi Consumer gặp lỗi (Dead Letter, retry count, backoff) | Yes |
| **Failure Observability** | Cách thức ghi log cảnh báo hoặc lưu bản ghi lỗi khi listener thất bại | Yes |
| **Pending Business State** | Trạng thái nghiệp vụ của thực thể trong thời gian chờ các Consumer xử lý | Yes |

---

## Event Listener Implementation Guidelines

Do `@TransactionalEventListener(phase = AFTER_COMMIT)` kích hoạt sau khi transaction nguồn đã kết thúc, thread hiện tại không còn transaction đang hoạt động. Nếu Listener cần ghi dữ liệu vào database, nó bắt buộc phải tạo transaction mới:

```java
package com.aquasuite.ims.module.sample.internal.listener;

import com.aquasuite.ims.module.sample.api.event.SampleBusinessCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SampleEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(SampleBusinessCompletedEvent event) {
        log.info("Receiving event {} for aggregate {}", event.eventId(), event.aggregateId());

        // 1. Kiểm tra tính Idempotent (Deduplication)
        // 2. Thực hiện cập nhật nghiệp vụ
        // 3. Bắt ngoại lệ và ghi log lỗi vận hành nếu thất bại, tránh ném lỗi làm chết thread ngoài ý muốn
    }
}
```

---

## Open Consistency Decision Gates

Các luồng nghiệp vụ phức tạp dưới đây chưa có đủ dữ liệu kiểm chứng và yêu cầu thống nhất nghiệp vụ để quyết định kiến trúc cứng; chúng được ghi nhận là các điểm quyết định đang mở (`TBD`):

### 1. Order Confirmation and Stock Reservation (`sales` <-> `inventory`)
- **Tình huống**: Khách hàng ấn xác nhận đơn hàng (`Confirm Order`).
- **Lựa chọn 1 (Atomic Transaction)**: `sales` gọi đồng bộ `inventory.reserveStock()` trong cùng một transaction. Ưu điểm: Không có nguy cơ bán vượt tồn kho (overselling). Nhược điểm: Phụ thuộc chặt chẽ giữa hai service, thời gian giữ lock dài.
- **Lựa chọn 2 (Eventual Consistency / SAGA Choreography)**: `sales` phát `OrderConfirmedEvent` với trạng thái đơn `PENDING_RESERVATION`. `inventory` lắng nghe và trừ tồn kho, sau đó phát lại sự kiện kết quả (`StockReservedEvent` hoặc `StockReservationFailedEvent`).
- **Trạng thái**: `TBD`. Quyết định thuộc về Lead Developer và Product Owner dựa trên kịch bản flash-sale / đồng bộ ca bán hàng tại quầy.

### 2. Payment Status and Finance Synchronization (`finance` -> `sales`, `crm`)
- **Tình huống**: Kế toán xác nhận phiếu thu tiền mặt hoặc hệ thống nhận webhook thanh toán VietQR thành công.
- **Cơ chế đề xuất**: `finance` lưu chứng từ thanh toán và phát `OrderPaymentStatusChangedEvent`. `sales` lắng nghe sự kiện để cập nhật `paymentStatus` của đơn hàng.
- **Xử lý rủi ro**: Trong trường hợp listener của `sales` gặp lỗi, bảng đối soát của `finance` vẫn là nguồn sự thật (Single Source of Truth). Cần một tiến trình kiểm tra định kỳ (reconciliation batch job) để đồng bộ trạng thái bị lệch.
- **Trạng thái**: `TBD` chi tiết cơ chế batch reconciliation.

### 3. Outbound Delivery and Serial Number Tracking (`fulfillment` -> `inventory`, `sales`)
- **Tình huống**: Kho xuất một đợt hàng có chứa sản phẩm quản lý số Serial (`isSerialTracked = true`).
- **Cơ chế đề xuất**: `fulfillment` phát `DeliveryBatchDispatchedEvent` để `inventory` trừ số lượng thực tế (`physicalQty`) và giải phóng lượng đặt trước (`reservedQty`).
- **Trạng thái**: `TBD` về cơ chế rollback khi đơn hàng bị Shipper hoàn trả do không giao được đến tay người nhận.

### 4. Warranty Activation on Delivery (`fulfillment` -> `servicedesk`)
- **Tình huống**: Shipper giao hàng thành công, kích hoạt sổ bảo hành điện tử.
- **Cơ chế đề xuất**: Sự kiện `DeliveryBatchCompletedEvent` mang theo danh sách Serial numbers. `servicedesk` lắng nghe và tạo `WarrantyRecord`.
- **Rủi ro**: Nếu việc tạo sổ bảo hành thất bại do lỗi hệ thống, đơn hàng vẫn phải được ghi nhận là giao thành công. Cần bảng hàng đợi công việc chờ xử lý (Outbox / Dead Letter Table) trong `servicedesk`.
- **Trạng thái**: `TBD` đặc tả bảng lưu vết công việc bảo hành chờ retry.
