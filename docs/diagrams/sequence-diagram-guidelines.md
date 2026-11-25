# Quy chuẩn vẽ Sequence Diagram cho AquaSuite

## Mục đích và phạm vi

Tài liệu này là hướng dẫn bắt buộc trước khi phân tích hoặc vẽ Sequence Diagram cho một Use Case của AquaSuite. Sơ đồ phải ưu tiên ba mục tiêu: thể hiện đúng luồng nghiệp vụ, phản ánh kiến trúc source hiện tại và đủ đơn giản để người đọc nhận ra trách nhiệm của từng thành phần.

Quy chuẩn áp dụng cho cả sơ đồ PlantUML, Draw.io và Visual Paradigm. Khi source code và tài liệu cũ khác nhau, phải kiểm tra source hiện tại trước khi xác định lifeline, operation, response và exception.

## Nguyên tắc tổng quát

Mỗi sơ đồ tập trung vào một Use Case hoặc một luồng nghiệp vụ có ranh giới rõ. Không biến Sequence Diagram thành sơ đồ toàn bộ class hoặc toàn bộ hạ tầng của hệ thống.

Sơ đồ cần thể hiện:

- Actor khởi tạo Use Case.
- Boundary nhận thao tác từ Actor.
- Controller tiếp nhận API request.
- Service điều phối logic nghiệp vụ.
- Các service hoặc lookup interface cần thiết của domain liên quan.
- Repository interface đọc hoặc lưu aggregate chính.
- Nhánh thành công và các nhánh lỗi nghiệp vụ quan trọng.
- API request, response và exception cần thiết.

Sơ đồ thường không cần thể hiện mapper, helper thuần kỹ thuật, Spring proxy, JPA implementation, security filter, database driver, getter/setter hoặc từng câu lệnh SQL. Chỉ thêm thành phần phụ khi nó quyết định hành vi của Use Case.

## Kết hợp BCE và Layered Architecture

Luồng cơ bản được bố trí từ trái sang phải:

```text
Actor
→ <<boundary>> UI
→ <<controller>> Controller
→ <<service>> Service interface
→ <<service>> Domain/Lookup interface liên quan
→ <<repository>> Repository interface
```

Ánh xạ BCE vào kiến trúc AquaSuite:

| Vai trò | Stereotype đề xuất | Trách nhiệm |
|---|---|---|
| Người dùng hoặc hệ thống ngoài | Actor | Khởi tạo hoặc nhận kết quả Use Case |
| Màn hình hoặc client UI | `<<boundary>>` | Thu thập input và hiển thị output |
| REST Controller | `<<controller>>` | Nhận request, gọi service và trả HTTP response |
| Business Service | `<<service>>` | Điều phối và thực thi logic nghiệp vụ |
| Repository | `<<repository>>` | Truy xuất hoặc lưu dữ liệu thuộc domain |
| Domain object cần thể hiện | `<<entity>>` | Mang trạng thái và hành vi nghiệp vụ quan trọng |
| Hệ thống tích hợp ngoài | `<<external system>>` | Cung cấp dịch vụ ngoài ranh giới AquaSuite |

Tên lifeline nên thể hiện cả tên object và abstraction mà caller sử dụng:

```text
quotationUI: QuotationUI
<<boundary>>

quotationController: QuotationController
<<controller>>

quotationService: QuotationService
<<service>>

quotationRepository: QuotationRepository
<<repository>>
```

## Giao tiếp qua interface

Các object phải giao tiếp qua abstraction phù hợp với kiến trúc:

- UI gọi Controller qua HTTP API.
- Controller gọi service interface, không biểu diễn gọi trực tiếp class `*ServiceImpl`.
- Service gọi service hoặc lookup interface của domain liên quan.
- Service gọi repository interface, không biểu diễn Spring Data/JPA implementation.
- UI hoặc Controller không truy cập Repository trực tiếp.
- Không biểu diễn một domain truy cập trực tiếp bảng dữ liệu của domain khác.

Tên message phải là operation có ý nghĩa và, khi sơ đồ mô tả implementation hiện tại, phải đối chiếu với tên method trong source:

```text
createQuotation(request)
getCustomerById(customerId)
getSkuSummaries(variantIds)
getStockSummaries(variantIds)
save(quotation)
```

Không dùng tên chung chung như `execute()`, `handle()` hoặc `process()` nếu source hoặc nghiệp vụ có operation rõ nghĩa hơn.

## Luồng nghiệp vụ chính

Happy path phải đọc được liên tục từ trên xuống dưới:

1. Actor nhập hoặc gửi dữ liệu.
2. UI gọi API và truyền request DTO.
3. Controller gọi service interface.
4. Service kiểm tra điều kiện nghiệp vụ.
5. Service lấy dữ liệu từ các domain liên quan.
6. Service tính toán hoặc đưa ra quyết định nghiệp vụ.
7. Repository lưu dữ liệu nếu Use Case có thay đổi trạng thái.
8. Service trả response DTO.
9. Controller trả HTTP `ApiResponse<T>`.
10. UI hiển thị kết quả cho Actor.

Chỉ dùng self-message khi cần làm rõ một bước nghiệp vụ quan trọng, ví dụ:

```text
quotationService -> quotationService: calculateFinancials(...)
```

Không tạo self-message riêng cho từng phép tính hoặc mapping nhỏ.

## Combined Fragment và guard condition

### `alt`

Dùng khi tồn tại từ hai nhánh loại trừ nhau. Mỗi nhánh phải có guard condition trong dấu `[]`.

```text
alt [discountRate <= 10%]
    quotationService -> quotationService: setRequiresApproval(false)
else [discountRate > 10%]
    quotationService -> quotationService: setRequiresApproval(true)
end
```

Một điều kiện có nhiều kết quả lỗi có thể biểu diễn như sau:

```text
alt [customer tồn tại và ACTIVE]
    tiếp tục tạo báo giá
else [customer không tồn tại]
    throw AppException(CUSTOMER_NOT_FOUND)
else [customer không ACTIVE]
    throw AppException(CUSTOMER_NOT_ACTIVE)
end
```

### `break`

Dùng khi điều kiện làm kết thúc toàn bộ phần còn lại của Use Case. Sau nhánh `break` không được tiếp tục bước lưu dữ liệu hoặc trả response thành công.

```text
break [request không hợp lệ]
    controller --> UI: HTTP 400, ApiResponse(code = "SYS_003")
    UI --> Actor: Hiển thị lỗi dữ liệu đầu vào
end
```

Nếu cần trình bày đồng thời nhánh hợp lệ và không hợp lệ, ưu tiên `alt`. Dùng `break` khi mục tiêu là nhấn mạnh kết thúc sớm.

### `opt`

Dùng cho hành động tùy chọn chỉ có một nhánh:

```text
opt [có phí vận chuyển]
    service -> service: calculateShippingFee()
end
```

### `loop`

Dùng khi cần thể hiện hành vi lặp có ý nghĩa:

```text
loop [mỗi quotation item]
    service -> service: validateSkuAndCalculateLineAmount(item)
end
```

Không dùng `loop` nếu source thực hiện một lời gọi bulk đơn giản như `getSkuSummaries(variantIds)` và chi tiết từng phần tử không quan trọng với Use Case.

## Request, response và exception

Request DTO được thể hiện tại lời gọi API:

```text
quotationUI -> quotationController:
POST /api/v1/sales/quotations
CreateQuotationRequest
```

Response thành công cần ghi HTTP status, envelope và dữ liệu chính:

```text
quotationController --> quotationUI:
HTTP 201
ApiResponse<QuotationResponse>
code = "SUCCESS"
```

Response lỗi cần ghi HTTP status và error code khi các giá trị này đã được source hoặc contract xác định:

```text
quotationController --> quotationUI:
HTTP 404
ApiResponse(success = false, code = "CUSTOMER_NOT_FOUND")
```

Exception được đặt tại operation phát hiện lỗi:

```text
quotationService --> quotationController:
throw AppException(CUSTOMER_NOT_FOUND)
```

Trong source AquaSuite, `GlobalExceptionHandler` chuyển `AppException` và các exception kỹ thuật thành `ApiResponse`. Với sơ đồ đơn giản, có thể không tạo lifeline riêng cho handler và ghi chú kết quả HTTP ngay tại response. Chỉ thêm lifeline `<<controller advice>> GlobalExceptionHandler` khi xử lý lỗi là nội dung chính của Use Case.

Không tự đặt HTTP status hoặc error code chưa tồn tại. Nếu requirement và source chưa thống nhất, ghi rõ điểm cần xác nhận trước khi hoàn thiện sơ đồ.

## Mũi tên, activation và thứ tự message

- Mũi tên liền biểu diễn lời gọi operation.
- Mũi tên nét đứt biểu diễn return value hoặc exception propagation.
- Activation bắt đầu khi lifeline nhận quyền xử lý và kết thúc khi trả quyền cho caller.
- Activation của Service bao phủ các bước kiểm tra, tính toán và lưu dữ liệu do Service điều phối.
- Message được đánh số phân cấp nếu công cụ và mẫu chung của nhóm đang sử dụng cách đánh số này.

Ví dụ thứ tự:

```text
1: Enter quotation information
1.1: POST /api/v1/sales/quotations
1.2: createQuotation(request)
1.2.1: getCustomerById(customerId)
1.2.2: getSkuSummaries(variantIds)
1.2.3: save(quotation)
1.3: HTTP 201 ApiResponse<QuotationResponse>
1.4: Display success message
```

Message return không cần ghi lại toàn bộ cấu trúc object. Chỉ ghi kiểu hoặc dữ liệu có ảnh hưởng tới quyết định tiếp theo, ví dụ `Customer`, `List<SkuSummary>` hoặc `QuotationResponse`.

## Mức chi tiết phù hợp

Một lifeline chỉ nên xuất hiện khi thỏa ít nhất một điều kiện:

- Nhận hoặc trả dữ liệu qua boundary của Use Case.
- Sở hữu một quyết định nghiệp vụ quan trọng.
- Cung cấp dữ liệu cần thiết cho quyết định tiếp theo.
- Lưu thay đổi trạng thái chính.
- Đại diện cho hệ thống ngoài có ảnh hưởng tới kết quả Use Case.

Nếu bỏ một thành phần mà người đọc vẫn hiểu chính xác nghiệp vụ và trách nhiệm giữa các layer, thành phần đó nên được bỏ khỏi sơ đồ.

## Quy trình phân tích trước khi vẽ

1. Xác định Actor, trigger, precondition và kết quả mong muốn của UC.
2. Tìm endpoint thực tế trong Controller.
3. Xác định request DTO, validation và authorization áp dụng.
4. Lần theo Controller đến service interface và implementation.
5. Ghi lại các quyết định nghiệp vụ theo đúng thứ tự thực thi.
6. Xác định dependency chéo domain và repository được sử dụng.
7. Xác định transaction boundary, event hoặc external integration nếu chúng ảnh hưởng tới kết quả UC.
8. Liệt kê happy path, alternative flow và exception flow.
9. Đối chiếu HTTP response, `ApiResponse`, error code và status với source.
10. Đơn giản hóa thành các lifeline cần thiết rồi mới vẽ.

## Checklist kiểm tra sơ đồ

Trước khi bàn giao một Sequence Diagram, kiểm tra:

- [ ] Sơ đồ chỉ mô tả một UC hoặc một luồng có ranh giới rõ.
- [ ] Actor và boundary được đặt ở phía trái.
- [ ] Luồng chính đi qua Controller, Service và Repository đúng trách nhiệm.
- [ ] Caller giao tiếp qua service/repository interface, không vẽ implementation không cần thiết.
- [ ] Lifeline có tên và stereotype rõ ràng.
- [ ] Operation, endpoint và DTO đã được đối chiếu với source hiện tại.
- [ ] `alt`, `break`, `opt` và `loop` được dùng đúng ngữ nghĩa.
- [ ] Mỗi nhánh có guard condition rõ ràng.
- [ ] Nhánh lỗi kết thúc đúng chỗ và không đi tiếp vào success flow.
- [ ] Request, response, HTTP status và error code quan trọng được thể hiện.
- [ ] Không tự đặt behavior hoặc error code chưa được source/requirement xác nhận.
- [ ] Mũi tên call/return và activation bar nhất quán.
- [ ] Mapper, helper và chi tiết framework không cần thiết đã được loại bỏ.
- [ ] Sơ đồ đủ đơn giản để đọc nhưng không làm mất quyết định nghiệp vụ chính.

## Template tối thiểu cho một UC

```text
Actor -> UI: Thực hiện thao tác
UI -> Controller: HTTP request + RequestDTO

break [input không hợp lệ]
    Controller --> UI: HTTP 400 + ApiResponse lỗi
    UI --> Actor: Hiển thị lỗi
end

Controller -> Service: executeUseCase(request)
Service -> RelatedService: getRequiredData(id)
RelatedService --> Service: RequiredData

alt [điều kiện nghiệp vụ hợp lệ]
    Service -> Repository: save(entity)
    Repository --> Service: savedEntity
    Service --> Controller: ResponseDTO
    Controller --> UI: HTTP 2xx + ApiResponse<ResponseDTO>
    UI --> Actor: Hiển thị thành công
else [điều kiện nghiệp vụ không hợp lệ]
    Service --> Controller: throw AppException(ERROR_CODE)
    Controller --> UI: HTTP 4xx + ApiResponse lỗi
    UI --> Actor: Hiển thị thông báo lỗi
end
```

Template là khung tối thiểu, không phải yêu cầu phải có mọi lifeline hoặc combined fragment. Sơ đồ cuối cùng phải phản ánh đúng behavior của UC đang phân tích.
