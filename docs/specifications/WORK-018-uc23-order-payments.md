# WORK-018 — UC-23 Manage Order Payments

## Metadata

- **Work ID**: WORK-018
- **Feature / Use Case**: UC-21 — Create and Manage Sales Orders; UC-23 — Manage Payment and Payment Verification
- **Version**: Dự thảo 0.8 — bổ sung theo dõi mốc thanh toán của đơn
- **Owner / Decision Owner**: Product Owner và Tech Lead — chưa xác định cụ thể
- **Owning Module**: `finance` và `sales` theo quyền sở hữu dữ liệu tương ứng; owner review liên module cần xác định
- **Risk Reasons**: Tiền, phân quyền, xử lý đồng thời, hợp đồng công khai, liên module và migration
- **Design Review**: PENDING — chưa được duyệt triển khai.

## 1. Goal and Scope

Cho phép nhân viên có quyền ghi nhận tiền mặt đã nhận hoặc khoản chuyển khoản khách khai báo cho đơn hàng hợp lệ, xem lịch sử và tổng quan thanh toán. Chỉ tiền vào đã `VERIFIED` được tính là đã trả. UC-21 và luồng online cung cấp đơn; UC-70 xác minh khoản chuyển khoản đang chờ.

### In Scope

- UC-21 trong `sales`: chuẩn hóa kênh `COUNTER`, tạo hóa đơn cửa hàng cùng đơn, gọi kiểm tra tín dụng cuối UC-24 theo toàn bộ giá trị đơn trong transaction ghi đơn. Contract và migration phải được review trước khi sửa code.
- Xem lịch sử, tổng quan và ghi nhận nhiều đợt thanh toán cho một đơn.
- Theo dõi tiến độ từng mốc thanh toán của đơn: tính các khoản `RECEIPT` / `VERIFIED` vào mốc còn thiếu theo thứ tự; hiển thị số theo thỏa thuận, đã trả và còn thiếu. Đây là phạm vi bổ sung, chưa được triển khai hoặc review.
- Tiền mặt thực nhận do nhân viên có quyền ghi nhận là `VERIFIED`; chuyển khoản khách khai báo là `PENDING`, lưu mã tham chiếu và chứng từ nếu có.
- Tính số tiền đã xác minh, đang chờ, còn phải trả và trạng thái thanh toán; cập nhật `sales` qua hợp đồng liên module được duyệt.
- Phân quyền, lưu thông tin thao tác, chống ghi trùng, xử lý đồng thời và kiểm thử phù hợp.

### Out of Scope

- Đối soát, xác nhận hoặc từ chối chuyển khoản của UC-70; phân bổ khoản thu vào hóa đơn điện tử theo đợt do người phụ trách UC-63 thực hiện; hoàn tiền, bút toán đảo; thu và nộp COD; duyệt mua nợ; checkout; quyết định giao hàng; FE; thanh toán thẻ hoặc máy quẹt thẻ.
- Tài liệu chưa xác lập việc xác minh tự động qua cổng ngân hàng hoặc webhook.
- Phát hành/liên kết hóa đơn điện tử từng đợt, điều chỉnh/in hóa đơn và in phiếu bán hàng ngoài phạm vi hiện tại. UC-24 sở hữu quy tắc kiểm tra mua nợ. Việc tính tiến độ **mốc thanh toán của đơn** thuộc UC-23; tự phân bổ hoặc chọn phân bổ **vào hóa đơn điện tử** thuộc UC-63, chưa triển khai trong work này.

### Order Payment Milestones — Current State and Agreed Direction

**Hiện trạng sau merge:** báo giá có các mốc thanh toán; luồng `convertQuotationToOrder` sao chép tên, tỷ lệ, số tiền tính theo tổng đơn và thứ tự vào `order_payment_milestones`. UC-23 hiện chỉ đọc tổng đơn và giao dịch để tính tổng đã trả/còn thiếu; chưa lấy các mốc qua contract Sales, chưa tính hoặc trả tiến độ theo mốc. Mốc hiện có chưa thể hiện hạn từng mốc, số đã trả hoặc trạng thái thanh toán. Đơn tạo trực tiếp có thể không có mốc.

**Quy tắc người dùng đã chốt:** chỉ khoản thu `RECEIPT` / `VERIFIED` của đúng đơn được tính vào tiến độ các mốc. Tính lần lượt vào mốc còn thiếu theo thứ tự đã thỏa thuận; nếu khoản tiền vượt phần thiếu của mốc hiện tại, phần dư tiếp tục tính vào các mốc tiếp theo **cùng đơn**. Khoản `PENDING` chỉ hiển thị chờ, chưa được tính đã trả cho bất kỳ mốc nào. Không tạo thêm transaction thu tiền khi tính tiến độ; tổng đã trả của đơn chỉ tính từ sổ giao dịch Finance một lần.

Ví dụ đơn 100 triệu có mốc cọc 30 triệu, đợt 1 là 40 triệu, đợt 2 là 30 triệu; đã thu `VERIFIED` 40 triệu: mốc cọc đã trả 30, còn 0; đợt 1 đã trả 10, còn 30; đợt 2 đã trả 0, còn 30; toàn đơn đã trả 40, còn 60 triệu. Nếu có thêm 20 triệu `PENDING`, tiến độ mốc và số đã trả không đổi; khoản chờ hiển thị riêng. Sau khi UC-70 xác minh thành công khoản 20 triệu, tiến độ được tính lại: đợt 1 đã trả 30, còn 10; toàn đơn đã trả 60, còn 40 triệu.

**Ranh giới:** tính tiến độ mốc là cách trình bày nghĩa vụ theo thỏa thuận của đơn, không khẳng định khoản tiền đã được phân bổ vào một hóa đơn điện tử. UC-63 do thành viên khác phụ trách sẽ thiết kế việc phân bổ vào hóa đơn theo đợt giao; UC-24 tính công nợ/quá hạn theo contract và quy tắc hóa đơn được duyệt. Không cộng khoản thiếu của mốc một lần nữa vào tổng nợ đơn hoặc tạo thêm giao dịch khi UC-63 phân bổ.

**Đề xuất để review, chưa phải contract đã duyệt:** Sales cung cấp projection chỉ đọc qua `sales.api` gồm mã mốc, mã đơn, tên, thứ tự và số tiền; Finance tính tiến độ từ projection và tổng giao dịch `VERIFIED` trong ảnh chụp dữ liệu nhất quán. Cần chốt cơ chế dữ liệu, API, snapshot/phiên bản mốc, thứ tự ổn định, cách xử lý mốc thay đổi và tương tác UC-70/UC-63. Không import `sales.internal` từ Finance; không tự thêm quan hệ entity chéo module.

**Điểm phụ thuộc sau merge:** luồng tạo đơn tại quầy hiện có kiểm tra khách và tạo hóa đơn cửa hàng. Luồng chuyển báo giá sang đơn là một đường riêng: tại thời điểm khảo sát chưa thấy gọi kiểm tra khách `BLOCKED`/tín dụng hoặc tạo hóa đơn cửa hàng trong phương thức chuyển đổi; cần phối hợp owner Sales/Finance xác nhận phạm vi, điều kiện và kiểm thử trước khi coi đây là bảo đảm toàn cục. Code hiện nhận cả báo giá `APPROVED` và `ACCEPTED`; điều kiện khách đã chốt cần nhóm thống nhất. Không sửa phần đó theo giả định.

### Related Use Cases and Invoice Proposal

- UC-21 tạo đơn và tính tổng; UC-22 xử lý đơn online. Giao diện đơn có thể gọi UC-23 ghi nhận thanh toán; việc chọn phương thức dự kiến không phải giao dịch thu tiền.
- UC-24 kiểm tra điều kiện mua nợ và theo dõi công nợ; UC-23 cung cấp giao dịch tiền vào đã xác minh để UC-24 tính toán. Phân bổ tiền trả chung cho nhiều hóa đơn thuộc UC-63. UC-26 điều chỉnh/hủy đơn; UC-23 không tự sửa đơn hoặc hoàn tiền.
- Luồng tạo đơn tại quầy hiện tạo hóa đơn cửa hàng `STORE` / `DRAFT`; luồng chuyển báo giá sang đơn cần kiểm tra và phối hợp owner để bổ sung theo quy tắc đã chốt. UC-24 xét hóa đơn `ISSUED` không phải `STORE` để tính hạn; không đồng nhất hóa đơn cửa hàng, biên nhận thanh toán và hóa đơn điện tử.
- Nhóm thống nhất UC-21 tạo hóa đơn cửa hàng sau khi tạo đơn; kế toán liên kết hóa đơn điện tử cho từng đợt sau khi khách nhận hàng. Trạng thái và mô hình dữ liệu cần phân biệt hai loại hóa đơn để không tính hạn nợ từ khi vừa tạo đơn.
- Người dùng xác nhận khoản thu vẫn thuộc đơn; liên kết với hóa đơn cửa hàng không đồng nghĩa đã phân bổ vào hóa đơn điện tử. UC-24 phải thể hiện khoản thu `VERIFIED` chưa phân bổ riêng. Ghi nhận khoản thu, số chưa phân bổ và kết quả phân bổ là các thông tin khác nhau, không ghi khoản thu mới khi phân bổ.
- Khi hóa đơn điện tử của đợt giao đầu tiên được liên kết, hệ thống tự phân bổ tiền cọc/tiền trả trước đã `VERIFIED` của đơn vào hóa đơn đó; phần trả trước còn dư tự phân bổ vào các hóa đơn đợt tiếp theo của cùng đơn khi đủ điều kiện. Với các khoản khách trả sau, kế toán kiểm tra và chọn hóa đơn khách thanh toán để gắn khoản tiền. Không phân bổ `PENDING`, không tạo thêm transaction thu tiền. Ví dụ: đơn 100 triệu, trả trước 30 triệu, hóa đơn đợt đầu 40 triệu thì đợt đầu còn 10 triệu; 60 triệu chưa xuất hóa đơn chưa tính quá hạn.
- Hạn trả từng đợt tính từ ngày phát hành hóa đơn điện tử của đợt cộng số ngày công nợ của khách. UC-21 gọi UC-24 kiểm tra hạn mức theo toàn bộ giá trị đơn mới, không trừ khoản dự kiến trả chưa được xác minh.
- Spec UC-24 hiện yêu cầu khoản trả trực tiếp có `invoiceId`, còn khoản chỉ gắn đơn chờ phân bổ theo UC-63. Quy tắc tự động vừa chốt cần được thiết kế và phối hợp UC-24/UC-63; chưa mặc định code hiện tại đã hỗ trợ phân bổ một giao dịch cho nhiều hóa đơn hoặc tự gắn tiền vào hóa đơn mới.
- Vòng đời, trạng thái, danh tính đơn/khách/hóa đơn, thứ tự khi phát hành lại hoặc đồng thời, điều chỉnh, phục hồi và trường hợp chưa có hóa đơn vẫn OPEN. Không triển khai phần phụ thuộc cho tới khi contract và thay đổi spec liên quan được review.

### Review Findings and Candidate Design

- Nhóm chốt UC-21 gọi bước kiểm tra tín dụng cuối cùng của UC-24, dùng điều kiện và kết quả UC-24; `COUNTER` là tên chuẩn của kênh bán tại quầy. Code hiện tại đã gọi contract, nhận `POS` và chuẩn hóa về `COUNTER`; không còn nhận `IN_STORE`. Cần kiểm kê dữ liệu/caller cũ, review khóa theo khách và đối chiếu hạn trả. `IN_STORE_PICKUP` là phương thức nhận hàng, không tự động đổi theo tên kênh.
- `Transaction.invoiceId` hiện chỉ liên kết một hóa đơn và UC-24 cộng toàn bộ tiền transaction vào hóa đơn đó. Không coi trường này là dữ liệu phân bổ nhiều đợt. Đề xuất thiết kế bảng kết quả phân bổ riêng trong `finance`, mỗi dòng liên kết giao dịch, hóa đơn điện tử và số tiền; cho phép nhiều dòng trên một giao dịch. Đây là phương án chưa được duyệt, không tự động thay đổi spec UC-24 đã duyệt.
- UC-24 đưa khoản thu gắn đơn và hóa đơn `STORE` vào tiền chưa phân bổ. Chưa có kết quả phân bổ từng phần, nên chưa tự tính hóa đơn 40 còn 10 khi có khoản trả trước 30. Cần review contract loại hóa đơn, dữ liệu cũ và mô hình UC-63 trước khi triển khai phân bổ.
- Sau finding review, projection hóa đơn trả thêm `invoiceType`; UC-24 loại `STORE` khỏi hóa đơn tính hạn ngay cả khi trạng thái là `ISSUED`. Các loại khác vẫn giữ cách tính `ISSUED` như trước để tránh tự phân loại lại lịch sử; chưa chứng minh mọi loại cũ đều là hóa đơn điện tử. Cần kiểm kê dữ liệu và chốt danh mục loại hóa đơn trước khi triển khai phân bổ UC-63; không chạy backfill tự động.
- Mã idempotency của UC-23 được khóa advisory theo key trước khóa dòng đơn, giữ cả hai tới commit/rollback; mọi luồng tương lai dùng cùng khóa key phải tuân theo cùng thứ tự. Unique index vẫn là lớp bảo vệ cuối. Lệnh cập nhật trạng thái Sales yêu cầu transaction và khóa dòng đơn; caller phải khóa trước khi đọc tổng đơn và các khoản thu đã xác minh. UC-70 và UC-26 phải tuân thủ cùng hợp đồng khi được triển khai.
- Theo quyết định mới nhất, `IN_STORE` không còn là kênh hợp lệ; UC-21 dùng `COUNTER` và vẫn nhận `POS`, chuẩn hóa `POS` về `COUNTER` khi tạo đơn. UC-24 nhận `COUNTER` và `POS` khi kiểm tra tín dụng, không nhận `IN_STORE`. Dữ liệu lịch sử chưa được chuyển đổi; cần kiểm kê trước khi quyết định migration. `IN_STORE_PICKUP` là phương thức nhận hàng và không đổi.
- Bất biến cần review: chỉ phân bổ khoản thu `VERIFIED`, cùng khách và đơn; số tiền phân bổ dương; tổng phân bổ của một khoản không vượt số tiền đã thu; tổng phân bổ vào một hóa đơn không vượt giá trị còn phân bổ được. Việc thêm phân bổ không tạo khoản thu mới, không cộng giao dịch và phân bổ hai lần vào số đã thu của đơn.
- Đề xuất serialize các thao tác thu, xác minh và phân bổ trên cùng đơn bằng một cơ chế khóa đã thống nhất; dùng khóa chống xử lý trùng cho lệnh/sự kiện và ràng buộc database phù hợp. Xác định transaction, retry và cơ chế xử lý khi hai hóa đơn được liên kết đồng thời, hủy/điều chỉnh, hoặc xảy ra lỗi giữa các bước. Thứ tự xác định hóa đơn đầu tiên khi cùng thời điểm và cách phục hồi vẫn cần owner quyết định.
- Hóa đơn cửa hàng và hóa đơn điện tử theo đợt cần định danh và trạng thái khác nhau; UC-24 chỉ tính hạn từ mốc của hóa đơn điện tử được xác lập theo nghiệp vụ nhóm chốt. Cần cập nhật và review spec UC-24 trước khi đổi cách hiểu `ISSUED` hoặc `issueDate`; dữ liệu cũ không được tự động phân loại lại.
- Theo yêu cầu người dùng, thay đổi UC-21 nằm trong WORK-018 để thiết kế và review xuyên `sales`/`finance`; dữ liệu vẫn thuộc module sở hữu tương ứng. Việc phân bổ khoản thu vào hóa đơn điện tử UC-63 do thành viên khác phụ trách; contract và thời điểm tích hợp giữa hai work item còn cần thống nhất.
- Sau khi UC-23 và UC-70 hoàn thiện, viết hướng dẫn UC-18 theo contract thực tế: phạm vi quyền khách, tạo transaction Pending, chống tạo trùng, xác minh và cập nhật trạng thái. Checkout online hiện ngoài phạm vi.

### UC-21 Integration — Proposed Design

1. `sales` xác định khách, mặt hàng và tổng đơn tại BE; kênh mục tiêu là `COUNTER`. Với đơn mua nợ, sau khi có tổng đơn, UC-21 gọi kiểm tra tín dụng cuối qua public contract `CreditControlApi` của UC-24 trong transaction ghi đơn. Không trừ khoản khách dự kiến trả chưa xác minh.
2. Nếu kiểm tra bị từ chối hoặc không thực hiện được, đơn mua nợ không được ghi thành công. Thiết kế phải giữ khóa tín dụng theo khách đến commit/rollback transaction tạo đơn và đọc lại công nợ, hạn mức sau khi lấy khóa; kiểm tra trên màn hình không giữ chỗ hạn mức.
3. Khi tạo đơn thành công, `sales` tạo đúng một hóa đơn cửa hàng gắn đơn/khách trong cùng transaction; nếu một bước thất bại thì rollback cả hai. Schema, trạng thái, mã duy nhất và response cụ thể chờ review. Hóa đơn cửa hàng không tự khởi động hạn nợ hóa đơn điện tử theo đợt.
4. Kiểm kê dữ liệu, caller, test và ràng buộc DB đang dùng `IN_STORE`/`POS`; thiết kế tương thích và migration sang `COUNTER` trước khi áp dụng. `IN_STORE_PICKUP` là phương thức nhận hàng và không tự đổi tên.
5. Review phụ thuộc hai chiều `sales`/`finance`, transaction propagation, thứ tự khóa và ảnh hưởng đến tồn kho; không import `internal` chéo module. Không coi việc gắn `@Transactional` là bằng chứng atomicity.

### Delivery Order

1. Hoàn thiện contract, schema và thiết kế UC-21/UC-24, rồi review Risk L.
2. Sửa và kiểm thử UC-21: kênh, kiểm tra tín dụng cuối, hóa đơn cửa hàng, rollback và đồng thời.
3. Sau khi contract thanh toán và đồng bộ được review, triển khai và kiểm thử BE UC-23.
4. Phối hợp UC-24/UC-63/UC-70 cho hóa đơn điện tử theo đợt, phân bổ và xác minh; các chức năng này chưa mặc định tồn tại.

### UC-21/UC-24 Detailed Design for Review

**Tình trạng:** Phương án cụ thể bên dưới là dự thảo để review, không phải code đã chạy hoặc contract đã duyệt. Sai lệch UC-21 chưa gọi UC-24, tên kênh và kiểm tra kỳ hạn đã được báo cáo; mục đích là sửa theo quy tắc người dùng đã làm rõ. Nếu phát hiện mâu thuẫn mới, báo cáo và tạm dừng phần bị ảnh hưởng theo AGENTS.md.

1. **Thứ tự xử lý:** Trong transaction tạo đơn, xác thực actor, xác định khách, kiểm tra mặt hàng và tính `finalTotal` ở server. Với `CREDIT`, gọi `finance.api.CreditControlApi.authorize` bằng customerId, `COUNTER` và toàn bộ `finalTotal` trước khi ghi đơn; không dùng lại nhánh `hasOverdueUnpaidOrders` riêng của UC-21 làm quyết định tín dụng. Chỉ tiếp tục khi kết quả được phép. Từ chối hoặc lỗi Finance phải rollback đơn, hóa đơn và thay đổi tồn kho trong cùng transaction; kiểm chứng thực tế mọi thành phần cùng transaction, không giả định.
2. **Tín dụng và đồng thời:** Chọn PostgreSQL `READ COMMITTED` làm phương án review. Finance dùng cùng khóa transaction theo customerId như `customer-credit:`; lấy khóa trước khi đọc lại hồ sơ khách, đơn, hóa đơn, transaction và tính hạn mức. Khóa phải thuộc cùng transaction/database connection với ghi đơn của Sales và được giữ đến commit/rollback. Kiểm tra hai transaction độc lập: hạn mức còn 100, mỗi đơn 60 → tối đa một đơn được ghi. Rà soát các đường khác có thể tạo nợ, thay hạn mức, xác minh/đảo thanh toán hoặc hủy đơn để cùng giao thức khóa; nếu chưa áp dụng được cho mọi đường liên quan, không tuyên bố đã bảo đảm toàn cục. Xác định thứ tự khóa khách → đơn → tồn kho và kiểm thử deadlock, timeout. Kiểm tra phụ thuộc vòng Spring giữa `sales` và `finance` do Finance gọi lại Sales; nếu phát sinh, tách orchestration/contract theo thiết kế được duyệt, không truy cập `internal` chéo module.
3. **Kỳ hạn nợ:** Finance từ chối cho mua nợ nếu `creditTermDays` null hoặc ngoài 1–30, bên cạnh trạng thái, loại khách, hạn mức và kênh đã có; định nghĩa mã lỗi/reason rõ ràng trong review. Không thay thế quy tắc UC-24 bằng kiểm tra quá hạn 60 ngày riêng của UC-21. Thời hạn từng đợt dùng ngày hóa đơn điện tử cộng kỳ hạn áp dụng; thời điểm chụp kỳ hạn khi điều chỉnh hồ sơ còn OPEN.
4. **Ràng buộc dữ liệu:** V4 dùng `(credit_limit > 0 AND credit_term_days BETWEEN 1 AND 30)`; PostgreSQL CHECK có thể chấp nhận `UNKNOWN` khi `credit_term_days IS NULL`, nên không chứng minh dữ liệu đó bị chặn. Đề xuất migration tiến về trước, giữ V4 nguyên, kiểm tra dữ liệu vi phạm trước khi siết constraint và dừng migration có thông báo nếu có dữ liệu cần xử lý; không tự gán kỳ hạn hoặc xóa dữ liệu. Constraint mới yêu cầu rõ `credit_term_days IS NOT NULL` khi `credit_limit > 0`, và giữ quy tắc hạn mức bằng 0/thời hạn null đã duyệt. Cần audit dữ liệu triển khai và phương án sửa dữ liệu do owner duyệt; chưa khẳng định dữ liệu lỗi đang tồn tại.
5. **Kênh:** Đề xuất ghi mới `COUNTER`, kiểm kê mọi request, response, enum, filter, test, DB constraints và dữ liệu `IN_STORE`/`POS` trước khi chọn migration/tương thích. Không tự đổi giá trị `POS` hoặc dữ liệu lịch sử khi chưa xác định ý nghĩa. Ghi rõ thời gian hỗ trợ alias và ảnh hưởng breaking API để review; không đổi `IN_STORE_PICKUP`.
6. **Hóa đơn cửa hàng:** Đề xuất một bản ghi có loại và trạng thái nội bộ phân biệt hóa đơn điện tử `ISSUED`, với ràng buộc duy nhất phù hợp cho một hóa đơn cửa hàng ban đầu mỗi đơn. Lưu đơn, dòng đơn và hóa đơn trong cùng transaction, rollback khi bất kỳ bước nào lỗi. `SalesInvoice.issueDate` và `createdBy` hiện NOT NULL, `@PrePersist` tự điền issueDate, trong khi Sales có thể trả actor null: không được dùng ngày tạo nội bộ như ngày phát hành điện tử hoặc dùng actor giả. Reviewer cần chọn mô hình/schema, mã hóa đơn duy nhất, lưu snapshot chi tiết, actor bắt buộc, contract response và khả năng sửa/điều chỉnh; kiểm thử UC-24 không tính quá hạn với chứng từ nội bộ. Chưa tự quyết thay đổi schema hay diễn giải lại dữ liệu cũ.
7. **Tính công nợ:** UC-24 hiện chỉ tính tiền gắn đơn và hóa đơn theo `invoiceId`; chưa có bằng chứng hỗ trợ allocation một phần. Tạo hóa đơn cửa hàng không được làm UC-24 coi nó là hóa đơn điện tử `ISSUED`. Quy tắc tự phân bổ và thủ công cần work/contract UC-63 và thay đổi UC-24 được review; không tuyên bố UC-21 hoặc UC-23 đã hỗ trợ.
8. **Thứ tự delivery:** (a) review phương án và chốt compatibility/schema với owner; (b) preflight dữ liệu + migration forward có phương án dừng/phục hồi; (c) tích hợp UC-21/UC-24 và tạo hóa đơn cửa hàng; (d) kiểm thử/review; (e) thiết kế chi tiết rồi triển khai UC-23. Không coi review slice UC-21 là phê duyệt UC-23, UC-63 hoặc UC-70.

**Kiểm thử bắt buộc cho slice UC-21/UC-24:** hồ sơ hạn mức dương với kỳ hạn null/0/31 bị từ chối ở service và DB, 1/30 hợp lệ; dữ liệu cũ vi phạm làm migration dừng có thể khôi phục; khóa được giữ xuyên transaction Sales và hai đơn đồng thời không vượt hạn mức; kiểm tra tín dụng lỗi và lỗi ghi hóa đơn rollback đơn/tồn kho; actor thiếu bị từ chối; tạo đúng một hóa đơn cửa hàng không xuất hiện trong nợ hóa đơn điện tử/quá hạn; COUNTER và dữ liệu/caller cũ theo chính sách được duyệt; regression UC-24 và full suite theo workflow.

## 2. Actors and Security

| Actor | Allowed Operations | Required Role/Permission | Data Restrictions |
|---|---|---|---|
| Sales Staff, Shop Owner | Xem thanh toán; ghi nhận tiền mặt hoặc chuyển khoản | Mã quyền cụ thể chưa chốt | Chỉ truy cập dữ liệu được phép |
| Accountant | Xem thanh toán, xác minh chuyển khoản trong UC-70; chọn hóa đơn cho khoản trả sau theo luồng phân bổ | Mã quyền cụ thể chưa chốt | Không trực tiếp ghi nhận thanh toán tại quầy; hạn chế truy cập chứng từ |

Bắt buộc xác thực và kiểm tra quyền tại BE. Chỉ người được phép thu tiền mới ghi tiền mặt thành `VERIFIED` ngay khi thực nhận. Lưu người thao tác và thời điểm; không ghi secret, nội dung chứng từ hoặc dữ liệu ngân hàng/khách hàng không cần thiết vào log. Cơ chế tải lên, lưu trữ và kiểm soát truy cập chứng từ chưa chốt.

## 3. Business Rules

1. Đơn phải tồn tại, đủ điều kiện nhận thanh toán và cung cấp số tiền phải thu hiện hành từ `sales`. Trạng thái đơn/hóa đơn được phép và cách xử lý đơn điều chỉnh còn phải quyết định.
2. Số tiền phải dương, phù hợp đơn vị tiền và độ chính xác được hỗ trợ. Số phải thu lấy từ `sales`, không tin giá trị client gửi. Chính sách đơn vị tiền và độ chính xác cần xác nhận.
3. Mỗi phương thức và mỗi đợt thanh toán là một giao dịch tiền vào riêng gắn với mã đơn. Tiền mặt thực nhận được tạo `VERIFIED`; chuyển khoản khách khai báo được tạo `PENDING`. Ảnh hoặc mã tham chiếu không tự chứng minh đã nhận tiền.
4. `verifiedPaid` là tổng giao dịch tiền vào hợp lệ `VERIFIED`; `pendingAmount` là tổng giao dịch tiền vào hợp lệ `PENDING`; `remaining = max(payableTotal - verifiedPaid, 0)`. Cách xử lý hàng trả, điều chỉnh và sổ hiện có cần được chốt trước khi triển khai.
5. Với tổng phải thu dương: chưa có tiền xác minh là `UNPAID`; đã xác minh một phần là `PARTIALLY_PAID`; đã xác minh bằng tổng phải thu là `PAID`. Không ghi nhận thanh toán vượt nghĩa vụ của đơn và không tự chuyển tiền giữa các đơn. Đơn giá trị 0 cần quyết định riêng.
6. `PENDING` không làm giảm số còn phải trả, không được tính là đã thu và không chặn ghi nhận khoản thanh toán khác. Khi xác minh khoản Pending, cần kiểm tra lại nghĩa vụ còn lại theo dữ liệu mới nhất để không tính dư; nếu tiền thực nhận vượt nghĩa vụ thì giữ thông tin và chuyển xử lý ngoại lệ theo quy trình chưa chốt, không tự coi giao dịch ngân hàng không tồn tại hoặc tự chuyển sang đơn khác. Chọn COD hoặc mua nợ không tự tạo giao dịch `VERIFIED`. Đơn COD và mua nợ được duyệt có thể xuất kho, giao hàng trước khi trả đủ theo quy tắc giao hàng riêng; UC-23 không phê duyệt xuất kho hay đổi trạng thái thực hiện đơn.
7. Bảo toàn lịch sử tài chính và người/thời điểm thao tác. Không âm thầm sửa hoặc xóa giao dịch đã xác minh; điều chỉnh và đảo giao dịch cần luồng riêng được duyệt.

| From | Action | To | Conditions/Invariants |
|---|---|---|---|
| Mới | Ghi nhận tiền mặt | VERIFIED | Đã thực nhận và người thu có quyền |
| Mới | Ghi nhận khách khai báo chuyển khoản | PENDING | Chưa khẳng định đã nhận tiền |
| PENDING | Kế toán xác minh | VERIFIED hoặc REJECTED | Thuộc UC-70; nêu để xác định ranh giới tích hợp |

## 4. Data and Migration Decisions

- `finance.transactions` hiện có `orderId` dạng scalar, số tiền, phương thức, URL chứng từ, trạng thái và người thao tác; `sales.orders` có tổng tiền và trạng thái thanh toán. Các trường này chưa chứng minh service thanh toán đã triển khai.
- UC-24 hiện lọc giao dịch `RECEIPT` / `VERIFIED` theo `customerId`, quy về đơn theo `orderId` hoặc hóa đơn, và tính dư hóa đơn riêng theo `invoiceId`; UC-23 cần thống nhất phân loại và xác thực liên kết với UC-24. Chưa có contract xác thực hóa đơn cho UC-23 được phê duyệt.
- Không tạo liên kết JPA chéo module hoặc import `internal` của module khác. Hợp đồng công khai dùng ID và DTO.
- Có thể cần migration bổ sung cho chống ghi trùng, metadata chứng từ hoặc chỉ mục. Schema, ràng buộc duy nhất, tương thích dữ liệu cũ, backfill và rollback cần review; không viết lại baseline migration.
- Không diễn giải lại lịch sử tài chính khi chưa có migration được review.

## 5. Contracts

Tên và đường dẫn dưới đây chỉ là đề xuất, chưa được triển khai hoặc phê duyệt. Cấu trúc HTTP response và lỗi theo `docs/contracts/api-conventions.md`.

### HTTP API

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| GET `/api/v1/finance/orders/{orderId}/payment-summary` | Quyền xem, chưa chốt mã | Đơn được phép truy cập | Tổng tiền và trạng thái | Chưa xác thực, thiếu quyền, không có đơn |
| GET `/api/v1/finance/orders/{orderId}/payments` | Quyền xem, chưa chốt mã | Đơn hợp lệ; phân trang chưa chốt | Lịch sử giao dịch | Chưa xác thực, thiếu quyền, không có đơn |
| POST `/api/v1/finance/orders/{orderId}/payments` | Quyền ghi nhận, chưa chốt mã | Số tiền, CASH/BANK_TRANSFER, tham chiếu/chứng từ khi áp dụng, khóa chống ghi trùng; validation chưa chốt | Giao dịch và tổng quan | Số tiền/trạng thái không hợp lệ, trùng xung đột, lỗi quyền hoặc đơn |

#### Current Implementation — Awaiting Review

- `POST /api/v1/finance/orders/{orderId}/payments`: Sales Staff/Shop Owner gửi JSON `idempotencyKey` (UUID), `amount`, `paymentMethod` (`CASH` hoặc `BANK_TRANSFER`), `referenceNo` và `description` tùy chọn. Tiền mặt tạo `VERIFIED`, chuyển khoản tạo `PENDING`. Gửi lại cùng khóa và cùng dữ liệu bởi cùng actor trả giao dịch cũ. Response hiện trả **giao dịch**, gọi summary riêng để lấy số dư.
- `GET /api/v1/finance/orders/{orderId}/payments` và `GET /api/v1/finance/orders/{orderId}/payment-summary`: Sales Staff/Shop Owner/Accountant. Summary tính số đã xác minh, đang chờ và còn thiếu từ transaction `RECEIPT`.
- `POST /api/v1/finance/orders/{orderId}/payments/{paymentId}/proof` nhận multipart `file` cho chuyển khoản Pending; PNG/JPEG/PDF tối đa 5 MB, kiểm tra chữ ký file, lưu trong PostgreSQL. `GET` cùng đường dẫn cho các vai trò được xem và trả file dạng attachment. Một giao dịch có tối đa một chứng từ; chưa có chức năng thay/xóa chứng từ.
- V6 kiểm tra hồ sơ tín dụng cũ trước khi siết kỳ hạn; V7 thêm unique index hóa đơn cửa hàng theo đơn; V8 thêm khóa idempotency; V9 lưu chứng từ. Các migration có thể dừng khi dữ liệu lịch sử vi phạm; không tự sửa dữ liệu.
- Hóa đơn cửa hàng hiện được lưu `invoiceType=STORE`, `status=DRAFT`, có snapshot các dòng. UC-24 coi khoản gắn hóa đơn cửa hàng chưa được phân bổ vào hóa đơn điện tử đã phát hành. Đơn cũ chưa có hóa đơn cửa hàng vẫn có thể có giao dịch chỉ gắn đơn; kế hoạch backfill/khả năng tương thích cần review.
- Phần UC-70 xác minh, UC-63 phân bổ, hóa đơn điện tử theo đợt và xử lý tiền thực nhận vượt nghĩa vụ chưa được hiện thực ở đây. Contract/thiết kế này chờ review sau triển khai theo chỉ dẫn người dùng.

Trường tham chiếu bắt buộc và chính sách lưu chứng từ cần được review; bản hiện thực ở trên dùng file riêng trong PostgreSQL, có endpoint kiểm soát quyền và không công khai URL chứng từ.

### Inter-Module Java API

| Provider | Consumer | Method/DTO | Transaction | Errors/Compatibility |
|---|---|---|---|---|
| sales | finance | Đề xuất đọc/kiểm tra snapshot đơn gồm ID, số phải thu, trạng thái và phiên bản | Nhất quán và khóa chưa chốt | Không có đơn, không đủ điều kiện, đơn đã thay đổi |
| finance / sales | sales / finance | Đề xuất cập nhật hoặc phát sự kiện trạng thái thanh toán | Chiến lược chưa chốt | Cần xác định retry, trạng thái cũ và phục hồi |
| finance | sales | `CreditControlApi.authorize`: kiểm tra cuối theo khách, COUNTER và tổng đơn | Đề xuất cùng transaction ghi đơn, khóa theo khách đến commit; review propagation và thứ tự khóa | Từ chối/lỗi kiểm tra rollback; kiểm tra tương thích caller cũ |

### Domain Event

`OrderPaymentStatusChangedEvent` là phương án kiến trúc chưa được duyệt. Nếu chọn, phải xác định payload/phiên bản, thời điểm commit, chống xử lý trùng, thứ tự, retry, theo dõi lỗi và đối soát. `docs/architecture/module-contracts.md` vẫn để mở cơ chế nhất quán `finance` → `sales`.

## 6. Concurrency, Transaction and Failure Safety

- Gửi lại cùng yêu cầu không được tạo hai giao dịch. Chốt phạm vi khóa chống trùng, ràng buộc duy nhất, cách trả kết quả khi gửi lại và khi dữ liệu xung đột.
- Ghi tiền mặt đồng thời hoặc thay đổi tổng đơn không được làm sai số dư. Chọn cơ chế khóa/phiên bản theo đơn và ranh giới transaction với `sales`; không nhầm khoản đang chờ với tiền đã nhận.
- Lưu thanh toán và cơ chế cập nhật trạng thái `sales` nhất quán, có khả năng phục hồi. Xác định retry và đối soát khi cập nhật liên module thất bại; không mặc định listener sau commit là đủ tin cậy.
- Xác định cách xử lý khi lưu chứng từ thành công nhưng ghi database thất bại, hoặc ngược lại; bảo vệ quyền truy cập chứng từ.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | Người có quyền ghi tiền mặt thực nhận | Kiểm thử tích hợp và phân quyền | Một giao dịch VERIFIED, tổng quan/trạng thái được cập nhật |
| AC-02 | Ghi nhận chuyển khoản khách khai báo | Kiểm thử tích hợp | PENDING; số đã xác minh và số còn thiếu không đổi |
| AC-03 | Nhiều đợt, kết hợp phương thức | Kiểm thử tích hợp | Lịch sử riêng, tổng hợp chính xác |
| AC-04 | Đơn, số tiền hoặc quyền không hợp lệ | Kiểm thử validation và bảo mật | Từ chối, không thay đổi sổ giao dịch |
| AC-05 | Gửi trùng và ghi nhận đồng thời | Kiểm thử đồng thời/chống trùng trên PostgreSQL | Không tạo trùng hoặc tính sai số đã xác minh |
| AC-06 | Cập nhật finance → sales thất bại | Kiểm thử tích hợp lỗi/phục hồi | Phục hồi bền vững theo thiết kế được duyệt |
| AC-07 | COD/mua nợ và thực hiện đơn | Kiểm thử ranh giới | Không tự tạo VERIFIED hay đổi trạng thái thực hiện đơn |
| AC-08 | Truy cập chứng từ | Kiểm thử quyền và lỗi lưu trữ | Chỉ người có quyền truy cập; xử lý lỗi theo chính sách được duyệt |
| AC-09 | Giao dịch trực tiếp gắn hóa đơn hoặc chỉ gắn đơn | Kiểm thử contract UC-23/UC-24 và danh tính đơn/khách/hóa đơn | Tính đúng dư đơn và dư hóa đơn theo spec UC-24; từ chối liên kết không khớp |
| AC-10 | Phụ thuộc UC-63: đơn 100 triệu, đã thu 30 triệu, hóa đơn điện tử đợt đầu 40 triệu | Kiểm thử tích hợp sau khi UC-63 được thiết kế và triển khai | Còn phải trả toàn đơn 70 triệu; hóa đơn đợt đầu còn 10 triệu; 60 triệu chưa xuất hóa đơn chưa tính quá hạn; chỉ có một khoản thu 30 triệu. Không coi là AC độc lập đã đạt của UC-23. |
| AC-11 | Phụ thuộc UC-63: khoản trả trước lớn hơn hóa đơn đầu tiên và có hóa đơn đợt sau | Kiểm thử PostgreSQL đồng thời, retry và xử lý trùng khi UC-63 có code | Tự phân bổ phần dư sang hóa đơn tiếp theo cùng đơn; không vượt số đã thu hoặc giá trị hóa đơn; không phân bổ Pending. |
| AC-12 | Phụ thuộc UC-63: khách trả sau cho hóa đơn chỉ định | Kiểm thử phân quyền, xác minh và phân bổ khi UC-63 có code | Kế toán chọn đúng hóa đơn của đơn; không tự chuyển khoản sang hóa đơn khác hoặc đơn khác. |
| AC-13 | Nhập số tiền vượt nghĩa vụ đơn hoặc Accountant ghi nhận tại quầy | Kiểm thử validation và phân quyền | Từ chối theo quy tắc được duyệt; không tạo giao dịch sai |
| AC-14 | Có khoản Pending và nhân viên ghi khoản thanh toán khác | Kiểm thử tích hợp và đồng thời UC-23/UC-70 | Cho ghi khoản hợp lệ; Pending không tính đã thu; xác minh lại số còn phải trả, xử lý ngoại lệ khi thực nhận vượt nghĩa vụ theo thiết kế được duyệt |
| AC-15 | Tạo đơn UC-21 thành công / lỗi tạo hóa đơn cửa hàng | Kiểm thử PostgreSQL và rollback | Một hóa đơn cửa hàng đúng đơn/khách; lỗi tạo đơn/hóa đơn rollback toàn bộ; không khởi động hạn nợ hóa đơn điện tử |
| AC-16 | Hai đơn mua nợ đồng thời vượt hạn mức nếu cộng dồn | Kiểm thử PostgreSQL với transaction độc lập | Kiểm tra cuối được tuần tự hóa theo khách đến commit; không chấp nhận cả hai đơn vượt hạn mức |
| AC-17 | Đơn COUNTER và dữ liệu/caller kênh cũ | Kiểm thử contract, migration và API | COUNTER được UC-24 nhận; xử lý giá trị cũ theo thiết kế duyệt; không đổi IN_STORE_PICKUP |
| AC-18 | Kỳ hạn nợ null, ngoài 1–30 và dữ liệu lịch sử | Unit và PostgreSQL migration/integration tests | Từ chối cấp tín dụng không hợp lệ; DB không chấp nhận hồ sơ mới sai; migration dừng an toàn khi phát hiện dữ liệu cần xử lý |
| AC-19 | Đơn 100 triệu có ba mốc 30/40/30; đã thu 40 triệu VERIFIED | Kiểm thử contract Sales–Finance và API tổng quan | Mốc 1 đã trả 30, còn 0; mốc 2 đã trả 10, còn 30; mốc 3 đã trả 0, còn 30; toàn đơn đã trả 40, còn 60; không tạo thêm transaction. |
| AC-20 | Thêm khoản chuyển khoản 20 triệu PENDING vào ví dụ AC-19 | Kiểm thử tích hợp UC-23 và UC-70 sau khi UC-70 có code | Trước xác minh, tiến độ mốc không đổi, PENDING hiển thị riêng; sau xác minh thành công, mốc 2 đã trả 30, còn 10 và toàn đơn còn 40. |
| AC-21 | Nhiều khoản VERIFIED, một khoản trải qua nhiều mốc hoặc gửi lại request | Kiểm thử PostgreSQL và idempotency | Mốc được tính tuần tự cùng đơn; không cộng tiền hai lần; tổng đã tính vào mốc không vượt tổng VERIFIED hoặc số tiền từng mốc. |
| AC-22 | Không có mốc, trùng thứ tự, tổng mốc không khớp tổng đơn hoặc làm tròn | Kiểm thử sau khi các chính sách dữ liệu được duyệt | Kết quả/lỗi được định nghĩa theo chính sách đã chốt; không tự giả định mốc đã đủ hoặc tạo giao dịch mới. |
| AC-23 | Đọc tiến độ khi ghi tiền hoặc xác minh đồng thời | Kiểm thử PostgreSQL với transaction độc lập | Kết quả phản ánh cùng ảnh chụp nhất quán; không đếm trùng và tuân thủ contract khóa đã review. |

## 8. Open Decisions

| Decision | Owner | Required Before | Status/Resolution |
|---|---|---|---|
| Trạng thái đơn/hóa đơn được phép, đơn điều chỉnh và đơn giá trị 0 | Product Owner / phụ trách Sales | Design Review | OPEN — UC-21/22 và BA cũ mâu thuẫn hoặc thiếu |
| Contract sales–finance, cạnh tranh khi đổi tổng đơn và đồng bộ đáng tin cậy | Tech Lead Sales / Finance | Design Review | OPEN — kiến trúc để mở đối soát |
| Mã quyền cụ thể và ánh xạ IAM | Product Owner / phụ trách IAM | Design Review | Chốt Sales Staff/Shop Owner ghi và xem; Accountant chỉ xem, xác minh và phân bổ; OPEN mã quyền và tương thích ma trận cũ |
| Độ chính xác số tiền và nhiều khoản Pending cạnh tranh | Product Owner / phụ trách Finance | Design Review | Đã chốt Pending không chặn khoản khác, không trả thừa đơn hoặc tự chuyển tiền sang đơn khác; OPEN quy trình ngoại lệ khi ngân hàng thực nhận vượt nghĩa vụ, tiền mặt khách đưa dư và cơ chế đồng thời |
| Mã tham chiếu/chứng từ bắt buộc, lưu trữ và truy cập hạn chế | Product Owner / phụ trách Security | Design Review | OPEN |
| Biên nhận UC-23, phiếu thu UC-63 và tích hợp audit | Phụ trách Finance | Design Review | OPEN — tránh ghi tiền hai lần |
| Phân loại dữ liệu hiện có, migration và phục hồi | Phụ trách Finance / Database | Design Review | OPEN |
| Ai tạo/phát hành hóa đơn, thời điểm phát hành, đơn chưa có hóa đơn và nhiều hóa đơn | Product Owner / phụ trách Sales, Finance | Design Review | OPEN — UC-21 chưa tạo hóa đơn; UC-24 dùng hóa đơn ISSUED để tính quá hạn |
| Cơ chế phân bổ khoản trả trước và trả sau | Phụ trách UC-23 / UC-24 / UC-63 | Design Review | Chốt tự phân bổ tiền trả trước vào hóa đơn đầu và phần dư sang các đợt sau cùng đơn; kế toán gắn khoản trả sau theo hóa đơn khách trả. OPEN contract, dữ liệu, đồng thời và cập nhật spec UC-24/UC-63 |
| Tích hợp kiểm tra tín dụng UC-21/UC-24 và tương thích kênh/hạn trả | Phụ trách UC-21 / UC-24 | Design Review | Đã chốt UC-21 gọi UC-24, `COUNTER` là kênh chuẩn; OPEN thiết kế tương thích dữ liệu/caller `IN_STORE`/`POS`, đồng thời và sửa sai lệch code/spec |
| Mốc trùng thứ tự, mốc thiếu, tổng mốc khác tổng đơn, làm tròn | Owner báo giá / Sales / Finance | Design Review | OPEN — thống nhất validation, thứ tự ổn định, mốc cọc có là một mốc hay dữ liệu riêng, và hành vi đơn không có mốc. |
| Thay đổi mốc, tổng đơn hoặc hoàn/đảo giao dịch đã xác minh | Owner Sales / Finance / UC-26 | Design Review / tích hợp | OPEN — snapshot, quyền sửa, cách tính lại, kiểm soát đồng thời và lịch sử; không tự viết lại transaction. |
| Hạn của mốc thỏa thuận so với hạn công nợ hóa đơn | Owner UC-23 / UC-24 / UC-63 | Design Review / tích hợp | OPEN — mốc hiện chưa có ngày đến hạn; phân biệt tiến độ theo thỏa thuận với quá hạn hóa đơn điện tử, tránh cộng nợ hai lần. |

Không duyệt thiết kế hoặc triển khai các phần bị ảnh hưởng khi các quyết định trên còn cản trở thiết kế an toàn.
