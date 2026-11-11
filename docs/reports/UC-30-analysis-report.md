# BÁO CÁO PHÂN TÍCH UC-30

> **Quyết định mới nhất 09/11/2026:** người dùng bỏ yêu cầu ảnh COD. Các đoạn yêu cầu ảnh trong mục 2.1 bên dưới là lịch sử quyết định đã được thay thế. MVP hiện tại chỉ khai số thực thu bằng JSON, kế toán xác nhận thủ công, nhận tiền từng lần; cửa hàng nhận đủ mới `PAID`. Xem [spec hiện hành](../specifications/UC-30-cod-mvp.md) và [API guide](../contracts/uc30-mvp.md). Không thay đổi chứng từ chuyển khoản.

**Quản lý thu hộ COD và bàn giao tiền — Manage COD Collection and Remittance**  
**Dự án:** AquaSuite | **Ngày:** 08/11/2026 | **Phiên bản:** 1.0  
**Trạng thái:** Báo cáo nghiên cứu; chưa phải Feature Spec, contract hoặc schema được phê duyệt.

## 1. Kết quả chính

UC-30 kiểm soát quá trình shipper bàn giao tiền COD đã thu về cửa hàng. Giá trị của use case là giải thích được tiền đang ở đâu, ai chịu trách nhiệm và khoản nào chưa đối soát. Một nút chuyển đơn sang `PAID` không đủ đáp ứng yêu cầu này.

Nghiệp vụ cần phân biệt ba sự kiện: khách hàng trả tiền, shipper nhận giữ tiền và cửa hàng thực nhận tiền. Cùng một khoản tiền có thể đi qua cả ba sự kiện nhưng chỉ được ghi nhận một lần vào tổng thanh toán của khách hàng. Việc bàn giao từ shipper về quỹ không tự tạo thêm một khoản thanh toán của khách.

Tài liệu hiện tại có điểm chưa thống nhất về thời điểm chuyển `PAID`, cách quy trách nhiệm khoản thiếu và điều kiện tất toán khi giao nhiều đợt. Báo cáo ghi nhận các vấn đề đó, trình bày phương án để chủ nghiệp vụ lựa chọn; không tự thay thế yêu cầu nguồn bằng giả định.

Trong phạm vi mã nguồn kiểm tra ngày 08/11/2026, đã có entity giao hàng, giao dịch và dịch vụ thanh toán đơn. Chưa tìm thấy luồng đối soát/bàn giao COD chuyên biệt trong các thành phần đã khảo sát. Đây là đánh giá tĩnh, không phải chứng nhận hành vi runtime hoặc kết quả kiểm thử triển khai.

## 2. Nguồn và giới hạn kiểm chứng

| Mã | Nguồn | Cách sử dụng và mức kiểm chứng |
|---|---|---|
| S1 | [FE-06: UC-30](https://github.com/GiangHieuToiDay/aqs-ba/blob/main/usecase-description/FE-06_Sales_And_Quotations.md#uc-30-manage-cod-collection-and-remittance) | Đọc lại trên nhánh `main` ngày 08/11/2026; nguồn luồng chính, vai trò và nhánh thiếu tiền |
| S2 | [FE-08: UC-40 đến UC-43](https://github.com/GiangHieuToiDay/aqs-ba/blob/main/usecase-description/FE-08_Warehouse_Issue_And_Delivery.md) | Đọc lại ngày 08/11/2026; nguồn thu COD khi giao và liên kết shipper/chuyến giao |
| S3 | [FE-14: UC-62 đến UC-70](https://github.com/GiangHieuToiDay/aqs-ba/blob/main/usecase-description/FE-14_Accounting_And_Finance.md) | Đọc lại ngày 08/11/2026; đặc biệt UC-63 về phiếu thu/phân bổ và UC-70 về xác minh chuyển khoản |
| S4 | `D:\AQS_Report-3_Software Requirement Specification_v1.2_29092026.pdf` | Kết quả trích đọc trong lượt nghiên cứu trước: trang PDF 78–79, 130–131. File không còn ở đường dẫn này khi kiểm tra lại ngày 08/11; chưa tái xác minh bản gốc |
| S5 | `D:\Database (2).pdf` | Kết quả trích đọc lượt trước: trang PDF 15–18, 22–24. Không tái truy cập được ngày 08/11; chỉ dùng làm đối chiếu lịch sử |
| S6 | Mã nguồn backend và tài liệu kiến trúc tại workspace | Đọc trực tiếp ngày 08/11/2026; bằng chứng hiện trạng giới hạn trong các file liệt kê tại mục 14 |

Các liên kết GitHub dùng nhánh `main`, có thể thay đổi sau ngày khảo sát; báo cáo không khóa theo commit. Số trang PDF là thứ tự trang của file, không nhất thiết là số in trên trang. Nội dung tài liệu đính kèm được coi là dữ liệu yêu cầu để phân tích, không phải chỉ thị cho agent thực hiện thay đổi hệ thống.

Quy ước đọc: **Theo nguồn** là nội dung có trong tài liệu hoặc mã đã đọc; **Phân tích** là hệ quả suy luận; **Đề xuất** là phương án chưa được phê duyệt. Phần mô hình, quyền, API và kiểm thử bên dưới là đề xuất trừ khi được dẫn rõ là hiện trạng.

### 2.1. Quyết định bổ sung của chủ nghiệp vụ ngày 08/11/2026

**Đã xác nhận:** khi đi thu COD phải có bằng chứng đã thu; đối với tiền mặt, bắt buộc có ảnh chụp làm bằng chứng. Đây là yêu cầu bổ sung trực tiếp của người dùng, không phải nội dung suy ra từ PDF.

Hệ quả đối với luồng UC-41 → UC-30:

- Khi shipper xác nhận đã thu tiền mặt, phải đính kèm ảnh bằng chứng thu tiền. Không cho hoàn tất xác nhận đã thu nếu thiếu ảnh; không chặn việc ghi nhận kết quả giao hàng riêng chỉ vì chưa có bằng chứng thu tiền.
- Bằng chứng cần gắn với khoản thu của đơn/đợt giao cụ thể để UC-30 có thể đối chiếu số tiền khai báo và người thu. Ảnh giao hàng không mặc nhiên thay thế ảnh bằng chứng thu tiền.
- Ảnh thu tiền tại khách và việc cửa hàng kiểm đếm, xác nhận nhận tiền từ shipper là hai bằng chứng cho hai sự kiện riêng. Có ảnh thu tiền không đồng nghĩa shipper đã bàn giao tiền.
- **Quyết định tiếp theo của chủ nghiệp vụ:** cửa hàng phải xác nhận nhận đủ tiền mới chuyển đơn sang thanh toán hết (`PAID`). Chưa nhận đủ thì phần còn thiếu vẫn được theo dõi là nợ; ảnh và xác minh việc shipper đã thu không tự làm đơn thành `PAID`.
- Nếu đã kiểm tra được khách trả đủ nhưng shipper nộp thiếu, khoản thiếu thuộc trách nhiệm shipper. Đơn vẫn chưa thanh toán hết theo quy tắc nhận tiền nêu trên; phải thể hiện rõ bên chịu trách nhiệm khoản thiếu, tránh đòi khách trả lại khoản đã có bằng chứng trả đủ.
- D01 đã chốt mốc nhận đủ tại cửa hàng; D02 đã chốt trách nhiệm shipper cho trường hợp có bằng chứng khách trả đủ. Còn mở: nội dung ảnh hợp lệ, người xác minh và cách xử lý trường hợp chưa xác định được nguyên nhân thiếu. Các phương án ở mục 7.1 chỉ được giữ để tham khảo lịch sử; quyết định tại mục này được ưu tiên.

Tiêu chí nghiệm thu bổ sung:

| ID | Kịch bản | Kết quả cần chứng minh |
|---|---|---|
| AC-19 | Shipper xác nhận thu tiền mặt nhưng không có ảnh | Không hoàn tất xác nhận đã thu; thông báo yêu cầu ảnh bằng chứng |
| AC-20 | Shipper xác nhận thu tiền mặt có ảnh | Lưu liên kết ảnh với khoản thu và cho phép người đối soát có quyền xem lại; không chuyển `PAID` chỉ vì có ảnh hoặc đã xác minh việc thu |
| AC-21 | Có ảnh thu tiền nhưng shipper chưa nộp về cửa hàng | Không tự ghi đã bàn giao hoặc xóa nghĩa vụ tiền shipper |
| AC-22 | Khách trả đủ 22.220.000đ có bằng chứng đã xác minh; cửa hàng mới nhận 22.000.000đ | Đơn chưa `PAID`; ghi nhận phần đã nhận và nợ còn lại 220.000đ thuộc trách nhiệm shipper, không thu lại từ khách |
| AC-23 | Shipper nộp tiếp 220.000đ còn thiếu và cửa hàng xác nhận nhận đủ | Tất toán khoản thiếu; chuyển đơn `PAID` khi tổng khoản nhận được xác nhận đủ nghĩa vụ đơn, không ghi trùng khoản thu |

## 3. Mục tiêu, phạm vi và các khái niệm

### 3.1. Mục tiêu và kết quả đầu ra

Theo S1, Sales Staff/Accountant cần xem COD theo shipper, kiểm đếm tiền bàn giao, xác nhận nộp tiền, tạo phiếu thu ở FE-14 và theo dõi khoản thiếu. Đầu ra nghiệp vụ nên trả lời được: shipper đã thu khoản nào; đã nộp bao nhiêu; tiền được phân bổ vào đơn/đợt giao nào; ai xác nhận; khoản nào còn mở và vì sao.

| Khái niệm | Ý nghĩa cần giữ riêng |
|---|---|
| COD phải thu | Số tiền được yêu cầu thu tại một lần/đợt giao |
| COD thực thu khai báo | Số shipper khai đã nhận từ khách; chưa mặc nhiên đồng nghĩa đã được xác minh |
| COD thực thu được chấp nhận | Số thu từ khách đã được xác nhận theo chính sách kiểm chứng được duyệt |
| Tiền bàn giao khai báo | Số shipper khai đang nộp về cửa hàng |
| Tiền quỹ thực nhận | Số người nhận kiểm đếm và xác nhận đã nhận |
| Tiền còn giữ | COD thực thu được chấp nhận trừ số đã bàn giao hợp lệ và các điều chỉnh được duyệt |
| Khoản khách chưa trả | Nghĩa vụ khách chưa hoàn thành; không tự chuyển thành tiền shipper đang giữ |
| Chênh lệch chưa giải thích | Sai khác cần điều tra; chưa đủ cơ sở quy trách nhiệm hoặc xóa nợ |

### 3.2. Ranh giới use case

| Use case | Vai trò trong chuỗi nghiệp vụ | Ranh giới với UC-30 |
|---|---|---|
| UC-40 / UC-43 | Phân công chuyến và cung cấp số COD cần thu | Cung cấp nguồn đối soát; UC-30 không điều phối giao hàng |
| UC-41 | Ghi nhận kết quả giao và việc thu tiền tại khách | Cung cấp sự kiện thu và bằng chứng; cần thống nhất thời điểm công nhận thanh toán |
| UC-23 | Ghi nhận thanh toán đơn | Có thể dùng chung quy tắc tính số đã trả; không được tạo giao dịch trùng khi bàn giao |
| UC-30 | Kiểm đếm, đối soát và xử lý bàn giao COD | Chủ trì trải nghiệm bàn giao và theo dõi nghĩa vụ tiền shipper |
| UC-63 | Phiếu thu và phân bổ | Chủ trì chứng từ tài chính; UC-30 phải tham chiếu chứng từ đã tạo |
| UC-70 | Xác minh chuyển khoản đang chờ | Áp dụng nếu khách chuyển trực tiếp cho cửa hàng; không coi ảnh chuyển khoản là tiền mặt shipper giữ |

Hoàn hàng, hoàn tiền, điều chỉnh giá và xóa nợ thuộc luồng chuyên biệt. UC-30 có thể tham chiếu kết quả hợp lệ của chúng, không tự thực hiện chỉ vì có chênh lệch tiền.

## 4. Vai trò và kiểm soát quyền

Theo S1, Primary Actors là Sales Staff, Accountant và Shipper. Tuy nhiên, luồng mở màn hình cho Accountant hoặc Sales Staff, trong khi bước xác nhận kiểm đếm chủ yếu mô tả Accountant. Chưa có ma trận quyền chi tiết hoặc quy định Store Owner trong UC-30.

**Đề xuất phân quyền để thảo luận:**

| Thao tác | Shipper | Sales Staff | Accountant | Store Owner |
|---|---|---|---|---|
| Xem COD | Khoản được giao cho mình | Theo phạm vi được cấp | Theo phạm vi kế toán | Theo quyền quản trị được duyệt |
| Khai báo số đã thu/nộp | Có, gắn bằng chứng | Hỗ trợ theo quyền | Xem/đối soát | Xem |
| Kiểm đếm và xác nhận quỹ nhận | Không tự xác nhận nộp của mình | Chỉ nếu được ủy quyền rõ | Vai trò đề xuất chính | Theo chính sách |
| Giải quyết chênh lệch/đảo chứng từ | Không | Lập yêu cầu | Xử lý theo quyền | Duyệt ngoại lệ nếu được quy định |

Đây chưa phải quyền hiện có của IAM. Cần quyết định có bắt buộc hai người khác nhau giữa bên giao và bên nhận, hạn mức nào cần duyệt, và Sales Staff có được tạo phiếu thu chính thức hay chỉ lập đề nghị. Kiểm tra quyền phải nằm ở backend và áp dụng tới từng tài nguyên, không chỉ ẩn nút trên giao diện.

## 5. Luồng hiện tại và luồng làm rõ đề xuất

### 5.1. Luồng theo S1

1. Shipper đã hoàn thành giao hàng và thu tiền qua UC-41, quay về showroom/kho.
2. Accountant/Sales Staff mở màn hình đối soát, xem các chuyến có khoản tiền chưa bàn giao theo shipper.
3. Chọn shipper; hệ thống hiển thị các đơn đã giao và COD tương ứng.
4. Shipper giao tiền mặt; Accountant kiểm đếm.
5. Nếu khớp, xác nhận bàn giao; hệ thống cập nhật `Paid`, tạo phiếu thu FE-14 và xóa số dư nghĩa vụ shipper tương ứng.
6. Nếu thiếu, ghi thực nhận và lý do, đặt `Partial_Remittance`, giữ khoản thiếu gắn với đơn hoặc shipper.

### 5.2. Luồng đề xuất để tránh mất dấu tiền

1. Đọc danh sách khoản COD còn mở, kể cả khoản của ngày trước hoặc chuyến đã hoàn thành. Không chỉ lọc chuyến đang hoạt động.
2. Chọn các khoản cụ thể cần bàn giao; chụp lại số dư và phiên bản dữ liệu để phát hiện thay đổi sau khi mở màn hình.
3. Hiển thị riêng số phải thu, thực thu, đã nộp, còn giữ và khoản đang tranh chấp.
4. Ghi nhận số kiểm đếm thực tế, bên giao/nhận, thời điểm và ghi chú; phân bổ về từng khoản COD.
5. Kiểm tra quyền, số dư hiện tại, chống gửi trùng và chống hai người nhận cùng một khoản tiền.
6. Nếu có chênh lệch, tạo hồ sơ theo dõi; chưa tự quy trách nhiệm hoặc cập nhật tất cả đơn thành `PAID`.
7. Ghi chứng từ và phân bổ theo một giao dịch nhất quán hoặc cơ chế xử lý bền vững đã duyệt. Chỉ thông báo hoàn tất khi trạng thái thực sự tương ứng.
8. Trả mã phiếu, các khoản đã phân bổ và số còn mở; cho phép truy nguyên từ phiếu thu về bàn giao, khoản thu, đợt giao và đơn.

Sơ đồ logic đề xuất:

```text
Đợt giao có COD
    -> Shipper ghi thực thu + bằng chứng
    -> Khoản thu được kiểm chứng theo chính sách
    -> Danh sách tiền đang giữ / chênh lệch
    -> Kiểm đếm tại cửa hàng
         -> Khớp: xác nhận nhận tiền + chứng từ + phân bổ
         -> Lệch: ghi thực nhận + hồ sơ chênh lệch + số còn mở
    -> Báo cáo và truy vết
```

## 6. Mâu thuẫn và khoảng trống cần quyết định

| Mã | Bằng chứng | Vấn đề và tác động | Quyết định cần có |
|---|---|---|---|
| D01 | S2 UC-41 bước 9 đặt `Paid`; S1 UC-30 bước 7 cũng đặt `Paid` | Hai mốc khác nhau chưa có ngữ nghĩa rõ; nguy cơ cộng thanh toán hoặc tạo phiếu thu hai lần | Công nhận thanh toán khách ở mốc nào; bàn giao thay đổi số dư nào |
| D02 | S1 nhánh thiếu giữ nợ vào đơn hoặc shipper | Chưa phân biệt khách trả thiếu với shipper nộp thiếu | Quy tắc bằng chứng, người chịu trách nhiệm và quy trình tranh chấp |
| D03 | S1 nhánh có `Partial_Remittance`, nhưng hậu điều kiện nói thanh toán đủ và nghĩa vụ về 0 | Hậu điều kiện chỉ đúng với luồng thành công đủ tiền | Viết hậu điều kiện riêng cho khớp, thiếu, thừa và lỗi |
| D04 | S1 tiêu đề nhánh nói shortage/overpayment, nội dung chỉ mô tả thiếu | Tiền thừa có thể bị phân bổ sai hoặc biến mất | Ghi tiền thừa chưa phân bổ và quy trình xác minh/hoàn trả |
| D05 | S1 xem các chuyến active và đặt tất cả đơn liên quan `Paid` | Khoản nợ từ chuyến đóng có thể bị bỏ sót; một đợt không đại diện toàn đơn | Chọn theo số dư COD còn mở; tính thanh toán toàn đơn từ dữ liệu tài chính |
| D06 | S1 cho Sales Staff thao tác; S3 UC-63 lấy Accountant làm actor | Chưa rõ ai được ghi nhận tiền và ai duyệt/tạo phiếu | Ma trận quyền và cơ chế ủy quyền |
| D07 | S4 GB-19 yêu cầu xác minh trước công nhận; UC-41 có thao tác Cash Collected của shipper | Chưa rõ khai báo shipper đã đủ bằng chứng xác minh hay chưa | Tiêu chuẩn công nhận khoản thu và quyền xác minh |
| D08 | S5 chỉ thể hiện COD phải thu tại delivery và giao dịch theo đơn | Chưa đủ định danh bàn giao/phân bổ theo đợt | Chọn mô hình dữ liệu và liên kết chứng từ |

D01–D08 là sổ quyết định mở, không phải xác nhận yêu cầu đã thay đổi. Theo governance repository, implementation phụ thuộc các điểm này phải chờ làm rõ; việc phân tích và lập báo cáo vẫn có thể hoàn thành.

## 7. Phân tích số tiền và các tình huống

### 7.1. Hai phương án công nhận thanh toán

**Phương án A: công nhận khi cửa hàng xác minh đã nhận tiền.** Phù hợp với cách diễn đạt UC-30 và quy tắc chỉ cộng giao dịch xác minh. Khi shipper khai thu, hiển thị riêng “đã thu, chờ đối soát”, chưa tạo khoản `VERIFIED` thứ hai. Nhược điểm là khách đã trả nhưng trạng thái thanh toán vẫn chờ; giao diện và nhắc nợ phải tránh đòi khách trả lần nữa.

**Phương án B: công nhận khoản khách trả sau khi xác minh việc shipper thu; bàn giao theo dõi riêng.** Khi bằng chứng thu được chấp nhận, khoản khách trả chỉ được ghi một lần; khi nộp quỹ, giảm tiền shipper giữ và tăng tiền cửa hàng nhận. Phù hợp với việc tách trách nhiệm khách và shipper, nhưng cần mô hình tiền đang giữ, quy trình xác minh và xử lý đảo/điều chỉnh. Không thể triển khai bằng hai lần gọi API ghi thu tiền đơn hiện tại.

**Quyết định chủ nghiệp vụ, thay thế khuyến nghị trước:** áp dụng mốc nhận tiền tại cửa hàng của phương án A. Chỉ chuyển `PAID` khi cửa hàng xác nhận nhận đủ; phần chưa nhận đủ vẫn là nợ. Nếu bằng chứng xác minh khách trả đủ nhưng shipper nộp thiếu thì shipper chịu trách nhiệm phần thiếu. Phương án B không được chọn. Bằng chứng thu tiền vẫn bắt buộc theo mục 2.1.

### 7.2. Công thức đề xuất

Trong phạm vi một đồng tiền và các khoản được chọn, không có điều chỉnh/hoàn trả:

```text
Tiền shipper còn giữ = Tổng thực thu được chấp nhận - Tổng bàn giao đã xác nhận
Chênh lệch kiểm đếm = Tiền thực nhận lần này - Tiền dự kiến bàn giao lần này
Khách còn phải trả = max(Tổng nghĩa vụ hợp lệ - Tổng thanh toán khách được công nhận, 0)
```

Khi có hoàn trả, đảo hoặc điều chỉnh, các tổng phải tính trên bút toán có dấu và liên kết nguồn; không sửa đè lịch sử. Số còn giữ khác 0 chỉ có nghĩa trong phạm vi đã chọn; một phiếu khớp không chứng minh toàn bộ nghĩa vụ shipper trong ngày bằng 0. Không dùng `max(...,0)` cho chênh lệch vì sẽ che mất tiền thừa.

### 7.3. Ví dụ phân biệt nguyên nhân thiếu

| Tình huống | Phải thu | Thực thu được chấp nhận | Đã nộp | Cách hiểu |
|---|---:|---:|---:|---|
| Khớp đủ | 10.700.000 | 10.700.000 | 10.700.000 | Phạm vi này không còn tiền shipper giữ |
| Khách trả thiếu | 10.700.000 | 10.200.000 | 10.200.000 | Khách còn thiếu 500.000 nếu chưa có điều chỉnh hợp lệ; shipper không mặc nhiên giữ 500.000 |
| Shipper nộp thiếu | 10.700.000 | 10.700.000 | 10.200.000 | Shipper còn phải bàn giao 500.000; theo B không tự mở lại nợ khách |
| Nộp nhiều lần | 10.700.000 | 10.700.000 | 6.000.000 lần đầu | Còn 4.700.000; lần nộp tiếp phải nối cùng nguồn |
| Tiền kiểm đếm thừa | 10.700.000 | 10.700.000 | 11.000.000 thực nhận | 300.000 chưa giải thích; không tự cộng vào đơn đã đủ |

### 7.4. Ngoại lệ cần hỗ trợ

- **Giao nhiều đợt:** đơn 30 triệu, đã xác minh cọc 10 triệu; đợt này thu 8 triệu và nộp đủ thì tổng đã trả mới là 18 triệu. Không chuyển toàn đơn `PAID`; tránh thu lặp phần cọc.
- **Khách chuyển khoản tại cửa:** xác minh qua luồng chuyển khoản; nếu tiền vào tài khoản cửa hàng thì shipper không giữ khoản tiền mặt đó. Không cộng cùng khoản vào cả COD mặt và chuyển khoản.
- **Giao thất bại hoặc khách từ chối một phần:** không suy ra đã thu từ trạng thái giao. COD cần được điều chỉnh qua quyền hợp lệ, giữ dấu vết số trước/sau.
- **Shipper dùng COD trả xăng/phí:** không tự khấu trừ; phải có chứng từ chi và liên kết được duyệt. Nếu chưa hỗ trợ bù trừ, nộp nguyên khoản rồi xử lý chi riêng.
- **Đổi shipper:** lưu người thực tế thu và người chịu trách nhiệm tiền; không chuyển nghĩa vụ tiền cũ chỉ bằng sửa người giao hiện tại.
- **Qua ngày/qua ca:** khoản chưa nộp phải tiếp tục hiện cho đến khi xử lý, không reset theo ngày.
- **Sửa/đảo sau xác nhận:** tạo chứng từ đối ứng có lý do và quyền duyệt; không xóa phiếu hoặc sửa số tiền đã chốt.
- **Không có khoản COD:** hiển thị danh sách rỗng, không tạo phiếu thu 0 hoặc cập nhật đơn.

## 8. Trạng thái và dữ liệu đề xuất

### 8.1. Các trục trạng thái độc lập

| Trục | Trạng thái minh họa | Ý nghĩa |
|---|---|---|
| Giao hàng | `DISPATCHED`, `IN_TRANSIT`, `DELIVERED` | Tiến độ hàng; không đủ chứng minh tiền |
| Thanh toán đơn | `UNPAID`, `PARTIALLY_PAID`, `PAID` | Theo tổng khoản khách trả được công nhận |
| Xác minh khoản thu | Chờ xác minh, được chấp nhận, bị bác bỏ | Độ tin cậy của khai báo thu |
| Bàn giao | Nháp, chờ kiểm đếm, xác nhận, đảo | Vòng đời chứng từ bàn giao |
| Đối soát | Khớp, thiếu, thừa, đang tranh chấp, đã xử lý | Kết quả so sánh và xử lý chênh lệch |

Chỉ trạng thái thanh toán đơn ở bảng trên là enum đã thấy trong code. Các tên khác là mô hình minh họa. Nên tách trạng thái chứng từ với kết quả chênh lệch: phiếu đã xác nhận nhận 10,2 triệu vẫn có thể còn một vụ thiếu tiền đang mở.

### 8.2. Mô hình tối thiểu cần đánh giá

| Đối tượng đề xuất | Nội dung chính | Lý do |
|---|---|---|
| Khoản thu COD | ID, deliveryId, orderId, người thu, số phải thu snapshot, thực thu, đồng tiền, thời điểm, bằng chứng/xác minh | Một đơn có thể nhiều đợt; giữ nguyên người thực tế thu |
| Phiếu bàn giao | ID/mã, người giao/nhận, thời điểm thực tế và ghi sổ, số kiểm đếm, trạng thái, khóa chống trùng | Chứng minh lần nộp và bên nhận |
| Phân bổ bàn giao | Phiếu bàn giao, khoản thu COD, số tiền phân bổ, liên kết chứng từ tài chính | Một phiếu có nhiều đơn; một khoản thu có thể nộp nhiều lần |
| Hồ sơ chênh lệch | Số chênh, loại nguyên nhân, bên chịu trách nhiệm hoặc chưa xác định, bằng chứng, người xử lý, kết quả | Tránh dùng một ghi chú tự do thay sổ theo dõi |

Đây là mô hình logic, chưa quyết định phải tạo bốn bảng. Có thể rút gọn tùy phạm vi; không tạo thêm sổ tiền song song mà không xác định nguồn sự thật. Cần hỗ trợ liên kết một phiếu bàn giao nhiều khách với các giao dịch theo từng khách/đơn; không gán tùy ý toàn bộ phiếu cho một customerId.

Các ràng buộc đề xuất: số tiền dương cho phân bổ; không phân bổ quá số còn mở; tổng phân bổ không vượt số nhận hợp lệ; tiền thừa nằm riêng; một nguồn thanh toán chỉ được công nhận một lần; phiếu đảo liên kết phiếu gốc; lưu phiên bản dữ liệu; dùng `BigDecimal`/PostgreSQL `NUMERIC`, quy tắc làm tròn và đồng tiền phải rõ. Không hardcode ngưỡng chênh lệch được bỏ qua khi chưa duyệt.

## 9. Đối chiếu backend hiện tại

| Thành phần đã đọc | Bằng chứng | Tác động với UC-30 |
|---|---|---|
| `Delivery` | Có `orderId`, `shipperId`, `batchNo`, `codRequired`, `codAmount`, `deliveredAt` | Có nền tảng định danh đợt giao và số COD; entity chưa thể hiện thực thu hay bàn giao |
| `Transaction` | Có loại thu/chi, `paymentType`, số tiền, đơn/khách, trạng thái, người duyệt, `idempotencyKey` | Có thể tham chiếu cơ chế chứng từ; một dòng chỉ có một orderId/customerId |
| `OrderPaymentServiceImpl.record` | CASH thành `VERIFIED`, chuyển khoản thành `PENDING`; kiểm tra số còn phải trả và khóa chống trùng | Không gọi trực tiếp để ghi lần nữa khi khoản khách trả đã được ghi trước đó |
| `OrderPaymentServiceImpl.summary` | Tổng `VERIFIED` của `RECEIPT` theo đơn được tính là đã trả; `PENDING` hiển thị riêng | Nếu bàn giao cũng thêm `RECEIPT` theo đơn, số đã trả có nguy cơ bị cộng hai lần |
| `PaymentSettlementApi` | Có truy vấn tổng thanh toán xác minh và lệnh tạo hoàn tiền chờ | Chưa thấy contract bàn giao COD trong API này |
| Module `fulfillment` khảo sát | Danh sách file hiện có gồm `Delivery`, `DeliveryItem` | Chưa có bằng chứng luồng UC-41 hoàn chỉnh trong phạm vi này; cần đánh giá dependency trước khi làm UC-30 |

Các khác biệt giữa S5 và code mới như `idempotencyKey`, `rejectReason` là bằng chứng PDF database không đủ đại diện hiện trạng. Báo cáo không giả định schema PDF là migration được duyệt. Không chạy ứng dụng, truy vấn database hay full test suite cho nghiên cứu này; không xác nhận dữ liệu đang triển khai thực tế.

## 10. Kiến trúc, tích hợp và tính nhất quán đề xuất

Theo kiến trúc repository, `fulfillment` sở hữu việc giao; `finance` sở hữu giao dịch tài chính; `sales` sở hữu đơn. FE-06 là nơi phân nhóm use case trong tài liệu BA, không tự buộc mọi bảng COD phải thuộc module `sales`.

Đề xuất `fulfillment` cung cấp sự kiện/kết quả thu gắn delivery; `finance` sở hữu đối soát, phân bổ, chứng từ và số dư tiền; `sales` nhận kết quả thanh toán tổng hợp. Ownership cuối cùng cần được duyệt. Mọi giao tiếp liên module qua `api/`, không import `internal/`, không liên kết JPA entity chéo module; dùng scalar ID. `share` không chứa nghiệp vụ COD.

Với modular monolith/PostgreSQL, nên đánh giá một transaction cho việc xác nhận bàn giao, cập nhật phân bổ và tạo chứng từ để tránh phiếu thành công nhưng mất khoản thu. Nếu chọn xử lý sau commit, cần trạng thái chờ, cơ chế bền vững, retry và đối soát lại; Spring application event hoặc `AFTER_COMMIT` tự nó không bảo đảm không mất việc khi tiến trình dừng.

Các kiểm soát cần có khi thiết kế:

1. Khóa hoặc cập nhật có điều kiện theo số dư/phiên bản; hai kế toán không được cùng nhận hết một khoản.
2. Khóa idempotency lưu cùng nội dung và kết quả; cùng key/cùng dữ liệu trả kết quả cũ, cùng key/khác dữ liệu báo xung đột.
3. Nếu phải khóa nhiều đơn/khoản thu, dùng thứ tự ổn định và xử lý deadlock/retry an toàn.
4. Lỗi giữa tạo phiếu và ghi phân bổ phải rollback hoặc giữ trạng thái chờ có thể phục hồi; không báo thành công giả.
5. Kiểm tra invariant định kỳ: tổng phân bổ khớp phiếu, không thu trùng nguồn, không có khoản mồ côi, số còn giữ được giải thích.
6. Notification/in phiếu lỗi không làm phát sinh thu lần hai; có thể tạo lại bản in từ dữ liệu đã chốt.

**Năng lực API cần có, chưa chốt URI/DTO:** truy vấn khoản COD mở theo shipper; xem chi tiết; tạo bản nháp và phân bổ; xác nhận nhận tiền; xem chứng từ; lập/xử lý chênh lệch; đảo theo quyền. Mọi tổng tiền và danh tính người duyệt phải tính/xác định ở server. Contract sau này theo `ApiResponse<T>` và error convention hiện hành; không coi danh sách này là API đã tồn tại.

## 11. Màn hình, báo cáo và audit

Màn hình tổng quan cần lọc shipper, ngày thu, chuyến/đợt, trạng thái đối soát và tuổi khoản chưa nộp. Các cột nên gồm phải thu, thực thu được chấp nhận, đã bàn giao, còn giữ, khoản tranh chấp. Phải xem được khoản ngày trước và từ chuyến đã đóng.

Màn hình kiểm đếm hiển thị các khoản được chọn, thực nhận, phân bổ từng dòng, chênh lệch và lý do; hiển thị lại số dư khi dữ liệu đã thay đổi. Nút xác nhận phải mô tả đúng hành động nhận tiền, không làm người dùng hiểu rằng mọi đơn trong chuyến đều đã trả đủ.

Biên nhận đề xuất có mã, bên giao/nhận, thời điểm, số tiền, danh sách phân bổ, khoản còn mở và mã chứng từ. Việc cần chữ ký hai bên hoặc ảnh tiền phải được chủ nghiệp vụ quyết định; ảnh giao hàng không mặc nhiên là bằng chứng số tiền thu.

Audit giữ người thao tác, thời điểm, nguồn thu, số trước/sau, lý do điều chỉnh, tham chiếu phiếu đảo và kết quả xác minh. Chỉ người có quyền được xem chứng từ và dữ liệu khách hàng; log không lưu token, secret hoặc ảnh bằng chứng dưới dạng dữ liệu thô.

Chỉ số quản trị đề xuất: COD đã thu chưa nộp; số ngày treo theo shipper; số thiếu/thừa chưa xử lý; tỷ lệ bàn giao khớp; tổng tiền quỹ thực nhận. Không gắn nhãn tổng COD là doanh thu: thu tiền, bàn giao tiền và ghi nhận doanh thu là các sự kiện khác nhau.

## 12. Tiêu chí nghiệm thu và bộ tình huống đề xuất

Các tiêu chí sau cần được cập nhật sau D01–D08, không phải yêu cầu đã được phê duyệt.

| ID | Kịch bản | Kết quả cần chứng minh |
|---|---|---|
| AC-01 | Hai khoản 3,5 và 7,2 triệu; nộp 10,7 triệu | Phiếu/phân bổ đúng từng nguồn; số còn mở trong phạm vi chọn bằng 0 |
| AC-02 | Khách chỉ trả 10,2/10,7 triệu, shipper nộp đủ thực thu | Giữ thiếu ở khách hoặc tranh chấp theo kết quả xác minh; không tự ghi shipper giữ 0,5 triệu |
| AC-03 | Khách trả đủ, shipper nộp thiếu 0,5 triệu | Còn nghĩa vụ shipper; trạng thái khách theo phương án công nhận được duyệt |
| AC-04 | Nộp 6 triệu rồi 4,7 triệu | Hai lần liên kết cùng nguồn, không trùng và không ghi đè lịch sử |
| AC-05 | Thực nhận 11 triệu, dự kiến 10,7 triệu | Giữ riêng 0,3 triệu chênh lệch; không tăng thanh toán đơn đã đủ |
| AC-06 | Đơn 30 triệu, cọc 10 triệu, COD đợt 8 triệu | Không chuyển toàn đơn `PAID` khi mới ghi nhận tổng 18 triệu |
| AC-07 | Một phiếu có nhiều khách và nhiều đơn | Phân bổ đúng từng đơn/khách; tổng chi tiết khớp tiền nhận hợp lệ |
| AC-08 | Gửi lại cùng key sau timeout | Trả cùng kết quả, không tạo phiếu/giao dịch thứ hai |
| AC-09 | Cùng key với nội dung khác | Từ chối xung đột, bảo toàn kết quả ban đầu |
| AC-10 | Hai kế toán đồng thời xác nhận cùng số dư | Không phân bổ vượt; request sau nhận lỗi hoặc số dư mới rõ ràng |
| AC-11 | Lỗi giữa tạo phiếu và cập nhật phân bổ | Không để trạng thái hoàn tất lệch chứng từ; phục hồi có thể kiểm chứng |
| AC-12 | Khách đã chuyển trực tiếp vào tài khoản cửa hàng | Không đồng thời cộng tiền mặt shipper giữ; xác minh theo luồng tương ứng |
| AC-13 | Shipper xem người khác hoặc tự duyệt | Backend từ chối theo phạm vi quyền |
| AC-14 | Khoản tồn từ hôm trước, chuyến đã đóng | Vẫn xuất hiện trong danh sách khoản mở |
| AC-15 | Đảo một phiếu đã xác nhận | Có dấu vết đối ứng và số dư đúng; không xóa lịch sử |
| AC-16 | Đổi người giao sau khi đã thu | Giữ nguyên người thực thu và truy vết trách nhiệm tiền |
| AC-17 | COD đã được công nhận trước khi bàn giao | Bàn giao không cộng lần hai vào tổng khách đã trả |
| AC-18 | Lỗi in phiếu/thông báo sau khi commit | In/gửi lại được, không thu lại hoặc tạo thêm giao dịch |

Kiểm thử tương lai cần kiểm tra nghiệp vụ ở service, quyền ở HTTP, concurrency/rollback bằng PostgreSQL và hành trình tích hợp UC-41 → UC-30 → UC-63 → tổng thanh toán đơn. Không dùng mock đơn thuần để chứng nhận khóa database hoặc tính nguyên tử. Báo cáo này không thực thi các test trên vì chưa triển khai UC-30.

## 13. Phạm vi triển khai tối thiểu và điều kiện sẵn sàng

**Phạm vi đầu tiên đề xuất:** tiền mặt COD của shipper nội bộ, nguồn thu gắn từng delivery, xem số dư mở, bàn giao đủ hoặc từng phần, lưu thiếu/thừa cần xử lý, phân bổ chính xác, chống trùng, audit và chứng từ tham chiếu. Không âm thầm mở rộng sang đối soát hãng vận chuyển ngoài, phí dịch vụ tự khấu trừ hoặc nhiều đồng tiền. Nếu doanh nghiệp có các trường hợp đó, phải bổ sung scope trước triển khai.

Trình tự hoàn thiện đề xuất:

1. Chủ nghiệp vụ và kế toán quyết định D01–D07; kiến trúc sư quyết định D08 cùng ownership và transaction.
2. Xác minh lại SRS/database PDF khi có file, đối chiếu với schema migration hiện tại; xác định UC-41 cung cấp được dữ liệu thực thu nào.
3. Viết Feature Spec và Delivery Brief theo workflow. Triển khai liên quan tiền, concurrency và xuyên module thuộc Risk L; báo cáo nghiên cứu này không thay Design Review.
4. Chốt mô hình nguồn thanh toán duy nhất, contract, quyền và migration additive; đánh giá dữ liệu COD lịch sử cần đối soát riêng.
5. Triển khai và kiểm chứng theo các lát nghiệp vụ, sau đó review chứng từ, quyền, concurrency và khả năng phục hồi.

Điều kiện tối thiểu để bắt đầu implementation: biết chính xác mốc công nhận thanh toán; xác định cách xử lý khách trả thiếu/shipper nộp thiếu; có nguồn thực thu và phân bổ theo đợt; rõ quyền nhận/duyệt; thống nhất phương án chống ghi thu hai lần.

## 14. Danh mục bằng chứng mã nguồn

Các đường dẫn dưới đây tương đối với `D:\Workspace\aquasuite-be`, được đọc ngày 08/11/2026:

- `src/main/java/com/aquasuite/ims/module/fulfillment/internal/entity/Delivery.java`
- `src/main/java/com/aquasuite/ims/module/fulfillment/internal/entity/DeliveryItem.java` — xác định sự tồn tại qua danh sách file; chưa phân tích chi tiết nội dung.
- `src/main/java/com/aquasuite/ims/module/finance/internal/entity/Transaction.java`
- `src/main/java/com/aquasuite/ims/module/finance/internal/service/impl/OrderPaymentServiceImpl.java`
- `src/main/java/com/aquasuite/ims/module/finance/internal/service/PaymentSettlementApiImpl.java`
- `src/main/java/com/aquasuite/ims/module/finance/api/PaymentSettlementApi.java`
- `src/main/java/com/aquasuite/ims/module/sales/internal/enums/PaymentStatus.java`
- `docs/README.md`, `docs/architecture/Architecture-Aquasuite.md`, `docs/architecture/module-contracts.md`, `docs/contracts/api-conventions.md`, `docs/workflow/feature-delivery.md`.

Không sửa mã nghiệp vụ, schema, permission hoặc các artifact lịch sử trong quá trình lập báo cáo. Kết quả bàn giao là tài liệu phân tích để BA, kế toán và nhóm phát triển thống nhất yêu cầu.
