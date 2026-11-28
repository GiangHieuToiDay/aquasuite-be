package com.aquasuite.ims.service.servicedesk;

/**
 * Dịch vụ đồng bộ cấp thẻ bảo hành điện tử tự động cho đơn hoàn thành tại quầy và phiếu giao đã giao.
 */
public interface WarrantyAutoIssuanceService {

    /**
     * Quét và cấp thẻ bảo hành tự động (idempotent) cho các dòng đủ điều kiện chưa có thẻ.
     *
     * @return số thẻ bảo hành mới được tạo thành công trong lần đồng bộ này
     */
    int syncEligibleRecords();
}
