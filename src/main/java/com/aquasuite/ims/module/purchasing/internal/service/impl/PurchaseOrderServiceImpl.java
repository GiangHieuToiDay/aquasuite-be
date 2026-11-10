package com.aquasuite.ims.module.purchasing.internal.service.impl;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.GoodsReceiptSummaryDto;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.module.inventory.internal.entity.Supplier;
import com.aquasuite.ims.module.inventory.internal.entity.SupplierProduct;
import com.aquasuite.ims.module.inventory.internal.enums.SupplierStatus;
import com.aquasuite.ims.module.inventory.internal.repository.SupplierProductRepository;
import com.aquasuite.ims.module.inventory.internal.repository.SupplierRepository;
import com.aquasuite.ims.module.purchasing.api.PurchasingApi;
import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrder;
import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrderItem;
import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrderStage;
import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrderStageRollbackRequest;
import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderDeliveryType;
import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStageStatus;
import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStatus;
import com.aquasuite.ims.module.purchasing.internal.enums.RollbackRequestStatus;
import com.aquasuite.ims.module.purchasing.internal.error.PurchasingErrorCode;
import com.aquasuite.ims.module.purchasing.internal.payload.request.*;
import com.aquasuite.ims.module.purchasing.internal.payload.response.*;
import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderItemRepository;
import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderRepository;
import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderStageRepository;
import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderStageRollbackRequestRepository;
import com.aquasuite.ims.module.purchasing.internal.service.PoCodeGenerator;
import com.aquasuite.ims.module.purchasing.internal.service.PurchaseOrderCalculator;
import com.aquasuite.ims.module.purchasing.internal.service.PurchaseOrderPdfService;
import com.aquasuite.ims.module.purchasing.internal.service.PurchaseOrderService;
import com.aquasuite.ims.share.dto.PageResponse;
import com.aquasuite.ims.share.exception.AppException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final PurchaseOrderStageRepository stageRepository;
    private final PurchaseOrderStageRollbackRequestRepository rollbackRequestRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final CatalogApi catalogApi;
    private final InventoryApi inventoryApi;
    private final PurchasingApi purchasingApi;
    private final IamApi iamApi;
    private final PoCodeGenerator poCodeGenerator;
    private final PurchaseOrderCalculator calculator;
    private final PurchaseOrderPdfService pdfService;

    @Override
    @Transactional
    public PurchaseOrderResponse createDraft(CreatePurchaseOrderRequest request) {
        log.info("Creating PO Draft for supplier: {}", request.getSupplierId());

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new AppException(PurchasingErrorCode.SUPPLIER_NOT_FOUND, request.getSupplierId()));

        if (supplier.getStatus() != SupplierStatus.ACTIVE) {
            throw new AppException(PurchasingErrorCode.SUPPLIER_NOT_ACTIVE, supplier.getSupplierName());
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(PurchasingErrorCode.PO_ITEMS_EMPTY);
        }

        // Duplicate SKU check
        Set<UUID> seenVariants = new HashSet<>();
        for (CreatePurchaseOrderRequest.CreatePurchaseOrderItemRequest item : request.getItems()) {
            if (!seenVariants.add(item.getVariantId())) {
                throw new AppException(PurchasingErrorCode.DUPLICATE_SKU_IN_PO, item.getVariantId());
            }
        }

        List<UUID> variantIds = request.getItems().stream()
                .map(CreatePurchaseOrderRequest.CreatePurchaseOrderItemRequest::getVariantId)
                .toList();
        Map<UUID, CatalogSkuSummaryDto> skuMap = catalogApi.getSkuSummaries(variantIds);

        PurchaseOrderDeliveryType deliveryType = PurchaseOrderDeliveryType.SINGLE;
        if (request.getDeliveryPlanType() != null && !request.getDeliveryPlanType().isBlank()) {
            try {
                deliveryType = PurchaseOrderDeliveryType.valueOf(request.getDeliveryPlanType().trim().toUpperCase());
            } catch (Exception ignored) {
            }
        }

        PurchaseOrder po = PurchaseOrder.builder()
                .poCode(poCodeGenerator.generatePoCode())
                .supplierId(supplier.getSupplierId())
                .warehouseId(request.getWarehouseId())
                .warehouseCode(request.getWarehouseCode() != null && !request.getWarehouseCode().isBlank()
                        ? request.getWarehouseCode().trim()
                        : "WH_MAIN")
                .status(PurchaseOrderStatus.DRAFT)
                .orderDate(LocalDate.now())
                .expectedDate(request.getExpectedDate())
                .note(request.getNote())
                .totalAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .isShortClosed(false)
                .deliveryPlanType(deliveryType)
                .createdBy(iamApi.getCurrentUserId())
                .version(0)
                .build();

        int sortOrder = 0;
        for (CreatePurchaseOrderRequest.CreatePurchaseOrderItemRequest itemReq : request.getItems()) {
            CatalogSkuSummaryDto skuDto = skuMap.get(itemReq.getVariantId());
            if (skuDto == null) {
                throw new AppException(PurchasingErrorCode.SKU_NOT_FOUND, itemReq.getVariantId());
            }
            if (!"ACTIVE".equalsIgnoreCase(skuDto.status())) {
                throw new AppException(PurchasingErrorCode.SKU_NOT_ACTIVE, skuDto.sku());
            }
            if (!calculator.isValidQuantity(itemReq.getQuantity(), skuDto.unit())) {
                throw new AppException(PurchasingErrorCode.INVALID_QUANTITY, skuDto.sku());
            }

            PurchaseOrderItem item = PurchaseOrderItem.builder()
                    .variantId(skuDto.variantId())
                    .sku(skuDto.sku())
                    .productName(skuDto.productName() != null ? skuDto.productName() : skuDto.variantName())
                    .unit(skuDto.unit() != null ? skuDto.unit() : "cái")
                    .quantityOrdered(itemReq.getQuantity())
                    .quantityReceived(BigDecimal.ZERO)
                    .unitCost(null)
                    .lineTotal(null)
                    .sortOrder(sortOrder++)
                    .build();

            po.addItem(item);
        }

        if (request.getStages() != null && !request.getStages().isEmpty()) {
            int stageSort = 0;
            for (CreatePurchaseOrderStageRequest stReq : request.getStages()) {
                PurchaseOrderStage st = PurchaseOrderStage.builder()
                        .stageId(stReq.getStageId() != null ? stReq.getStageId() : UUID.randomUUID())
                        .stageName(stReq.getStageName() != null ? stReq.getStageName() : "Đợt " + (stageSort + 1))
                        .description(stReq.getDescription())
                        .expectedDeliveryDate(stReq.getExpectedDeliveryDate() != null ? stReq.getExpectedDeliveryDate()
                                : request.getExpectedDate())
                        .percent(stReq.getPercent() != null ? stReq.getPercent() : new BigDecimal("100.00"))
                        .amount(stReq.getAmount() != null ? stReq.getAmount() : BigDecimal.ZERO)
                        .status(PurchaseOrderStageStatus.PENDING)
                        .sortOrder(stReq.getSortOrder() != null ? stReq.getSortOrder() : stageSort++)
                        .build();
                po.addStage(st);
            }
        }

        PurchaseOrder savedPo = purchaseOrderRepository.save(po);
        log.info("PO Draft created successfully: {}", savedPo.getPoCode());
        return mapToDetailResponse(savedPo, supplier);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderResponse getDetail(UUID purchaseOrderId) {
        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.PO_NOT_FOUND));
        Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
        return mapToDetailResponse(po, supplier);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse updateDraft(UUID purchaseOrderId, UpdatePurchaseOrderRequest request) {
        log.info("Updating PO Draft: {}", purchaseOrderId);

        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.PO_NOT_FOUND));

        if (po.getStatus() == PurchaseOrderStatus.CONFIRMED || po.getStatus() == PurchaseOrderStatus.COMPLETED
                || po.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new AppException(PurchasingErrorCode.PO_LOCKED_ITEMS_CANNOT_EDIT);
        }
        if (po.getStatus() != PurchaseOrderStatus.DRAFT && po.getStatus() != PurchaseOrderStatus.QUOTED) {
            throw new AppException(PurchasingErrorCode.PO_LOCKED_ITEMS_CANNOT_EDIT);
        }

        if (!Objects.equals(po.getVersion(), request.getVersion())) {
            throw new AppException(PurchasingErrorCode.PO_VERSION_CONFLICT);
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(PurchasingErrorCode.PO_ITEMS_EMPTY);
        }

        boolean hasAnyPrice = request.getItems().stream()
                .anyMatch(it -> it.getUnitCost() != null && it.getUnitCost().compareTo(BigDecimal.ZERO) > 0);
        if (hasAnyPrice && request.getExpectedDate() == null) {
            throw new AppException(PurchasingErrorCode.EXPECTED_DATE_REQUIRED);
        }

        if (hasAnyPrice && po.getStatus() == PurchaseOrderStatus.DRAFT) {
            po.setStatus(PurchaseOrderStatus.QUOTED);
        }

        // Duplicate SKU check
        Set<UUID> seenVariants = new HashSet<>();
        for (UpdatePurchaseOrderRequest.UpdatePurchaseOrderItemRequest item : request.getItems()) {
            if (!seenVariants.add(item.getVariantId())) {
                throw new AppException(PurchasingErrorCode.DUPLICATE_SKU_IN_PO, item.getVariantId());
            }
        }

        List<UUID> variantIds = request.getItems().stream()
                .map(UpdatePurchaseOrderRequest.UpdatePurchaseOrderItemRequest::getVariantId)
                .toList();
        Map<UUID, CatalogSkuSummaryDto> skuMap = catalogApi.getSkuSummaries(variantIds);

        Map<UUID, PurchaseOrderItem> existingItemsByVariant = new HashMap<>();
        if (po.getItems() != null) {
            for (PurchaseOrderItem it : po.getItems()) {
                existingItemsByVariant.put(it.getVariantId(), it);
            }
        }

        Set<UUID> updatedVariantIds = new HashSet<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int sortOrder = 0;

        for (UpdatePurchaseOrderRequest.UpdatePurchaseOrderItemRequest itemReq : request.getItems()) {
            CatalogSkuSummaryDto skuDto = skuMap.get(itemReq.getVariantId());
            if (skuDto == null) {
                throw new AppException(PurchasingErrorCode.SKU_NOT_FOUND, itemReq.getVariantId());
            }
            if (!"ACTIVE".equalsIgnoreCase(skuDto.status())) {
                throw new AppException(PurchasingErrorCode.SKU_NOT_ACTIVE, skuDto.sku());
            }
            if (!calculator.isValidQuantity(itemReq.getQuantity(), skuDto.unit())) {
                throw new AppException(PurchasingErrorCode.INVALID_QUANTITY, skuDto.sku());
            }

            BigDecimal unitCost = itemReq.getUnitCost();
            BigDecimal lineTotal = null;
            if (unitCost != null) {
                if (unitCost.compareTo(BigDecimal.ZERO) < 0) {
                    throw new AppException(PurchasingErrorCode.INVALID_UNIT_COST, skuDto.sku());
                }
                lineTotal = calculator.calculateLineTotal(itemReq.getQuantity(), unitCost);
                totalAmount = totalAmount.add(lineTotal);
            }

            PurchaseOrderItem existingItem = existingItemsByVariant.get(itemReq.getVariantId());
            if (existingItem != null) {
                existingItem.setQuantityOrdered(itemReq.getQuantity());
                existingItem.setUnitCost(unitCost);
                existingItem.setLineTotal(lineTotal);
                existingItem.setSortOrder(sortOrder++);
                existingItem.setSku(skuDto.sku());
                existingItem.setProductName(skuDto.productName() != null ? skuDto.productName() : skuDto.variantName());
                existingItem.setUnit(skuDto.unit() != null ? skuDto.unit() : "cái");
            } else {
                PurchaseOrderItem newItem = PurchaseOrderItem.builder()
                        .variantId(skuDto.variantId())
                        .sku(skuDto.sku())
                        .productName(skuDto.productName() != null ? skuDto.productName() : skuDto.variantName())
                        .unit(skuDto.unit() != null ? skuDto.unit() : "cái")
                        .quantityOrdered(itemReq.getQuantity())
                        .quantityReceived(BigDecimal.ZERO)
                        .unitCost(unitCost)
                        .lineTotal(lineTotal)
                        .sortOrder(sortOrder++)
                        .build();
                po.addItem(newItem);
            }
            updatedVariantIds.add(itemReq.getVariantId());
        }

        if (po.getItems() != null) {
            po.getItems().removeIf(it -> !updatedVariantIds.contains(it.getVariantId()));
        }

        po.setTotalAmount(totalAmount);
        po.setExpectedDate(request.getExpectedDate());
        po.setNote(request.getNote());

        if (request.getDeliveryPlanType() != null && !request.getDeliveryPlanType().isBlank()) {
            try {
                po.setDeliveryPlanType(
                        PurchaseOrderDeliveryType.valueOf(request.getDeliveryPlanType().trim().toUpperCase()));
            } catch (Exception ignored) {
            }
        }

        // Staging update
        if (request.getStages() != null && !request.getStages().isEmpty()) {
            for (CreatePurchaseOrderStageRequest st : request.getStages()) {
                if (st.getExpectedDeliveryDate() == null) {
                    throw new AppException(PurchasingErrorCode.STAGE_MISSING_EXPECTED_DATE);
                }
            }

            List<CreatePurchaseOrderStageRequest> stageReqs = new ArrayList<>(request.getStages());
            if (po.getDeliveryPlanType() == PurchaseOrderDeliveryType.SINGLE && stageReqs.size() == 1) {
                stageReqs.get(0).setAmount(totalAmount);
                stageReqs.get(0).setPercent(new BigDecimal("100.00"));
            } else if (stageReqs.size() > 1 && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal sumPrevious = BigDecimal.ZERO;
                for (int i = 0; i < stageReqs.size() - 1; i++) {
                    sumPrevious = sumPrevious
                            .add(stageReqs.get(i).getAmount() != null ? stageReqs.get(i).getAmount() : BigDecimal.ZERO);
                }
                BigDecimal lastAmount = totalAmount.subtract(sumPrevious);
                if (lastAmount.compareTo(BigDecimal.ZERO) < 0) {
                    throw new AppException(PurchasingErrorCode.STAGE_SUM_NOT_MATCH_PO_TOTAL);
                }
                stageReqs.get(stageReqs.size() - 1).setAmount(lastAmount);
            }

            if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal stageSum = stageReqs.stream()
                        .map(s -> s.getAmount() != null ? s.getAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (stageSum.compareTo(totalAmount) != 0) {
                    throw new AppException(PurchasingErrorCode.STAGE_SUM_NOT_MATCH_PO_TOTAL);
                }
            }

            po.clearStages();
            int stageSort = 0;
            for (CreatePurchaseOrderStageRequest stReq : stageReqs) {
                PurchaseOrderStage st = PurchaseOrderStage.builder()
                        .stageId(stReq.getStageId() != null ? stReq.getStageId() : UUID.randomUUID())
                        .stageName(stReq.getStageName() != null ? stReq.getStageName() : "Đợt " + (stageSort + 1))
                        .description(stReq.getDescription())
                        .expectedDeliveryDate(stReq.getExpectedDeliveryDate())
                        .percent(stReq.getPercent() != null ? stReq.getPercent() : BigDecimal.ZERO)
                        .amount(stReq.getAmount() != null ? stReq.getAmount() : BigDecimal.ZERO)
                        .status(PurchaseOrderStageStatus.PENDING)
                        .sortOrder(stReq.getSortOrder() != null ? stReq.getSortOrder() : stageSort++)
                        .build();
                po.addStage(st);
            }
        } else if (po.getDeliveryPlanType() == PurchaseOrderDeliveryType.SINGLE && request.getExpectedDate() != null) {
            po.clearStages();
            po.addStage(PurchaseOrderStage.builder()
                    .stageId(UUID.randomUUID())
                    .stageName("Đợt 1 (Toàn bộ)")
                    .description("Giao một lần toàn bộ đơn hàng")
                    .expectedDeliveryDate(request.getExpectedDate())
                    .percent(new BigDecimal("100.00"))
                    .amount(totalAmount)
                    .status(PurchaseOrderStageStatus.PENDING)
                    .sortOrder(0)
                    .build());
        }

        PurchaseOrder updatedPo = purchaseOrderRepository.save(po);
        Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
        return mapToDetailResponse(updatedPo, supplier);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderListItemResponse> listPurchaseOrders(PurchaseOrderFilterRequest filter) {
        Specification<PurchaseOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String pattern = "%" + filter.getSearch().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("poCode")), pattern),
                        cb.like(cb.lower(root.get("note")), pattern)));
            }
            if (filter.getSupplierId() != null) {
                predicates.add(cb.equal(root.get("supplierId"), filter.getSupplierId()));
            }
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), filter.getFromDate()));
            }
            if (filter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("orderDate"), filter.getToDate()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        String sortBy = filter.getSortBy() != null && !filter.getSortBy().isBlank() ? filter.getSortBy() : "createdAt";
        Sort.Direction direction = "ASC".equalsIgnoreCase(filter.getSortDirection()) ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by(direction, sortBy));

        Page<PurchaseOrder> page = purchaseOrderRepository.findAll(spec, pageable);

        Set<UUID> supplierIds = page.getContent().stream()
                .map(PurchaseOrder::getSupplierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Supplier> supplierMap = supplierRepository.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getSupplierId, Function.identity(), (a, b) -> a));

        Set<UUID> userIds = page.getContent().stream()
                .map(PurchaseOrder::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> userNameMap = new HashMap<>();
        for (UUID uid : userIds) {
            userNameMap.put(uid, getUserNameSafely(uid));
        }

        return PageResponse.from(page, po -> {
            Supplier supp = supplierMap.get(po.getSupplierId());
            int totalStg = po.getStages() != null ? po.getStages().size() : 0;
            int settledStg = po.getStages() != null
                    ? (int) po.getStages().stream().filter(s -> s.getStatus() == PurchaseOrderStageStatus.SETTLED)
                            .count()
                    : 0;
            return PurchaseOrderListItemResponse.builder()
                    .purchaseOrderId(po.getPurchaseOrderId())
                    .poCode(po.getPoCode())
                    .supplierId(po.getSupplierId())
                    .supplierName(supp != null ? supp.getSupplierName() : null)
                    .supplierCode(supp != null ? supp.getSupplierCode() : null)
                    .warehouseCode(po.getWarehouseCode())
                    .status(po.getStatus())
                    .orderDate(po.getOrderDate())
                    .expectedDate(po.getExpectedDate())
                    .totalAmount(po.getTotalAmount())
                    .paidAmount(po.getPaidAmount() != null ? po.getPaidAmount() : BigDecimal.ZERO)
                    .deliveryPlanType(po.getDeliveryPlanType() != null ? po.getDeliveryPlanType().name()
                            : PurchaseOrderDeliveryType.SINGLE.name())
                    .isShortClosed(Boolean.TRUE.equals(po.getIsShortClosed()))
                    .totalStages(totalStg)
                    .settledStages(settledStg)
                    .totalItems(po.getItems() != null ? po.getItems().size() : 0)
                    .createdByName(userNameMap.get(po.getCreatedBy()))
                    .createdAt(po.getCreatedAt())
                    .build();
        });
    }

    @Override
    @Transactional
    public PurchaseOrderResponse confirmPurchaseOrder(UUID purchaseOrderId) {
        log.info("Confirming PO: {}", purchaseOrderId);

        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.PO_NOT_FOUND));

        if (po.getStatus() != PurchaseOrderStatus.QUOTED && po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new AppException(PurchasingErrorCode.PO_CANNOT_CONFIRM_STATUS);
        }

        Supplier supplier = supplierRepository.findById(po.getSupplierId())
                .orElseThrow(() -> new AppException(PurchasingErrorCode.SUPPLIER_NOT_FOUND, po.getSupplierId()));

        if (supplier.getStatus() != SupplierStatus.ACTIVE) {
            throw new AppException(PurchasingErrorCode.SUPPLIER_NOT_ACTIVE, supplier.getSupplierName());
        }

        if (po.getItems() == null || po.getItems().isEmpty()) {
            throw new AppException(PurchasingErrorCode.PO_ITEMS_EMPTY);
        }

        if (po.getExpectedDate() == null) {
            throw new AppException(PurchasingErrorCode.PO_CONFIRM_MISSING_EXPECTED_DATE);
        }

        List<UUID> variantIds = po.getItems().stream().map(PurchaseOrderItem::getVariantId).toList();
        Map<UUID, CatalogSkuSummaryDto> skuMap = catalogApi.getSkuSummaries(variantIds);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurchaseOrderItem item : po.getItems()) {
            if (item.getUnitCost() == null || item.getUnitCost().compareTo(BigDecimal.ZERO) <= 0) {
                throw new AppException(PurchasingErrorCode.PO_CONFIRM_MISSING_PRICE, item.getSku());
            }
            CatalogSkuSummaryDto skuDto = skuMap.get(item.getVariantId());
            if (skuDto == null) {
                throw new AppException(PurchasingErrorCode.SKU_NOT_FOUND, item.getVariantId());
            }
            if (!"ACTIVE".equalsIgnoreCase(skuDto.status())) {
                throw new AppException(PurchasingErrorCode.SKU_NOT_ACTIVE, item.getSku());
            }

            BigDecimal lineTotal = calculator.calculateLineTotal(item.getQuantityOrdered(), item.getUnitCost());
            item.setLineTotal(lineTotal);
            totalAmount = totalAmount.add(lineTotal);
        }
        po.setTotalAmount(totalAmount);

        // Ensure stages exist
        if (po.getStages() == null || po.getStages().isEmpty()) {
            if (po.getDeliveryPlanType() == PurchaseOrderDeliveryType.SINGLE) {
                po.addStage(PurchaseOrderStage.builder()
                        .stageId(UUID.randomUUID())
                        .stageName("Đợt 1 (Toàn bộ)")
                        .description("Giao một lần toàn bộ đơn hàng")
                        .expectedDeliveryDate(po.getExpectedDate())
                        .percent(new BigDecimal("100.00"))
                        .amount(totalAmount)
                        .status(PurchaseOrderStageStatus.PENDING)
                        .sortOrder(0)
                        .build());
            } else {
                throw new AppException(PurchasingErrorCode.STAGE_MISSING_EXPECTED_DATE);
            }
        }

        // Validate stages
        for (PurchaseOrderStage st : po.getStages()) {
            if (st.getExpectedDeliveryDate() == null) {
                throw new AppException(PurchasingErrorCode.STAGE_MISSING_EXPECTED_DATE);
            }
        }

        BigDecimal stageSum = po.getStages().stream()
                .map(PurchaseOrderStage::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (stageSum.compareTo(totalAmount) != 0) {
            throw new AppException(PurchasingErrorCode.STAGE_SUM_NOT_MATCH_PO_TOTAL);
        }

        po.setStatus(PurchaseOrderStatus.CONFIRMED);
        po.setConfirmedBy(iamApi.getCurrentUserId());
        po.setConfirmedAt(OffsetDateTime.now());

        PurchaseOrder confirmedPo = purchaseOrderRepository.save(po);
        log.info("PO confirmed successfully: {}", confirmedPo.getPoCode());
        return mapToDetailResponse(confirmedPo, supplier);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse reopenToQuoted(UUID purchaseOrderId, ReopenPoRequest request) {
        log.info("Reopening PO: {}", purchaseOrderId);

        if (request == null || request.getReopenReason() == null || request.getReopenReason().isBlank()) {
            throw new AppException(PurchasingErrorCode.REOPEN_REASON_REQUIRED);
        }

        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.PO_NOT_FOUND));

        if (po.getStatus() != PurchaseOrderStatus.CONFIRMED) {
            throw new AppException(PurchasingErrorCode.PO_CANNOT_EDIT);
        }

        boolean hasSettledStage = po.getStages() != null && po.getStages().stream()
                .anyMatch(st -> st.getStatus() == PurchaseOrderStageStatus.SETTLED);
        if (hasSettledStage) {
            throw new AppException(PurchasingErrorCode.PO_CANNOT_REOPEN_WITH_SETTLED_STAGES);
        }

        po.setStatus(PurchaseOrderStatus.QUOTED);
        po.setReopenedBy(iamApi.getCurrentUserId());
        po.setReopenedAt(OffsetDateTime.now());
        po.setReopenReason(request.getReopenReason().trim());

        PurchaseOrder savedPo = purchaseOrderRepository.save(po);
        Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
        return mapToDetailResponse(savedPo, supplier);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse settleStage(UUID purchaseOrderId, UUID stageId, SettleStageRequest request) {
        log.info("Settling stage {} of PO: {}", stageId, purchaseOrderId);

        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.PO_NOT_FOUND));

        if (po.getStatus() != PurchaseOrderStatus.CONFIRMED) {
            throw new AppException(PurchasingErrorCode.PO_CANNOT_CONFIRM_STATUS);
        }

        PurchaseOrderStage stage = stageRepository
                .findByStageIdAndPurchaseOrder_PurchaseOrderId(stageId, purchaseOrderId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.STAGE_NOT_FOUND));

        if (stage.getStatus() == PurchaseOrderStageStatus.SETTLED
                || stage.getStatus() == PurchaseOrderStageStatus.CANCELLED) {
            throw new AppException(PurchasingErrorCode.STAGE_ALREADY_SETTLED);
        }

        BigDecimal actualAmount = request.getActualAmount() != null ? request.getActualAmount() : stage.getAmount();
        if (actualAmount.compareTo(stage.getAmount()) != 0) {
            if (request.getNote() == null || request.getNote().isBlank()) {
                throw new AppException(PurchasingErrorCode.STAGE_SETTLEMENT_REASON_REQUIRED);
            }
        }

        stage.setStatus(PurchaseOrderStageStatus.SETTLED);
        stage.setActualAmount(actualAmount);
        stage.setPaidAt(request.getPaidAt() != null ? request.getPaidAt() : OffsetDateTime.now());
        stage.setPaidBy(iamApi.getCurrentUserId());
        stage.setPaymentMethod(request.getPaymentMethod());
        stage.setPaymentReference(request.getPaymentReference());
        stage.setSettlementNote(request.getNote());
        stageRepository.save(stage);

        BigDecimal currentPaid = po.getPaidAmount() != null ? po.getPaidAmount() : BigDecimal.ZERO;
        po.setPaidAmount(currentPaid.add(actualAmount));

        // If all active stages are settled, auto COMPLETED
        boolean allSettled = po.getStages().stream()
                .filter(s -> s.getStatus() != PurchaseOrderStageStatus.CANCELLED)
                .allMatch(s -> s.getStatus() == PurchaseOrderStageStatus.SETTLED);
        if (allSettled) {
            po.setStatus(PurchaseOrderStatus.COMPLETED);
        }

        PurchaseOrder savedPo = purchaseOrderRepository.save(po);
        Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
        return mapToDetailResponse(savedPo, supplier);
    }

    @Override
    @Transactional
    public StageRollbackRequestResponse requestStageRollback(UUID purchaseOrderId, UUID stageId,
            StageRollbackRequest request) {
        log.info("Requesting rollback for stage {} of PO: {}", stageId, purchaseOrderId);

        if (request == null || request.getRequestReason() == null || request.getRequestReason().isBlank()) {
            throw new AppException(PurchasingErrorCode.ROLLBACK_REASON_REQUIRED);
        }

        PurchaseOrderStage stage = stageRepository
                .findByStageIdAndPurchaseOrder_PurchaseOrderId(stageId, purchaseOrderId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.STAGE_NOT_FOUND));

        if (stage.getStatus() != PurchaseOrderStageStatus.SETTLED) {
            throw new AppException(PurchasingErrorCode.STAGE_NOT_SETTLED);
        }

        PurchaseOrder po = stage.getPurchaseOrder();
        if (Boolean.TRUE.equals(po.getIsShortClosed())) {
            throw new AppException(PurchasingErrorCode.PO_CANNOT_EDIT);
        }

        boolean exists = rollbackRequestRepository.existsByStage_StageIdAndStatus(stageId,
                RollbackRequestStatus.PENDING);
        if (exists) {
            throw new AppException(PurchasingErrorCode.ROLLBACK_REQUEST_ALREADY_PENDING);
        }

        PurchaseOrderStageRollbackRequest rr = PurchaseOrderStageRollbackRequest.builder()
                .rollbackId(UUID.randomUUID())
                .stage(stage)
                .purchaseOrderId(purchaseOrderId)
                .requestedBy(iamApi.getCurrentUserId())
                .requestedAt(OffsetDateTime.now())
                .requestReason(request.getRequestReason().trim())
                .status(RollbackRequestStatus.PENDING)
                .build();
        PurchaseOrderStageRollbackRequest saved = rollbackRequestRepository.save(rr);

        return StageRollbackRequestResponse.builder()
                .rollbackId(saved.getRollbackId())
                .stageId(stageId)
                .purchaseOrderId(purchaseOrderId)
                .requestedBy(saved.getRequestedBy())
                .requestedByName(getUserNameSafely(saved.getRequestedBy()))
                .requestedAt(saved.getRequestedAt())
                .requestReason(saved.getRequestReason())
                .status(saved.getStatus())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public PurchaseOrderResponse reviewStageRollback(UUID purchaseOrderId, UUID rollbackId,
            ReviewRollbackRequest request) {
        log.info("Reviewing rollback {} for PO: {}", rollbackId, purchaseOrderId);

        PurchaseOrderStageRollbackRequest rr = rollbackRequestRepository.findById(rollbackId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.ROLLBACK_REQUEST_NOT_FOUND));

        if (rr.getStatus() != RollbackRequestStatus.PENDING) {
            throw new AppException(PurchasingErrorCode.ROLLBACK_REQUEST_NOT_PENDING);
        }

        PurchaseOrderStage stage = rr.getStage();
        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.PO_NOT_FOUND));

        if (Boolean.TRUE.equals(po.getIsShortClosed())) {
            throw new AppException(PurchasingErrorCode.PO_CANNOT_EDIT);
        }

        rr.setReviewedBy(iamApi.getCurrentUserId());
        rr.setReviewedAt(OffsetDateTime.now());
        rr.setReviewNote(request.getReviewNote());

        if (Boolean.TRUE.equals(request.getApproved())) {
            rr.setStatus(RollbackRequestStatus.APPROVED);

            BigDecimal actualAmount = stage.getActualAmount() != null ? stage.getActualAmount() : BigDecimal.ZERO;
            stage.setStatus(PurchaseOrderStageStatus.PENDING);
            stage.setActualAmount(null);
            stage.setPaidAt(null);
            stage.setPaidBy(null);
            stage.setPaymentMethod(null);
            stage.setPaymentReference(null);
            stage.setSettlementNote(null);
            stageRepository.save(stage);

            BigDecimal currentPaid = po.getPaidAmount() != null ? po.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal newPaid = currentPaid.subtract(actualAmount);
            if (newPaid.compareTo(BigDecimal.ZERO) < 0)
                newPaid = BigDecimal.ZERO;
            po.setPaidAmount(newPaid);

            if (po.getStatus() == PurchaseOrderStatus.COMPLETED && !Boolean.TRUE.equals(po.getIsShortClosed())) {
                po.setStatus(PurchaseOrderStatus.CONFIRMED);
            }
            purchaseOrderRepository.save(po);
        } else {
            rr.setStatus(RollbackRequestStatus.REJECTED);
        }

        rollbackRequestRepository.save(rr);
        Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
        return mapToDetailResponse(po, supplier);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse directRollbackStage(UUID purchaseOrderId, UUID stageId, StageRollbackRequest request) {
        log.info("Direct rollback stage {} for PO: {}", stageId, purchaseOrderId);

        if (request == null || request.getRequestReason() == null || request.getRequestReason().isBlank()) {
            throw new AppException(PurchasingErrorCode.ROLLBACK_REASON_REQUIRED);
        }

        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.PO_NOT_FOUND));

        if (Boolean.TRUE.equals(po.getIsShortClosed())) {
            throw new AppException(PurchasingErrorCode.PO_CANNOT_EDIT);
        }

        PurchaseOrderStage stage = stageRepository
                .findByStageIdAndPurchaseOrder_PurchaseOrderId(stageId, purchaseOrderId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.STAGE_NOT_FOUND));

        if (stage.getStatus() != PurchaseOrderStageStatus.SETTLED) {
            throw new AppException(PurchasingErrorCode.STAGE_NOT_SETTLED);
        }

        PurchaseOrderStageRollbackRequest rr = PurchaseOrderStageRollbackRequest.builder()
                .rollbackId(UUID.randomUUID())
                .stage(stage)
                .purchaseOrderId(purchaseOrderId)
                .requestedBy(iamApi.getCurrentUserId())
                .requestedAt(OffsetDateTime.now())
                .requestReason(request.getRequestReason().trim())
                .status(RollbackRequestStatus.APPROVED)
                .reviewedBy(iamApi.getCurrentUserId())
                .reviewedAt(OffsetDateTime.now())
                .reviewNote("Chủ cửa hàng hoàn tác trực tiếp")
                .build();
        rollbackRequestRepository.save(rr);

        BigDecimal actualAmount = stage.getActualAmount() != null ? stage.getActualAmount() : BigDecimal.ZERO;
        stage.setStatus(PurchaseOrderStageStatus.PENDING);
        stage.setActualAmount(null);
        stage.setPaidAt(null);
        stage.setPaidBy(null);
        stage.setPaymentMethod(null);
        stage.setPaymentReference(null);
        stage.setSettlementNote(null);
        stageRepository.save(stage);

        BigDecimal currentPaid = po.getPaidAmount() != null ? po.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaid = currentPaid.subtract(actualAmount);
        if (newPaid.compareTo(BigDecimal.ZERO) < 0)
            newPaid = BigDecimal.ZERO;
        po.setPaidAmount(newPaid);

        if (po.getStatus() == PurchaseOrderStatus.COMPLETED && !Boolean.TRUE.equals(po.getIsShortClosed())) {
            po.setStatus(PurchaseOrderStatus.CONFIRMED);
        }

        PurchaseOrder savedPo = purchaseOrderRepository.save(po);
        Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
        return mapToDetailResponse(savedPo, supplier);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse closeShortPurchaseOrder(UUID purchaseOrderId, CloseShortPoRequest request) {
        log.info("Closing short PO: {}", purchaseOrderId);

        if (request == null || request.getCloseReason() == null || request.getCloseReason().isBlank()) {
            throw new AppException(PurchasingErrorCode.CLOSE_REASON_REQUIRED);
        }

        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.PO_NOT_FOUND));

        if (po.getStatus() != PurchaseOrderStatus.CONFIRMED) {
            throw new AppException(PurchasingErrorCode.PO_CANNOT_CLOSE_STATUS);
        }

        boolean hasSettledStage = po.getStages() != null && po.getStages().stream()
                .anyMatch(st -> st.getStatus() == PurchaseOrderStageStatus.SETTLED);
        if (!hasSettledStage) {
            throw new AppException(PurchasingErrorCode.PO_CLOSE_SHORT_REQUIRES_SETTLED_STAGE);
        }

        // Cancel remaining pending stages
        if (po.getStages() != null) {
            for (PurchaseOrderStage st : po.getStages()) {
                if (st.getStatus() == PurchaseOrderStageStatus.PENDING) {
                    st.setStatus(PurchaseOrderStageStatus.CANCELLED);
                    stageRepository.save(st);
                }
            }
        }

        po.setStatus(PurchaseOrderStatus.COMPLETED);
        po.setIsShortClosed(true);
        po.setClosedBy(iamApi.getCurrentUserId());
        po.setClosedAt(OffsetDateTime.now());
        po.setCloseReason(request.getCloseReason().trim());

        PurchaseOrder savedPo = purchaseOrderRepository.save(po);
        Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
        return mapToDetailResponse(savedPo, supplier);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse cancelPurchaseOrder(UUID purchaseOrderId, CancelPurchaseOrderRequest request) {
        log.info("Cancelling PO: {}", purchaseOrderId);

        if (request.getCancelReason() == null || request.getCancelReason().isBlank()) {
            throw new AppException(PurchasingErrorCode.CANCEL_REASON_REQUIRED);
        }

        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.PO_NOT_FOUND));

        if (po.getStatus() != PurchaseOrderStatus.DRAFT && po.getStatus() != PurchaseOrderStatus.QUOTED
                && po.getStatus() != PurchaseOrderStatus.CONFIRMED) {
            throw new AppException(PurchasingErrorCode.PO_CANNOT_CANCEL_STATUS);
        }

        boolean hasSettledStage = po.getStages() != null && po.getStages().stream()
                .anyMatch(st -> st.getStatus() == PurchaseOrderStageStatus.SETTLED);
        if (hasSettledStage) {
            throw new AppException(PurchasingErrorCode.PO_CANNOT_CANCEL_WITH_SETTLED_STAGES);
        }

        if (po.getStatus() == PurchaseOrderStatus.CONFIRMED) {
            if (inventoryApi.hasConfirmedGoodsReceipt(purchaseOrderId)
                    || inventoryApi.hasDraftGoodsReceipt(purchaseOrderId)) {
                throw new AppException(PurchasingErrorCode.PO_CANNOT_CANCEL_RECEIPT_EXISTS);
            }
        }

        po.setStatus(PurchaseOrderStatus.CANCELLED);
        po.setCancelledBy(iamApi.getCurrentUserId());
        po.setCancelledAt(OffsetDateTime.now());
        po.setCancelReason(request.getCancelReason().trim());

        PurchaseOrder cancelledPo = purchaseOrderRepository.save(po);
        Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
        return mapToDetailResponse(cancelledPo, supplier);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse closePurchaseOrder(UUID purchaseOrderId, ClosePurchaseOrderRequest request) {
        log.info("Closing PO: {}", purchaseOrderId);

        if (request.getCloseReason() == null || request.getCloseReason().isBlank()) {
            throw new AppException(PurchasingErrorCode.CLOSE_REASON_REQUIRED);
        }

        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.PO_NOT_FOUND));

        if (po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new AppException(PurchasingErrorCode.PO_CANNOT_CLOSE_STATUS);
        }

        if (inventoryApi.hasDraftGoodsReceipt(purchaseOrderId)) {
            throw new AppException(PurchasingErrorCode.PO_CANNOT_CLOSE_DRAFT_RECEIPT);
        }

        po.setStatus(PurchaseOrderStatus.COMPLETED);
        po.setClosedBy(iamApi.getCurrentUserId());
        po.setClosedAt(OffsetDateTime.now());
        po.setCloseReason(request.getCloseReason().trim());

        PurchaseOrder closedPo = purchaseOrderRepository.save(po);
        Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
        return mapToDetailResponse(closedPo, supplier);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportPdf(UUID purchaseOrderId) {
        PurchaseOrderResponse po = getDetail(purchaseOrderId);
        return pdfService.generatePdf(po);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LowStockSuggestionResponse> getLowStockSuggestions(UUID supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.SUPPLIER_NOT_FOUND, supplierId));

        List<SupplierProduct> supplierProducts = supplierProductRepository.findByIdSupplierId(supplierId);
        if (supplierProducts.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, BigDecimal> lastSupplyPricesByProductId = supplierProducts.stream()
                .filter(sp -> sp.getLastSupplyPrice() != null)
                .collect(Collectors.toMap(sp -> sp.getId().getProductId(), SupplierProduct::getLastSupplyPrice,
                        (a, b) -> a));

        List<UUID> productIds = supplierProducts.stream()
                .map(sp -> sp.getId().getProductId())
                .toList();

        List<CatalogSkuSummaryDto> skus = catalogApi.getSkusByProductIds(productIds);
        if (skus.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> variantIds = skus.stream().map(CatalogSkuSummaryDto::variantId).toList();
        Map<UUID, StockSummaryDto> stockMap = inventoryApi.getStockSummaries(variantIds);
        Map<UUID, BigDecimal> incomingMap = purchasingApi.getIncomingStockQtyMap(variantIds);

        List<LowStockSuggestionResponse> results = new ArrayList<>();
        for (CatalogSkuSummaryDto sku : skus) {
            StockSummaryDto stock = stockMap.get(sku.variantId());
            if (stock == null)
                continue;

            BigDecimal availableQty = stock.availableQty() != null ? stock.availableQty() : BigDecimal.ZERO;
            BigDecimal minimumQty = stock.minimumQty() != null ? stock.minimumQty() : BigDecimal.ZERO;
            BigDecimal maximumQty = stock.maximumQty();

            if (minimumQty.compareTo(BigDecimal.ZERO) > 0 && availableQty.compareTo(minimumQty) <= 0) {
                BigDecimal incoming = incomingMap.getOrDefault(sku.variantId(), BigDecimal.ZERO);
                BigDecimal proposedQty = null;

                if (maximumQty != null && maximumQty.compareTo(BigDecimal.ZERO) > 0) {
                    proposedQty = maximumQty.subtract(availableQty).subtract(incoming);
                    if (proposedQty.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                }

                results.add(LowStockSuggestionResponse.builder()
                        .variantId(sku.variantId())
                        .sku(sku.sku())
                        .productName(sku.productName() != null ? sku.productName() : sku.variantName())
                        .unit(sku.unit())
                        .onHandQty(stock.onHandQty())
                        .availableQty(availableQty)
                        .minimumQty(minimumQty)
                        .maximumQty(maximumQty)
                        .incomingQty(incoming)
                        .suggestedQty(proposedQty)
                        .lastSupplyPrice(lastSupplyPricesByProductId.get(sku.productId()))
                        .build());
            }
        }

        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierItemOptionResponse> getSupplierItems(UUID supplierId, boolean all) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.SUPPLIER_NOT_FOUND, supplierId));

        List<SupplierProduct> supplierProducts = supplierProductRepository.findByIdSupplierId(supplierId);
        Map<UUID, BigDecimal> lastPriceMap = supplierProducts.stream()
                .filter(sp -> sp.getLastSupplyPrice() != null)
                .collect(Collectors.toMap(sp -> sp.getId().getProductId(), SupplierProduct::getLastSupplyPrice,
                        (a, b) -> a));

        Set<UUID> suppliedProductIds = supplierProducts.stream()
                .map(sp -> sp.getId().getProductId())
                .collect(Collectors.toSet());

        if (!all) {
            if (suppliedProductIds.isEmpty()) {
                return Collections.emptyList();
            }
            List<CatalogSkuSummaryDto> skus = catalogApi.getSkusByProductIds(suppliedProductIds);
            return skus.stream()
                    .map(sku -> SupplierItemOptionResponse.builder()
                            .variantId(sku.variantId())
                            .sku(sku.sku())
                            .productName(sku.productName() != null ? sku.productName() : sku.variantName())
                            .unit(sku.unit())
                            .lastSupplyPrice(lastPriceMap.get(sku.productId()))
                            .suppliedBySupplier(true)
                            .build())
                    .toList();
        } else {
            List<CatalogSkuSummaryDto> allSkus = catalogApi.getAllActiveSkus();
            return allSkus.stream()
                    .map(sku -> SupplierItemOptionResponse.builder()
                            .variantId(sku.variantId())
                            .sku(sku.sku())
                            .productName(sku.productName() != null ? sku.productName() : sku.variantName())
                            .unit(sku.unit())
                            .lastSupplyPrice(lastPriceMap.get(sku.productId()))
                            .suppliedBySupplier(suppliedProductIds.contains(sku.productId()))
                            .build())
                    .toList();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierOptionResponse> getActiveSuppliers() {
        return supplierRepository.findAll().stream()
                .filter(s -> s.getStatus() == SupplierStatus.ACTIVE)
                .map(s -> SupplierOptionResponse.builder()
                        .supplierId(s.getSupplierId())
                        .supplierCode(s.getSupplierCode())
                        .supplierName(s.getSupplierName())
                        .contactName(s.getContactName())
                        .phone(s.getPhone())
                        .email(s.getEmail())
                        .address(s.getAddress())
                        .build())
                .toList();
    }

    private PurchaseOrderResponse mapToDetailResponse(PurchaseOrder po, Supplier supplier) {
        List<UUID> variantIds = po.getItems() != null
                ? po.getItems().stream().map(PurchaseOrderItem::getVariantId).toList()
                : Collections.emptyList();
        Map<UUID, CatalogSkuSummaryDto> skuMap = variantIds.isEmpty() ? Collections.emptyMap()
                : catalogApi.getSkuSummaries(variantIds);

        List<SupplierProduct> supplierProducts = supplierProductRepository.findByIdSupplierId(po.getSupplierId());
        Map<UUID, BigDecimal> lastPriceMap = supplierProducts.stream()
                .filter(sp -> sp.getLastSupplyPrice() != null)
                .collect(Collectors.toMap(sp -> sp.getId().getProductId(), SupplierProduct::getLastSupplyPrice,
                        (a, b) -> a));

        List<PurchaseOrderItemResponse> itemResponses = new ArrayList<>();
        if (po.getItems() != null) {
            for (PurchaseOrderItem item : po.getItems()) {
                CatalogSkuSummaryDto skuDto = skuMap.get(item.getVariantId());
                UUID productId = skuDto != null ? skuDto.productId() : null;

                itemResponses.add(PurchaseOrderItemResponse.builder()
                        .poItemId(item.getPurchaseOrderItemId())
                        .variantId(item.getVariantId())
                        .sku(item.getSku())
                        .productName(item.getProductName())
                        .unit(item.getUnit())
                        .quantity(item.getQuantityOrdered())
                        .receivedQty(item.getQuantityReceived())
                        .remainingQty(item.getRemainingQty())
                        .unitCost(item.getUnitCost())
                        .lineTotal(item.getLineTotal())
                        .sortOrder(item.getSortOrder())
                        .lastSupplyPrice(productId != null ? lastPriceMap.get(productId) : null)
                        .build());
            }
        }

        List<PurchaseOrderReceiptItemResponse> receiptResponses = new ArrayList<>();
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            List<GoodsReceiptSummaryDto> receipts = inventoryApi.getReceiptsByPurchaseOrderId(po.getPurchaseOrderId());
            for (GoodsReceiptSummaryDto gr : receipts) {
                receiptResponses.add(PurchaseOrderReceiptItemResponse.builder()
                        .goodsReceiptId(gr.goodsReceiptId())
                        .receiptCode(gr.receiptCode())
                        .status(gr.status())
                        .receiptDate(gr.receiptDate())
                        .supplierInvoiceNo(gr.supplierInvoiceNo())
                        .totalAmount(gr.totalAmount())
                        .confirmedAt(gr.confirmedAt())
                        .build());
            }
        }

        List<PurchaseOrderStageResponse> stageResponses = new ArrayList<>();
        int settledStagesCount = 0;
        if (po.getStages() != null) {
            for (PurchaseOrderStage st : po.getStages()) {
                if (st.getStatus() == PurchaseOrderStageStatus.SETTLED) {
                    settledStagesCount++;
                }

                boolean hasPendingRollback = rollbackRequestRepository.existsByStage_StageIdAndStatus(
                        st.getStageId(), RollbackRequestStatus.PENDING);

                Optional<PurchaseOrderStageRollbackRequest> latestRollback = rollbackRequestRepository
                        .findFirstByStage_StageIdAndStatus(st.getStageId(), RollbackRequestStatus.PENDING);

                StageRollbackRequestResponse latestRollbackDto = null;
                if (latestRollback.isPresent()) {
                    PurchaseOrderStageRollbackRequest rr = latestRollback.get();
                    latestRollbackDto = StageRollbackRequestResponse.builder()
                            .rollbackId(rr.getRollbackId())
                            .stageId(st.getStageId())
                            .purchaseOrderId(po.getPurchaseOrderId())
                            .requestedBy(rr.getRequestedBy())
                            .requestedByName(getUserNameSafely(rr.getRequestedBy()))
                            .requestedAt(rr.getRequestedAt())
                            .requestReason(rr.getRequestReason())
                            .status(rr.getStatus())
                            .reviewedBy(rr.getReviewedBy())
                            .reviewedByName(getUserNameSafely(rr.getReviewedBy()))
                            .reviewedAt(rr.getReviewedAt())
                            .reviewNote(rr.getReviewNote())
                            .createdAt(rr.getCreatedAt())
                            .build();
                }

                stageResponses.add(PurchaseOrderStageResponse.builder()
                        .stageId(st.getStageId())
                        .purchaseOrderId(po.getPurchaseOrderId())
                        .stageName(st.getStageName())
                        .description(st.getDescription())
                        .expectedDeliveryDate(st.getExpectedDeliveryDate())
                        .percent(st.getPercent())
                        .amount(st.getAmount())
                        .status(st.getStatus())
                        .actualAmount(st.getActualAmount())
                        .paidAt(st.getPaidAt())
                        .paidBy(st.getPaidBy())
                        .paidByName(getUserNameSafely(st.getPaidBy()))
                        .paymentMethod(st.getPaymentMethod())
                        .paymentReference(st.getPaymentReference())
                        .settlementNote(st.getSettlementNote())
                        .sortOrder(st.getSortOrder())
                        .version(st.getVersion())
                        .hasPendingRollback(hasPendingRollback)
                        .latestRollbackRequest(latestRollbackDto)
                        .build());
            }
        }

        return PurchaseOrderResponse.builder()
                .purchaseOrderId(po.getPurchaseOrderId())
                .poCode(po.getPoCode())
                .supplierId(po.getSupplierId())
                .supplierName(supplier != null ? supplier.getSupplierName() : null)
                .supplierCode(supplier != null ? supplier.getSupplierCode() : null)
                .supplierPhone(supplier != null ? supplier.getPhone() : null)
                .warehouseId(po.getWarehouseId())
                .warehouseCode(po.getWarehouseCode())
                .status(po.getStatus())
                .orderDate(po.getOrderDate())
                .expectedDate(po.getExpectedDate())
                .totalAmount(po.getTotalAmount())
                .paidAmount(po.getPaidAmount() != null ? po.getPaidAmount() : BigDecimal.ZERO)
                .deliveryPlanType(po.getDeliveryPlanType() != null ? po.getDeliveryPlanType().name()
                        : PurchaseOrderDeliveryType.SINGLE.name())
                .isShortClosed(Boolean.TRUE.equals(po.getIsShortClosed()))
                .reopenedBy(po.getReopenedBy())
                .reopenedByName(getUserNameSafely(po.getReopenedBy()))
                .reopenedAt(po.getReopenedAt())
                .reopenReason(po.getReopenReason())
                .totalStages(po.getStages() != null ? po.getStages().size() : 0)
                .settledStages(settledStagesCount)
                .note(po.getNote())
                .createdBy(po.getCreatedBy())
                .createdByName(getUserNameSafely(po.getCreatedBy()))
                .confirmedBy(po.getConfirmedBy())
                .confirmedByName(getUserNameSafely(po.getConfirmedBy()))
                .confirmedAt(po.getConfirmedAt())
                .cancelledBy(po.getCancelledBy())
                .cancelledByName(getUserNameSafely(po.getCancelledBy()))
                .cancelledAt(po.getCancelledAt())
                .cancelReason(po.getCancelReason())
                .closedBy(po.getClosedBy())
                .closedByName(getUserNameSafely(po.getClosedBy()))
                .closedAt(po.getClosedAt())
                .closeReason(po.getCloseReason())
                .version(po.getVersion())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .items(itemResponses)
                .receipts(receiptResponses)
                .stages(stageResponses)
                .build();
    }

    private String getUserNameSafely(UUID userId) {
        if (userId == null)
            return null;
        try {
            UserSummaryDto user = iamApi.getUserById(userId);
            return user != null ? user.fullName() : null;
        } catch (Exception ex) {
            log.debug("User {} not found in IAM", userId);
            return null;
        }
    }
}
