# Hướng dẫn viết Use Case Specification (khuôn của nhóm)

Tài liệu này mô tả **cách làm** một use case specification (kèm sequence diagram và class diagram) theo khuôn đã thống nhất, để người đọc (người hoặc AI) làm được mà không cần hỏi lại. Ví dụ mẫu: `UC-02.1-use-case-specification.vi.md` (Đăng nhập) và `UC-03.5-use-case-specification.vi.md` (Quản lý quyền của vai trò).

Người phụ trách (Created By): Pham Van Nam. Thư mục lưu spec: `docs/specifications/NAM/`.

---

## 1. Nguyên tắc cốt lõi

1. **Spec viết theo code hiện tại**, không theo spec BA cũ. Spec BA (repo `D:\DO_AN\aqs-ba`) chỉ để tham khảo ý nghĩa nghiệp vụ.
2. **Có mâu thuẫn giữa BA và code thì dừng lại, báo người phụ trách và hỏi** (quy tắc của repo, `AGENTS.md`). Không tự giả định. Phần BA có mà code chưa có thì ghi vào bảng "Ngoài phạm vi".
3. **Tiếng Việt trước, tiếng Anh sau.** Hai bản cùng cấu trúc, cùng số bước, đồng bộ khi sửa. Thông báo hiển thị cho người dùng **luôn giữ nguyên chữ tiếng Việt** trong cả bản tiếng Anh.
4. **Thông báo ghi thẳng câu hiển thị**, trong dấu “…”. **Không ghi số MSG** (danh mục MSG của nhóm chưa thống nhất) và **không ghi mã lỗi nội bộ** (AUTH_xxx, SVD_xxx, SYS_003…). Chữ thông báo lấy đúng từ code/giao diện, không tự soạn.
5. **Không ghi chi tiết cài đặt** trong thân spec: không endpoint, không tên class/hàm, không HTTP status. Các chi tiết này chỉ dùng khi phân tích để chắc spec khớp code.
6. **Quy tắc nghiệp vụ nằm trong bảng GBR** và được dẫn chiếu ngay trong từng bước (ví dụ "theo GBR-AU-01"); nội dung kiểm tra dữ liệu thì viết thẳng vào bước.
7. **Không commit, không push** trừ khi người phụ trách yêu cầu rõ. Không sửa file `.vpp` đang mở trong Visual Paradigm (có file `.lock`): tạo bản `_v2` cạnh bên.

## 2. Quy trình làm một UC

1. **Xác định tên và phạm vi UC.** Nếu tên/ mã UC không khớp với thực tế code (ví dụ "Tạo vai trò tùy biến" nhưng code chỉ có 5 vai trò cố định) thì đề xuất tên đúng và nói rõ lý do.
2. **Đọc nguồn theo thứ tự:**
   1. Spec BA của UC (`aqs-ba/usecase-description/` tiếng Anh, `aqs-ba/usecase-des/` tiếng Việt). Lưu ý hai bản có thể đánh số khác nhau.
   2. Code backend: controller (endpoint, quyền), service/impl (luồng, kiểm tra, lỗi), DTO (ràng buộc dữ liệu), entity/repository, enum, mã lỗi (`*ErrorCode.java`).
   3. Code giao diện (`aquasuite-fe`): màn hình, nút, ô nhập, **chữ thông báo thật** (toast, lỗi từng ô, hộp xác nhận), chuyển trang.
   4. Test (`src/test`) để xác nhận hành vi (ví dụ trạng thái tài khoản nào bị từ chối).
   5. Tài liệu có sẵn trong `docs/` (spec IAM, contracts).
3. **Lập danh sách khác biệt BA và code**, báo người phụ trách và chốt hướng (thường là theo code).
4. **Viết spec tiếng Việt**, duyệt, rồi **chuyển sang tiếng Anh**.
5. **Vẽ sequence diagram** rồi **class diagram** (mục 5, 6) khi được yêu cầu.
6. **Đối chiếu lần cuối** từng câu trong spec với code (ràng buộc độ dài, thời hạn token, thứ tự kiểm tra, chữ thông báo).

## 3. Khuôn spec (bảng)

Tiêu đề: `# UC-<mã> – <Tên tiếng Việt> (<Tên tiếng Anh>)`, rồi một dòng ghi chú ngôn ngữ/nguồn. Sau đó bảng hai cột:

```
|  |  |
| :-: | :-- |
| Tác nhân chính | ... |
| Tác nhân phụ | Không có |
| Người tạo | Pham Van Nam |
| Ngày tạo | dd/mm/yyyy |
| Mô tả | Là <tác nhân>, tôi muốn ... để ... |
| Điều kiện tiên quyết | 1. ...<br>2. ... |
| Luồng chính | **<Tên luồng>**<br>1. ...<br>2. ... |
| Luồng thay thế | **Bước N_<Tên>**<br>1. ...<br>2. ... Use case kết thúc. |
| Luồng ngoại lệ | **Bước N_<Tên>**<br>1. ...<br>2. ... Quay lại bước M. |
| Hậu điều kiện | 1. ...<br>2. Khi thất bại ... |
```

Trong ô, các bước cách nhau bằng `<br>`, các luồng cách nhau bằng `<br><br>`.

Sau bảng chính có các mục:

- `## Quy tắc nghiệp vụ`: bảng 5 cột `GBR-ID | Nội dung quy tắc | Áp dụng cho | Lý do | Thực thi bởi` (bản Anh: `GBR-ID | Rule Statement | Applies To | Rationale | Enforced By`).
- `## Ngoài phạm vi (code hiện tại chưa hỗ trợ ...)`: bảng `Nội dung | Ghi chú`.

### Quy tắc viết nội dung

- **Luồng chính:** một đường thành công, mỗi bước là hành động của tác nhân hoặc của hệ thống, không có điều kiện rẽ nhánh. Câu dạng "Hệ thống kiểm tra ... theo GBR-xx-nn." Chỉ gọi tên, không giải thích cách cài đặt.
- **Luồng thay thế:** các cách khác để đạt mục tiêu hoặc thao tác khác hợp lệ (gỡ quyền, tìm kiếm, hủy, chọn "Đăng ký"). Đặt tên `Bước N_<tên>` với N là bước rẽ nhánh.
- **Luồng ngoại lệ:** các tình huống lỗi. Mỗi luồng nêu điều kiện, câu thông báo hiển thị, và **kết thúc rõ ràng**: "Quay lại bước M." hoặc "Use case kết thúc."
- **Hậu điều kiện:** tách "khi thành công" và "khi thất bại" (dữ liệu không đổi).
- Mỗi trạng thái/giá trị phải khớp code (ví dụ trạng thái tài khoản, thời hạn token, giới hạn độ dài).
- Vai trò: chỉ dùng 5 vai trò hiện có: Chủ cửa hàng, Nhân viên bán hàng, Kế toán, Nhân viên giao hàng (Shipper), Khách hàng. Vai trò Kỹ thuật viên đã bỏ.

### Bảng GBR

- **GBR-ID:** `GBR-<module>-<nn>`, ví dụ `GBR-AU-01` (Authentication), `GBR-AC-01` (Access Control). Số liên tục trong từng module; mã không trùng giữa các module.
- **Nội dung quy tắc:** một quy tắc rõ ràng, kiểm tra được.
- **Chỉ đặt GBR cho quy tắc nghiệp vụ** (chính sách, cách tính, trạng thái, ràng buộc dữ liệu có ý nghĩa nghiệp vụ như mã/tên duy nhất, không vượt số còn lại, không có vòng lặp). **Không đặt GBR** cho: kiểm tra dữ liệu nhập (bắt buộc, độ dài, định dạng), phân trang/sắp xếp/tìm kiếm, khóa xử lý đồng thời, chống gửi trùng, hành vi màn hình, và ai được dùng (đã có ở Điều kiện tiên quyết). Các nội dung này viết thẳng vào bước kiểm tra và luồng ngoại lệ. Mỗi use case thường chỉ có 3–5 GBR.
- **Áp dụng cho:** mã UC và bước, ví dụ `UC-02.1 (bước 5)`; thêm các UC khác nếu dùng chung.
- **Lý do:** vì sao có quy tắc (bảo mật, toàn vẹn dữ liệu, thuận tiện...). Chỉ dẫn chiếu R1/R2 khi biết chắc.
- **Thực thi bởi:** thành phần chịu trách nhiệm ở mức cao (Giao diện đăng nhập, Hệ thống/dịch vụ xác thực, Cơ sở dữ liệu), không ghi tên class.

## 4. Quy ước thông báo

- Chỉ ghi câu thấy trên màn hình, lấy từ giao diện (toast, lỗi dưới ô nhập, hộp xác nhận) hoặc từ thông báo lỗi backend khi giao diện hiển thị lại nó.
- Một số lỗi được giao diện đổi chữ (ví dụ `AUTH_003` hiện "Email hoặc mật khẩu không chính xác."). Luôn ghi chữ **người dùng thật sự thấy**.
- Nếu một câu có chỗ điền động, dùng `<...>` (ví dụ `<ticketId>`, `<trạng thái>`).

## 5. Sequence diagram

- Một UC một sơ đồ (nếu quá dài thì tách UC, không nhồi). Người tham gia: tác nhân, giao diện (`boundary`), controller, service, repository (`entity`), dịch vụ ngoài/ listener (`control`).
- Tin nhắn đánh số liên tục. Lỗi vẽ bằng khung `break` (có điều kiện), lựa chọn vẽ bằng `alt`. Tin nhắn hiển thị cho người dùng ghi `Display "<chữ thông báo tiếng Việt>"`.
- Kết xuất: file nguồn `docs/diagrams/UC<..>_Sequence.puml`, file Visual Paradigm `.vpp` và `.svg` ở `D:\DO_AN\Diagram`.
- Công cụ: `D:\DO_AN\Diagram\tools\` (`gen_ws.py` định nghĩa luồng một lần rồi sinh cả `.puml` và file dựng `.vpp`; `build_vpp.sh` gọi Visual Paradigm CLI; cần cài Visual Paradigm 18). Xem ví dụ trong `gen_ws.py`.
- Kiểm tra `.lock` trước khi ghi đè `.vpp`; nếu đang mở thì ghi `_v2`.

## 6. Class diagram (draw.io)

- Mỗi hộp: `«Stereotype»` + tên (giữa), ô thuộc tính (`-ten: Kieu`), ô phương thức (`+ten(...): Kieu`). Interface và Repository in nghiêng.
- Stereotype: `«RestController»`, `«interface»`, `«Service»`, `«Repository»`, `«Entity»`, `«record»` (Java record), `«DTO»` (lớp DTO thường), `«enum»`, `«Listener»`.
- Mũi tên: quan hệ dùng `uses` (nét liền, đầu tam giác đặc) có số `1` hai đầu; `returns`, `receives`, Repository → Entity `uses` (nét đứt, đầu tam giác đặc); `implements` (nét đứt, tam giác rỗng); nhãn nằm cạnh đường (bên phải đường dọc, phía trên đường ngang).
- Công cụ: `tools/classgen.py` + ví dụ `tools/make3.py` (sinh file `.drawio`). Lấy đúng tên lớp, trường, chữ ký từ code.

## 7. Checklist trước khi giao

- [ ] Tên và mã UC đúng với thực tế code.
- [ ] Mọi câu trong spec đã đối chiếu code (độ dài, thời hạn, thứ tự kiểm tra, trạng thái).
- [ ] Thông báo là chữ tiếng Việt thật, không số MSG, không mã lỗi nội bộ.
- [ ] Mỗi luồng ngoại lệ có "Quay lại bước M." hoặc "Use case kết thúc."
- [ ] Bảng GBR đủ 5 cột; mọi GBR trong bước đều có trong bảng.
- [ ] Mục "Ngoài phạm vi" liệt kê phần BA có mà code chưa có.
- [ ] Bản tiếng Việt và tiếng Anh cùng cấu trúc, cùng số bước.
- [ ] File lưu trong `docs/specifications/NAM/`, tên `UC-<mã>-use-case-specification.vi.md` và `UC-<mã>-use-case-specification.md`.
- [ ] Chưa commit/push (trừ khi được yêu cầu).
