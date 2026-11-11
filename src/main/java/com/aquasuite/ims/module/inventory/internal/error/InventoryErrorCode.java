package com.aquasuite.ims.module.inventory.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InventoryErrorCode implements BaseErrorCode {
    INVENTORY_NOT_FOUND("INV_001", "Không tìm thấy thông tin tồn kho của sản phẩm: {0}", HttpStatus.NOT_FOUND),
    INSUFFICIENT_AVAILABLE_STOCK("INV_002", "Số lượng tồn khả dụng không đủ. Cần: {0}, Khả dụng: {1}", HttpStatus.BAD_REQUEST),
    NEGATIVE_ON_HAND_STOCK("INV_003", "Thao tác làm âm tồn kho thực tế. Hiện tại: {0}, Điều chỉnh: {1}", HttpStatus.BAD_REQUEST),
    STOCK_COUNT_NOT_FOUND("INV_004", "Không tìm thấy phiếu kiểm kho: {0}", HttpStatus.NOT_FOUND),
    INVALID_STOCK_COUNT_STATUS("INV_005", "Trạng thái phiếu kiểm kho không hợp lệ: {0}", HttpStatus.BAD_REQUEST),
    SUPPLIER_NOT_FOUND("INV_006", "Không tìm thấy nhà cung cấp: {0}", HttpStatus.NOT_FOUND),
    DUPLICATE_STOCK_COUNT_ITEM("INV_007", "Mã SKU bị lặp lại nhiều lần trong cùng phiếu kiểm kê: {0}", HttpStatus.BAD_REQUEST),
    INVALID_ADJUST_QUANTITY("INV_008", "Số lượng điều chỉnh kho phải khác 0", HttpStatus.BAD_REQUEST),
    INVENTORY_NOT_EMPTY("INV_009", "Không thể xóa tồn kho vì SKU vẫn còn tồn, đang giữ hoặc đã có lịch sử kho", HttpStatus.CONFLICT),
    GOODS_RECEIPT_PO_NOT_FOUND("INV_010", "Không tìm thấy đơn mua hàng để nhập kho: {0}", HttpStatus.NOT_FOUND),
    GOODS_RECEIPT_PO_NOT_RECEIVABLE("INV_011", "Đơn mua hàng không ở trạng thái cho phép nhập kho (cần CONFIRMED hoặc PARTIALLY_RECEIVED). Trạng thái hiện tại: {0}", HttpStatus.BAD_REQUEST),
    GOODS_RECEIPT_EMPTY_ITEMS("INV_012", "Phiếu nhập phải có ít nhất một dòng với số lượng lớn hơn 0", HttpStatus.BAD_REQUEST),
    GOODS_RECEIPT_DUPLICATE_ITEM("INV_013", "Dòng đơn mua bị lặp lại nhiều lần trong cùng phiếu nhập: {0}", HttpStatus.BAD_REQUEST),
    GOODS_RECEIPT_ITEM_NOT_IN_PO("INV_014", "Dòng nhập không thuộc đơn mua hàng này: {0}", HttpStatus.BAD_REQUEST),
    GOODS_RECEIPT_INVALID_QUANTITY("INV_015", "Số lượng nhận phải lớn hơn 0. Giá trị nhập: {0}", HttpStatus.BAD_REQUEST),
    GOODS_RECEIPT_EXCEEDS_REMAINING("INV_016", "Số lượng nhận vượt quá số còn phải nhận. Nhận: {0}, Còn lại: {1}", HttpStatus.BAD_REQUEST),
    GOODS_RECEIPT_NOT_FOUND("INV_017", "Không tìm thấy phiếu nhập kho: {0}", HttpStatus.NOT_FOUND),
    INVALID_THRESHOLD("INV_018", "Ngưỡng tồn kho không hợp lệ: tối thiểu phải ≥ 0 và tối đa (nếu có) phải ≥ tối thiểu", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
