package com.aquasuite.ims.module.inventory.internal.service;

import com.aquasuite.ims.module.inventory.internal.payload.req.CreateStockCountRequest;
import com.aquasuite.ims.module.inventory.internal.payload.res.StockCountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface StockCountService {

    /**
     * Tạo phiếu kiểm kho dạng nháp (DRAFT)
     */
    StockCountResponse createDraftStockCount(CreateStockCountRequest request, UUID actorUserId);

    /**
     * Duyệt phiếu kiểm kho (APPROVE) -> Cân bằng tồn kho và ghi thẻ kho
     */
    StockCountResponse approveStockCount(UUID stockCountId, UUID approverUserId);

    /**
     * Hủy phiếu kiểm kho
     */
    StockCountResponse cancelStockCount(UUID stockCountId);

    /**
     * Chi tiết phiếu kiểm kho
     */
    StockCountResponse getStockCountDetail(UUID stockCountId);

    /**
     * Danh sách phiếu kiểm kho phân trang
     */
    Page<StockCountResponse> listStockCounts(Pageable pageable);
}
