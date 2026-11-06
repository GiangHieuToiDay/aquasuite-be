package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerAddressDto;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.module.sales.internal.calculator.DiscountPolicyChecker;
import com.aquasuite.ims.module.sales.internal.calculator.DiscountPolicyChecker.DiscountCheckResult;
import com.aquasuite.ims.module.sales.internal.calculator.QuotationCalculator;
import com.aquasuite.ims.module.sales.internal.calculator.QuotationCalculator.CalculationLine;
import com.aquasuite.ims.module.sales.internal.calculator.QuotationCalculator.QuotationTotals;
import com.aquasuite.ims.module.sales.internal.entity.Quotation;
import com.aquasuite.ims.module.sales.internal.entity.QuotationItem;
import com.aquasuite.ims.module.sales.internal.entity.QuotationPaymentMilestone;
import com.aquasuite.ims.module.sales.internal.enums.QuotationStatus;
import com.aquasuite.ims.module.sales.internal.exception.SalesErrorCode;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.sales.internal.mapper.QuotationMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.ApproveQuotationRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.MarkAcceptedRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.MarkLostRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.QuotationFilterRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.QuotationItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.QuotationPaymentMilestoneRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.QuotationUpsertRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.RejectQuotationRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.SubmitApprovalRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationListItemResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationResponse;
import com.aquasuite.ims.module.sales.internal.repository.QuotationItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.QuotationPaymentMilestoneRepository;
import com.aquasuite.ims.module.sales.internal.repository.QuotationRepository;
import com.aquasuite.ims.module.sales.internal.service.QuotationService;
import com.aquasuite.ims.share.dto.PageResponse;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import com.aquasuite.ims.share.util.PageableUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.aquasuite.ims.module.sales.internal.calculator.QuotationConstants.MONEY_SCALE;
import static com.aquasuite.ims.module.sales.internal.calculator.QuotationConstants.PERCENT_SCALE;
import static com.aquasuite.ims.module.sales.internal.calculator.QuotationConstants.ROUNDING_MODE;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationServiceImpl implements QuotationService {

    private static final BigDecimal DEFAULT_STAFF_DISCOUNT_LIMIT = BigDecimal.valueOf(10);
    private static final int DEFAULT_BACKORDER_LEAD_TIME_DAYS = 7;
    private static final Set<String> DISCRETE_UNITS = Set.of(
            "cái", "cai", "bộ", "bo", "chiếc", "chiec", "thùng", "thung",
            "hộp", "hop", "cuộn", "cuon", "gói", "goi", "bình", "binh"
    );
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "quotationCode", "quotationDate", "validUntil", "totalAmount", "status", "revisionNo"
    );
    private static final String DEFAULT_SORT_FIELD = "createdAt";

    private final CatalogApi catalogApi;
    private final CrmApi crmApi;
    private final InventoryApi inventoryApi;
    private final IamApi iamApi;
    private final QuotationCalculator quotationCalculator;
    private final DiscountPolicyChecker discountPolicyChecker;
    private final QuotationMapper quotationMapper;
    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository quotationItemRepository;
    private final QuotationPaymentMilestoneRepository quotationPaymentMilestoneRepository;

    // =========================================================================
    // NHÓM 2: SOẠN THẢO, TÍNH NHẨM & LƯU NHÁP (DRAFTING & CALCULATION)
    // =========================================================================

    @Override
    @Transactional
    public QuotationResponse createQuotation(QuotationUpsertRequest request) {
        log.info("Creating new quotation for customerId={}, projectName={}", request.getCustomerId(), request.getProjectName());

        LocalDate today = LocalDate.now();

        // 1. Thẩm định ngày hiệu lực: validUntil không được trước ngày lập (today)
        if (request.getValidUntil() != null && request.getValidUntil().isBefore(today)) {
            throw new AppException(SalesErrorCode.INVALID_VALID_UNTIL_DATE, request.getValidUntil(), today);
        }

        // 2. Thẩm định Khách hàng (CRM)
        CustomerSummaryDto customer = crmApi.getCustomerById(request.getCustomerId())
                .orElseThrow(() -> new AppException(SalesErrorCode.CUSTOMER_NOT_FOUND, request.getCustomerId()));

        if (!"ACTIVE".equalsIgnoreCase(customer.status())) {
            throw new AppException(SalesErrorCode.CUSTOMER_NOT_ACTIVE, customer.customerCode() != null ? customer.customerCode() : customer.customerId());
        }

        // Tự động lấy địa chỉ giao hàng mặc định từ CRM nếu request không truyền
        String deliveryAddress = request.getDeliveryAddress();
        if ((deliveryAddress == null || deliveryAddress.isBlank()) && request.getCustomerId() != null) {
            deliveryAddress = crmApi.getDefaultDeliveryAddress(request.getCustomerId())
                    .map(CustomerAddressDto::addressText)
                    .orElse(null);
        }

        // 3. Xử lý danh sách sản phẩm (items)
        List<QuotationItemRequest> itemRequests = request.getItems() != null ? request.getItems() : Collections.emptyList();
        UUID quotationId = UUID.randomUUID();

        // Thẩm định SKU & quy cách đơn vị tính
        Map<UUID, CatalogSkuSummaryDto> skuMap = validateVariantsAndGetSkuMap(itemRequests);

        // Tra cứu tồn kho
        Set<UUID> variantIds = skuMap.keySet();
        Map<UUID, StockSummaryDto> stockMap = variantIds.isEmpty() ? Collections.emptyMap() : inventoryApi.getStockSummaries(variantIds);

        // Chuẩn bị dữ liệu tính toán và danh sách entity items
        List<CalculationLine> calcLines = new ArrayList<>(itemRequests.size());
        List<BigDecimal> discountRates = new ArrayList<>(itemRequests.size());
        List<QuotationItem> quotationItems = new ArrayList<>(itemRequests.size());
        Map<UUID, String> skuNames = new HashMap<>();

        buildQuotationLinesAndEntities(quotationId, itemRequests, skuMap, stockMap, calcLines, discountRates, quotationItems, skuNames);

        // 4. Tính toán tài chính
        QuotationTotals totals = quotationCalculator.calculate(calcLines, request.getVatRate(), request.getShippingFee());

        // 5. Kiểm tra hạn mức chiết khấu
        DiscountCheckResult approvalResult = discountPolicyChecker.check(discountRates, DEFAULT_STAFF_DISCOUNT_LIMIT);

        // 6. Khởi tạo và lưu Entity Quotation
        UUID currentUserId = iamApi.getCurrentUserId();
        String quotationCode = generateQuotationCode();

        Quotation quotation = quotationMapper.toEntity(request, quotationId, quotationCode, 1, currentUserId);
        if (deliveryAddress != null && (quotation.getDeliveryAddress() == null || quotation.getDeliveryAddress().isBlank())) {
            quotation.setDeliveryAddress(deliveryAddress);
        }
        quotation.setStatus(QuotationStatus.DRAFT);
        quotation.setTotalAmount(totals.grandTotal());

        quotation = quotationRepository.save(quotation);
        if (!quotationItems.isEmpty()) {
            quotationItemRepository.saveAll(quotationItems);
        }

        List<QuotationPaymentMilestone> savedMilestones = new ArrayList<>();
        if (request.getMilestones() != null && !request.getMilestones().isEmpty()) {
            int order = 1;
            for (QuotationPaymentMilestoneRequest mReq : request.getMilestones()) {
                savedMilestones.add(QuotationPaymentMilestone.builder()
                        .quotationId(quotationId)
                        .stageName(mReq.getStageName())
                        .percent(mReq.getPercent())
                        .sortOrder(mReq.getSortOrder() != null ? mReq.getSortOrder() : order++)
                        .build());
            }
            savedMilestones = quotationPaymentMilestoneRepository.saveAll(savedMilestones);
        }

        // 7. Đóng gói Response (lọc ownerData theo quyền hạn)
        boolean isOwner = isShopOwner();
        return quotationMapper.toResponse(
                quotation,
                quotationItems,
                savedMilestones,
                totals,
                approvalResult,
                customer.customerName(),
                skuNames,
                isOwner
        );
    }

    @Override
    @Transactional(readOnly = true)
    public QuotationResponse previewCalculation(QuotationUpsertRequest request) {
        log.info("Previewing calculation in-memory without database mutation");

        List<QuotationItemRequest> itemRequests = request.getItems() != null ? request.getItems() : Collections.emptyList();

        Map<UUID, CatalogSkuSummaryDto> skuMap = validateVariantsAndGetSkuMap(itemRequests);
        Set<UUID> variantIds = skuMap.keySet();
        Map<UUID, StockSummaryDto> stockMap = variantIds.isEmpty() ? Collections.emptyMap() : inventoryApi.getStockSummaries(variantIds);

        List<CalculationLine> calcLines = new ArrayList<>(itemRequests.size());
        List<BigDecimal> discountRates = new ArrayList<>(itemRequests.size());
        List<QuotationItem> virtualItems = new ArrayList<>(itemRequests.size());
        Map<UUID, String> skuNames = new HashMap<>();

        UUID previewId = UUID.randomUUID();
        buildQuotationLinesAndEntities(previewId, itemRequests, skuMap, stockMap, calcLines, discountRates, virtualItems, skuNames);

        QuotationTotals totals = quotationCalculator.calculate(calcLines, request.getVatRate(), request.getShippingFee());
        DiscountCheckResult approvalResult = discountPolicyChecker.check(discountRates, DEFAULT_STAFF_DISCOUNT_LIMIT);

        String customerName = null;
        if (request.getCustomerId() != null) {
            customerName = crmApi.getCustomerById(request.getCustomerId())
                    .map(CustomerSummaryDto::customerName)
                    .orElse(null);
        }

        Quotation virtualQuotation = quotationMapper.toEntity(request, previewId, "PREVIEW", 1, iamApi.getCurrentUserId());
        virtualQuotation.setStatus(QuotationStatus.DRAFT);
        virtualQuotation.setTotalAmount(totals.grandTotal());

        List<QuotationPaymentMilestone> virtualMilestones = new ArrayList<>();
        if (request.getMilestones() != null && !request.getMilestones().isEmpty()) {
            int order = 1;
            for (QuotationPaymentMilestoneRequest mReq : request.getMilestones()) {
                virtualMilestones.add(QuotationPaymentMilestone.builder()
                        .quotationId(previewId)
                        .stageName(mReq.getStageName())
                        .percent(mReq.getPercent())
                        .sortOrder(mReq.getSortOrder() != null ? mReq.getSortOrder() : order++)
                        .build());
            }
        }

        boolean isOwner = isShopOwner();
        return quotationMapper.toResponse(
                virtualQuotation,
                virtualItems,
                virtualMilestones,
                totals,
                approvalResult,
                customerName,
                skuNames,
                isOwner
        );
    }

    @Override
    @Transactional
    public QuotationResponse updateQuotation(UUID quotationId, QuotationUpsertRequest request) {
        log.info("Updating quotation quotationId={}", quotationId);

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new AppException(SalesErrorCode.QUOTATION_NOT_FOUND, quotationId));

        // Kiểm tra khóa trạng thái: Chỉ cho phép sửa khi DRAFT hoặc REJECTED
        if (quotation.getStatus() != QuotationStatus.DRAFT && quotation.getStatus() != QuotationStatus.REJECTED) {
            throw new AppException(SalesErrorCode.QUOTATION_STATE_LOCKED, quotation.getStatus());
        }

        // Kiểm tra quyền sở hữu
        UUID currentUserId = iamApi.getCurrentUserId();
        if (!quotation.getCreatedBy().equals(currentUserId) && !isShopOwner()) {
            throw new AppException(SalesErrorCode.FORBIDDEN_QUOTATION_ACCESS);
        }

        // Validate ngày hiệu lực nếu có truyền
        if (request.getValidUntil() != null && request.getValidUntil().isBefore(quotation.getQuotationDate())) {
            throw new AppException(SalesErrorCode.INVALID_VALID_UNTIL_DATE, request.getValidUntil(), quotation.getQuotationDate());
        }

        // Validate khách hàng
        CustomerSummaryDto customer = crmApi.getCustomerById(request.getCustomerId())
                .orElseThrow(() -> new AppException(SalesErrorCode.CUSTOMER_NOT_FOUND, request.getCustomerId()));
        if (!"ACTIVE".equalsIgnoreCase(customer.status())) {
            throw new AppException(SalesErrorCode.CUSTOMER_NOT_ACTIVE, customer.customerCode() != null ? customer.customerCode() : customer.customerId());
        }

        // Cập nhật Header
        quotation.setProjectName(request.getProjectName());
        quotation.setCustomerId(request.getCustomerId());
        quotation.setNote(request.getNote());
        if (request.getDeliveryAddress() != null && !request.getDeliveryAddress().isBlank()) {
            quotation.setDeliveryAddress(request.getDeliveryAddress());
        }
        if (request.getDeliverySchedule() != null) {
            quotation.setDeliverySchedule(request.getDeliverySchedule());
        }
        if (request.getValidUntil() != null) {
            quotation.setValidUntil(request.getValidUntil());
        }
        if (request.getVatRate() != null) {
            quotation.setVatRate(request.getVatRate());
        }
        if (request.getShippingFee() != null) {
            quotation.setShippingFee(request.getShippingFee());
        }
        if (request.getSentToEmail() != null) {
            quotation.setSentToEmail(request.getSentToEmail());
        }

        // Nếu trước đó bị REJECTED mà Sales sửa lại -> chuyển về DRAFT
        if (quotation.getStatus() == QuotationStatus.REJECTED) {
            quotation.setStatus(QuotationStatus.DRAFT);
        }

        // Xóa dòng cũ
        quotationItemRepository.deleteByQuotationId(quotationId);

        // Tạo dòng mới
        List<QuotationItemRequest> itemRequests = request.getItems() != null ? request.getItems() : Collections.emptyList();
        Map<UUID, CatalogSkuSummaryDto> skuMap = validateVariantsAndGetSkuMap(itemRequests);
        Set<UUID> variantIds = skuMap.keySet();
        Map<UUID, StockSummaryDto> stockMap = variantIds.isEmpty() ? Collections.emptyMap() : inventoryApi.getStockSummaries(variantIds);

        List<CalculationLine> calcLines = new ArrayList<>(itemRequests.size());
        List<BigDecimal> discountRates = new ArrayList<>(itemRequests.size());
        List<QuotationItem> newItems = new ArrayList<>(itemRequests.size());
        Map<UUID, String> skuNames = new HashMap<>();

        buildQuotationLinesAndEntities(quotationId, itemRequests, skuMap, stockMap, calcLines, discountRates, newItems, skuNames);

        QuotationTotals totals = quotationCalculator.calculate(calcLines, quotation.getVatRate(), quotation.getShippingFee());
        DiscountCheckResult approvalResult = discountPolicyChecker.check(discountRates, DEFAULT_STAFF_DISCOUNT_LIMIT);

        quotation.setTotalAmount(totals.grandTotal());
        quotation = quotationRepository.save(quotation);
        if (!newItems.isEmpty()) {
            quotationItemRepository.saveAll(newItems);
        }

        quotationPaymentMilestoneRepository.deleteByQuotationId(quotationId);
        List<QuotationPaymentMilestone> updatedMilestones = new ArrayList<>();
        if (request.getMilestones() != null && !request.getMilestones().isEmpty()) {
            int order = 1;
            for (QuotationPaymentMilestoneRequest mReq : request.getMilestones()) {
                updatedMilestones.add(QuotationPaymentMilestone.builder()
                        .quotationId(quotationId)
                        .stageName(mReq.getStageName())
                        .percent(mReq.getPercent())
                        .sortOrder(mReq.getSortOrder() != null ? mReq.getSortOrder() : order++)
                        .build());
            }
            updatedMilestones = quotationPaymentMilestoneRepository.saveAll(updatedMilestones);
        }

        boolean isOwner = isShopOwner();
        return quotationMapper.toResponse(
                quotation,
                newItems,
                updatedMilestones,
                totals,
                approvalResult,
                customer.customerName(),
                skuNames,
                isOwner
        );
    }

    @Override
    @Transactional
    public void deleteQuotation(UUID quotationId) {
        log.info("Deleting quotation quotationId={}", quotationId);

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new AppException(SalesErrorCode.QUOTATION_NOT_FOUND, quotationId));

        // Chỉ cho phép xóa khi status == DRAFT VÀ revisionNo == 1
        if (quotation.getStatus() != QuotationStatus.DRAFT || (quotation.getRevisionNo() != null && quotation.getRevisionNo() > 1)) {
            throw new AppException(SalesErrorCode.ONLY_DRAFT_CAN_BE_DELETED);
        }

        UUID currentUserId = iamApi.getCurrentUserId();
        if (!quotation.getCreatedBy().equals(currentUserId) && !isShopOwner()) {
            throw new AppException(SalesErrorCode.FORBIDDEN_QUOTATION_ACCESS);
        }

        quotationItemRepository.deleteByQuotationId(quotationId);
        quotationPaymentMilestoneRepository.deleteByQuotationId(quotationId);
        quotationRepository.delete(quotation);
    }

    // =========================================================================
    // NHÓM 3: LUỒNG PHÊ DUYỆT CHIẾT KHẤU VƯỢT KHUNG (APPROVAL WORKFLOW - AF1)
    // =========================================================================

    @Override
    @Transactional
    public QuotationResponse submitForApproval(UUID quotationId, SubmitApprovalRequest request) {
        log.info("Submitting quotation for approval quotationId={}", quotationId);

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new AppException(SalesErrorCode.QUOTATION_NOT_FOUND, quotationId));

        // Kiểm tra quyền sở hữu: chỉ người tạo hoặc Admin mới được trình duyệt
        UUID currentUserId = iamApi.getCurrentUserId();
        if (!quotation.getCreatedBy().equals(currentUserId) && !isShopOwner()) {
            throw new AppException(SalesErrorCode.FORBIDDEN_QUOTATION_ACCESS);
        }

        if (quotation.getStatus() != QuotationStatus.DRAFT && quotation.getStatus() != QuotationStatus.REJECTED) {
            throw new AppException(SalesErrorCode.INVALID_STATUS_TRANSITION, quotation.getStatus());
        }

        if (request == null || request.getApprovalNote() == null || request.getApprovalNote().isBlank()) {
            throw new AppException(SalesErrorCode.APPROVAL_NOTE_REQUIRED);
        }

        List<QuotationItem> items = quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId);
        if (items.isEmpty()) {
            throw new AppException(SalesErrorCode.QUOTATION_ITEMS_EMPTY);
        }

        // Refresh tồn kho thời gian thực cho các dòng items trước khi trình duyệt
        Set<UUID> variantIds = items.stream().map(QuotationItem::getVariantId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!variantIds.isEmpty()) {
            Map<UUID, StockSummaryDto> stockMap = inventoryApi.getStockSummaries(variantIds);
            boolean itemsUpdated = false;
            for (QuotationItem item : items) {
                StockSummaryDto stockDto = stockMap.get(item.getVariantId());
                BigDecimal availableQty = (stockDto != null && stockDto.availableQty() != null) ? stockDto.availableQty() : BigDecimal.ZERO;
                BigDecimal newBackorder = item.getQuantity().subtract(availableQty).max(BigDecimal.ZERO);
                Integer newLeadTime = newBackorder.compareTo(BigDecimal.ZERO) > 0 ? DEFAULT_BACKORDER_LEAD_TIME_DAYS : 0;
                if (item.getBackorderQty().compareTo(newBackorder) != 0 || !Objects.equals(item.getLeadTimeDays(), newLeadTime)) {
                    item.setBackorderQty(newBackorder);
                    item.setLeadTimeDays(newLeadTime);
                    itemsUpdated = true;
                }
            }
            if (itemsUpdated) {
                quotationItemRepository.saveAll(items);
            }
        }

        // Kiểm tra xem có thực sự vượt hạn mức không
        List<BigDecimal> discountRates = items.stream()
                .filter(i -> i.getParentItemId() == null)
                .map(QuotationItem::getDiscountRate)
                .toList();

        DiscountCheckResult discountCheck = discountPolicyChecker.check(discountRates, DEFAULT_STAFF_DISCOUNT_LIMIT);
        if (!discountCheck.requiresApproval()) {
            throw new AppException(SalesErrorCode.DISCOUNT_WITHIN_LIMIT, DEFAULT_STAFF_DISCOUNT_LIMIT);
        }

        quotation.setStatus(QuotationStatus.PENDING_APPROVAL);
        quotation.setApprovalNote(request.getApprovalNote().trim());
        quotation = quotationRepository.save(quotation);

        return assembleFullResponse(quotation, items, discountCheck);
    }

    @Override
    @Transactional
    public QuotationResponse approveQuotation(UUID quotationId, ApproveQuotationRequest request) {
        log.info("Approving quotation quotationId={}", quotationId);

        if (!isShopOwner()) {
            throw new AppException(CommonErrorCode.ACCESS_DENIED, "Chỉ Quản trị viên / Chủ cửa hàng mới có quyền phê duyệt báo giá");
        }

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new AppException(SalesErrorCode.QUOTATION_NOT_FOUND, quotationId));

        if (quotation.getStatus() != QuotationStatus.PENDING_APPROVAL && quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new AppException(SalesErrorCode.INVALID_STATUS_TRANSITION, quotation.getStatus());
        }

        List<QuotationItem> items = quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId);

        // Chốt biên lợi nhuận gộp tại thời điểm duyệt
        BigDecimal netRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (QuotationItem item : items) {
            if (item.getParentItemId() == null) {
                BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                BigDecimal price = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                BigDecimal rate = item.getDiscountRate() != null ? item.getDiscountRate() : BigDecimal.ZERO;
                BigDecimal cost = item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO;

                BigDecimal gross = qty.multiply(price);
                BigDecimal discountAmt = gross.multiply(rate).divide(BigDecimal.valueOf(100), MONEY_SCALE, ROUNDING_MODE);
                BigDecimal lineNet = gross.setScale(MONEY_SCALE, ROUNDING_MODE).subtract(discountAmt);
                BigDecimal lineCost = qty.multiply(cost).setScale(MONEY_SCALE, ROUNDING_MODE);

                netRevenue = netRevenue.add(lineNet);
                totalCost = totalCost.add(lineCost);
            }
        }

        BigDecimal marginPercent = BigDecimal.ZERO.setScale(PERCENT_SCALE, ROUNDING_MODE);
        if (netRevenue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal grossProfit = netRevenue.subtract(totalCost);
            marginPercent = grossProfit.multiply(BigDecimal.valueOf(100))
                    .divide(netRevenue, PERCENT_SCALE, ROUNDING_MODE);
        }

        quotation.setMarginPercentAtApproval(marginPercent);
        quotation.setStatus(QuotationStatus.APPROVED);
        quotation.setApprovedBy(iamApi.getCurrentUserId());
        quotation.setApprovedAt(OffsetDateTime.now());
        if (request != null && request.getApprovalNote() != null && !request.getApprovalNote().isBlank()) {
            quotation.setApprovalNote(request.getApprovalNote().trim());
        }

        quotation = quotationRepository.save(quotation);
        return assembleFullResponse(quotation, items, null);
    }

    @Override
    @Transactional
    public QuotationResponse rejectQuotation(UUID quotationId, RejectQuotationRequest request) {
        log.info("Rejecting quotation quotationId={}", quotationId);

        if (!isShopOwner()) {
            throw new AppException(CommonErrorCode.ACCESS_DENIED, "Chỉ Quản trị viên / Chủ cửa hàng mới có quyền từ chối báo giá");
        }

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new AppException(SalesErrorCode.QUOTATION_NOT_FOUND, quotationId));

        if (quotation.getStatus() != QuotationStatus.PENDING_APPROVAL && quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new AppException(SalesErrorCode.INVALID_STATUS_TRANSITION, quotation.getStatus());
        }

        if (request == null || request.getApprovalNote() == null || request.getApprovalNote().isBlank()) {
            throw new AppException(SalesErrorCode.APPROVAL_NOTE_REQUIRED);
        }

        quotation.setStatus(QuotationStatus.REJECTED);
        quotation.setApprovalNote(request.getApprovalNote().trim());
        quotation.setApprovedBy(iamApi.getCurrentUserId());
        quotation.setApprovedAt(OffsetDateTime.now());

        quotation = quotationRepository.save(quotation);
        List<QuotationItem> items = quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId);

        return assembleFullResponse(quotation, items, null);
    }

    // =========================================================================
    // NHÓM 5: QUẢN LÝ PHIÊN BẢN & KẾT QUẢ ĐÀM PHÁN (POST-SENT APIs - AF3 & AF5)
    // =========================================================================

    @Override
    @Transactional
    public QuotationResponse sendQuotation(UUID quotationId) {
        log.info("Sending quotation quotationId={}", quotationId);

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new AppException(SalesErrorCode.QUOTATION_NOT_FOUND, quotationId));

        // Kiểm tra quyền sở hữu: chỉ người tạo hoặc Admin mới được gửi
        UUID currentUserId = iamApi.getCurrentUserId();
        if (!quotation.getCreatedBy().equals(currentUserId) && !isShopOwner()) {
            throw new AppException(SalesErrorCode.FORBIDDEN_QUOTATION_ACCESS);
        }

        // Cho phép phát hành nếu đang ở DRAFT hoặc APPROVED
        if (quotation.getStatus() != QuotationStatus.DRAFT && quotation.getStatus() != QuotationStatus.APPROVED) {
            throw new AppException(SalesErrorCode.INVALID_STATUS_TRANSITION, quotation.getStatus());
        }

        List<QuotationItem> items = quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId);
        if (items.isEmpty()) {
            throw new AppException(SalesErrorCode.QUOTATION_ITEMS_EMPTY);
        }

        // Nếu đang ở DRAFT, nếu chiết khấu vượt hạn mức (> 10%) thì BẮT BUỘC phải qua bước DUYỆT (APPROVED) trước khi gửi khách
        if (quotation.getStatus() == QuotationStatus.DRAFT) {
            List<BigDecimal> discountRates = items.stream()
                    .filter(i -> i.getParentItemId() == null)
                    .map(QuotationItem::getDiscountRate)
                    .toList();
            DiscountCheckResult discountCheck = discountPolicyChecker.check(discountRates, DEFAULT_STAFF_DISCOUNT_LIMIT);
            if (discountCheck.requiresApproval()) {
                throw new AppException(SalesErrorCode.DISCOUNT_LIMIT_EXCEEDED, discountCheck.maxDiscountRate(), DEFAULT_STAFF_DISCOUNT_LIMIT);
            }
        }

        // Rule 4: Hạn hiệu lực bắt buộc khi phát hành
        if (quotation.getValidUntil() == null) {
            quotation.setValidUntil(quotation.getQuotationDate().plusDays(30));
        } else if (quotation.getValidUntil().isBefore(quotation.getQuotationDate())) {
            throw new AppException(SalesErrorCode.INVALID_VALID_UNTIL_DATE, quotation.getValidUntil(), quotation.getQuotationDate());
        }

        quotation.setStatus(QuotationStatus.SENT);
        quotation.setSentAt(OffsetDateTime.now());

        quotation = quotationRepository.save(quotation);
        return assembleFullResponse(quotation, items, null);
    }

    @Override
    @Transactional
    public QuotationResponse createRevision(UUID quotationId) {
        log.info("Creating new revision from quotationId={}", quotationId);

        Quotation sourceQuotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new AppException(SalesErrorCode.QUOTATION_NOT_FOUND, quotationId));

        // Kiểm tra quyền sở hữu: chỉ người tạo hoặc Admin mới được tạo bản sửa đổi
        UUID currentUserId = iamApi.getCurrentUserId();
        if (!sourceQuotation.getCreatedBy().equals(currentUserId) && !isShopOwner()) {
            throw new AppException(SalesErrorCode.FORBIDDEN_QUOTATION_ACCESS);
        }

        if (sourceQuotation.getStatus() != QuotationStatus.SENT && sourceQuotation.getStatus() != QuotationStatus.APPROVED && sourceQuotation.getStatus() != QuotationStatus.LOST) {
            throw new AppException(SalesErrorCode.QUOTATION_NOT_SENT, sourceQuotation.getStatus());
        }

        // Kiểm tra xem đã có revision nào đang xử lý chưa
        boolean hasOpenRevision = quotationRepository.existsByQuotationCodeAndStatusIn(
                sourceQuotation.getQuotationCode(),
                List.of(QuotationStatus.DRAFT, QuotationStatus.PENDING_APPROVAL, QuotationStatus.APPROVED)
        );
        if (hasOpenRevision) {
            throw new AppException(SalesErrorCode.REVISION_ALREADY_EXISTS, sourceQuotation.getQuotationCode());
        }

        // Tìm revisionNo tiếp theo
        List<Quotation> allRevisions = quotationRepository.findByQuotationCodeOrderByRevisionNoDesc(sourceQuotation.getQuotationCode());
        int nextRevisionNo = allRevisions.isEmpty() ? 2 : allRevisions.get(0).getRevisionNo() + 1;

        UUID newQuotationId = UUID.randomUUID();

        // Khởi tạo Header mới
        Quotation newQuotation = Quotation.builder()
                .quotationId(newQuotationId)
                .quotationCode(sourceQuotation.getQuotationCode())
                .revisionNo(nextRevisionNo)
                .parentQuotationId(sourceQuotation.getQuotationId())
                .customerId(sourceQuotation.getCustomerId())
                .projectName(sourceQuotation.getProjectName())
                .deliveryAddress(sourceQuotation.getDeliveryAddress())
                .deliverySchedule(sourceQuotation.getDeliverySchedule())
                .sentToEmail(sourceQuotation.getSentToEmail())
                .note(sourceQuotation.getNote())
                .status(QuotationStatus.DRAFT)
                .quotationDate(LocalDate.now())
                .validUntil(LocalDate.now().plusDays(30))
                .vatRate(sourceQuotation.getVatRate())
                .shippingFee(sourceQuotation.getShippingFee())
                .createdBy(iamApi.getCurrentUserId())
                .build();

        // Copy items và refresh giá Catalog & tồn kho Inventory
        List<QuotationItem> sourceItems = quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(sourceQuotation.getQuotationId());
        Set<UUID> variantIds = sourceItems.stream().map(QuotationItem::getVariantId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, CatalogSkuSummaryDto> skuMap = variantIds.isEmpty() ? Collections.emptyMap() : catalogApi.getSkuSummaries(variantIds);
        Map<UUID, StockSummaryDto> stockMap = variantIds.isEmpty() ? Collections.emptyMap() : inventoryApi.getStockSummaries(variantIds);

        List<CalculationLine> calcLines = new ArrayList<>();
        List<BigDecimal> discountRates = new ArrayList<>();
        List<QuotationItem> newItems = new ArrayList<>();
        Map<UUID, String> skuNames = new HashMap<>();

        Map<UUID, UUID> oldToNewItemIds = new HashMap<>();
        for (QuotationItem oldItem : sourceItems) {
            oldToNewItemIds.put(oldItem.getQuotationItemId(), UUID.randomUUID());
        }

        for (QuotationItem oldItem : sourceItems) {
            CatalogSkuSummaryDto skuDto = skuMap.get(oldItem.getVariantId());
            StockSummaryDto stockDto = stockMap.get(oldItem.getVariantId());

            BigDecimal unitPrice = (skuDto != null && skuDto.sellingPrice() != null) ? skuDto.sellingPrice() : oldItem.getUnitPrice();
            BigDecimal unitCost = (skuDto != null && skuDto.costPrice() != null) ? skuDto.costPrice() : oldItem.getUnitCost();
            BigDecimal qty = oldItem.getQuantity();
            BigDecimal availableQty = (stockDto != null && stockDto.availableQty() != null) ? stockDto.availableQty() : BigDecimal.ZERO;
            BigDecimal backorderQty = qty.subtract(availableQty).max(BigDecimal.ZERO);
            Integer leadTimeDays = backorderQty.compareTo(BigDecimal.ZERO) > 0 ? DEFAULT_BACKORDER_LEAD_TIME_DAYS : 0;
            boolean isComponent = oldItem.getParentItemId() != null;

            calcLines.add(new CalculationLine(qty, unitPrice, oldItem.getDiscountRate(), unitCost, isComponent));
            if (!isComponent) {
                discountRates.add(oldItem.getDiscountRate());
            }
            if (skuDto != null) {
                skuNames.put(skuDto.variantId(), skuDto.variantName());
            }

            UUID newParentItemId = oldItem.getParentItemId() != null ? oldToNewItemIds.get(oldItem.getParentItemId()) : null;

            QuotationItem newItem = QuotationItem.builder()
                    .quotationItemId(oldToNewItemIds.get(oldItem.getQuotationItemId()))
                    .quotationId(newQuotationId)
                    .parentItemId(newParentItemId)
                    .variantId(oldItem.getVariantId())
                    .sku(skuDto != null ? skuDto.sku() : oldItem.getSku())
                    .unit(skuDto != null && skuDto.unit() != null ? skuDto.unit() : oldItem.getUnit())
                    .quantity(qty)
                    .unitPrice(unitPrice)
                    .discountRate(oldItem.getDiscountRate())
                    .unitCost(unitCost)
                    .backorderQty(backorderQty)
                    .leadTimeDays(leadTimeDays)
                    .sortOrder(oldItem.getSortOrder())
                    .build();

            newItems.add(newItem);
        }

        QuotationTotals totals = quotationCalculator.calculate(calcLines, newQuotation.getVatRate(), newQuotation.getShippingFee());
        newQuotation.setTotalAmount(totals.grandTotal());

        newQuotation = quotationRepository.save(newQuotation);
        if (!newItems.isEmpty()) {
            quotationItemRepository.saveAll(newItems);
        }

        List<QuotationPaymentMilestone> oldMilestones = quotationPaymentMilestoneRepository.findByQuotationIdOrderBySortOrderAsc(sourceQuotation.getQuotationId());
        List<QuotationPaymentMilestone> newMilestones = new ArrayList<>();
        for (QuotationPaymentMilestone oldM : oldMilestones) {
            newMilestones.add(QuotationPaymentMilestone.builder()
                    .quotationId(newQuotationId)
                    .stageName(oldM.getStageName())
                    .percent(oldM.getPercent())
                    .sortOrder(oldM.getSortOrder())
                    .build());
        }
        if (!newMilestones.isEmpty()) {
            newMilestones = quotationPaymentMilestoneRepository.saveAll(newMilestones);
        }

        DiscountCheckResult approvalResult = discountPolicyChecker.check(discountRates, DEFAULT_STAFF_DISCOUNT_LIMIT);
        String customerName = crmApi.getCustomerById(newQuotation.getCustomerId())
                .map(CustomerSummaryDto::customerName)
                .orElse(null);

        boolean isOwner = isShopOwner();
        return quotationMapper.toResponse(
                newQuotation,
                newItems,
                newMilestones,
                totals,
                approvalResult,
                customerName,
                skuNames,
                isOwner
        );
    }

    @Override
    @Transactional
    public QuotationResponse markAccepted(UUID quotationId, MarkAcceptedRequest request) {
        log.info("Marking quotation as accepted quotationId={}", quotationId);

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new AppException(SalesErrorCode.QUOTATION_NOT_FOUND, quotationId));

        // Kiểm tra quyền sở hữu: chỉ người tạo hoặc Admin mới được chốt thành công
        UUID currentUserId = iamApi.getCurrentUserId();
        if (!quotation.getCreatedBy().equals(currentUserId) && !isShopOwner()) {
            throw new AppException(SalesErrorCode.FORBIDDEN_QUOTATION_ACCESS);
        }

        if (quotation.getStatus() != QuotationStatus.SENT && quotation.getStatus() != QuotationStatus.APPROVED) {
            throw new AppException(SalesErrorCode.QUOTATION_NOT_SENT, quotation.getStatus());
        }

        if (quotation.getValidUntil() != null && quotation.getValidUntil().isBefore(LocalDate.now())) {
            log.warn("Quotation {} has expired on {}, accepting with warning", quotation.getQuotationCode(), quotation.getValidUntil());
        }

        quotation.setStatus(QuotationStatus.ACCEPTED);
        quotation.setAcceptedAt(OffsetDateTime.now());
        if (request != null && request.getSignedFileUrl() != null && !request.getSignedFileUrl().isBlank()) {
            quotation.setSignedFileUrl(request.getSignedFileUrl().trim());
        }

        // Chuyển các bản ghi khác cùng mã sang SUPERSEDED
        List<Quotation> otherRevisions = quotationRepository.findByQuotationCodeAndQuotationIdNot(quotation.getQuotationCode(), quotationId);
        for (Quotation rev : otherRevisions) {
            if (rev.getStatus() != QuotationStatus.ACCEPTED && rev.getStatus() != QuotationStatus.LOST) {
                rev.setStatus(QuotationStatus.SUPERSEDED);
            }
        }
        if (!otherRevisions.isEmpty()) {
            quotationRepository.saveAll(otherRevisions);
        }

        quotation = quotationRepository.save(quotation);
        List<QuotationItem> items = quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId);

        return assembleFullResponse(quotation, items, null);
    }

    @Override
    @Transactional
    public QuotationResponse markLost(UUID quotationId, MarkLostRequest request) {
        log.info("Marking quotation as lost quotationId={}", quotationId);

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new AppException(SalesErrorCode.QUOTATION_NOT_FOUND, quotationId));

        // Kiểm tra quyền sở hữu: chỉ người tạo hoặc Admin mới được báo mất dự án
        UUID currentUserId = iamApi.getCurrentUserId();
        if (!quotation.getCreatedBy().equals(currentUserId) && !isShopOwner()) {
            throw new AppException(SalesErrorCode.FORBIDDEN_QUOTATION_ACCESS);
        }

        if (quotation.getStatus() != QuotationStatus.SENT && quotation.getStatus() != QuotationStatus.APPROVED) {
            throw new AppException(SalesErrorCode.QUOTATION_NOT_SENT, quotation.getStatus());
        }

        if (request == null || request.getLostReason() == null || request.getLostReason().isBlank()) {
            throw new AppException(SalesErrorCode.LOST_REASON_REQUIRED);
        }

        quotation.setStatus(QuotationStatus.LOST);
        quotation.setLostAt(OffsetDateTime.now());
        quotation.setLostReason(request.getLostReason().trim());

        quotation = quotationRepository.save(quotation);
        List<QuotationItem> items = quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId);

        return assembleFullResponse(quotation, items, null);
    }

    // =========================================================================
    // NHÓM 6: TRA CỨU & DANH SÁCH BÁO GIÁ (QUERY & SEARCH APIs)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public QuotationResponse getQuotationById(UUID quotationId) {
        log.info("Getting quotation details quotationId={}", quotationId);

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new AppException(SalesErrorCode.QUOTATION_NOT_FOUND, quotationId));

        UUID currentUserId = iamApi.getCurrentUserId();
        if (!quotation.getCreatedBy().equals(currentUserId) && !isShopOwner()) {
            throw new AppException(SalesErrorCode.FORBIDDEN_QUOTATION_ACCESS);
        }

        List<QuotationItem> items = quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId);
        List<BigDecimal> discountRates = items.stream()
                .filter(i -> i.getParentItemId() == null)
                .map(QuotationItem::getDiscountRate)
                .toList();
        DiscountCheckResult discountCheck = discountPolicyChecker.check(discountRates, DEFAULT_STAFF_DISCOUNT_LIMIT);
        return assembleFullResponse(quotation, items, discountCheck);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuotationListItemResponse> getRevisionHistory(String quotationCode) {
        log.info("Getting revision history for quotationCode={}", quotationCode);

        List<Quotation> revisions = quotationRepository.findByQuotationCodeOrderByRevisionNoDesc(quotationCode);
        if (revisions.isEmpty()) {
            throw new AppException(SalesErrorCode.QUOTATION_NOT_FOUND, quotationCode);
        }

        UUID currentUserId = iamApi.getCurrentUserId();
        boolean hasAccess = isShopOwner() || revisions.stream().anyMatch(r -> r.getCreatedBy().equals(currentUserId));
        if (!hasAccess) {
            throw new AppException(SalesErrorCode.FORBIDDEN_QUOTATION_ACCESS);
        }

        Map<UUID, String> customerNameCache = new HashMap<>();
        Map<UUID, String> userNameCache = new HashMap<>();

        List<UUID> revIds = revisions.stream().map(Quotation::getQuotationId).toList();
        Map<UUID, Boolean> revApprovalMap = new HashMap<>();
        if (!revIds.isEmpty()) {
            List<QuotationItem> revItems = quotationItemRepository.findByQuotationIdIn(revIds);
            Map<UUID, List<BigDecimal>> discountsByQuotationId = revItems.stream()
                    .filter(i -> i.getParentItemId() == null && i.getDiscountRate() != null)
                    .collect(Collectors.groupingBy(
                            QuotationItem::getQuotationId,
                            Collectors.mapping(QuotationItem::getDiscountRate, Collectors.toList())
                    ));
            for (UUID qId : revIds) {
                List<BigDecimal> rates = discountsByQuotationId.getOrDefault(qId, Collections.emptyList());
                boolean req = discountPolicyChecker.check(rates, DEFAULT_STAFF_DISCOUNT_LIMIT).requiresApproval();
                revApprovalMap.put(qId, req);
            }
        }

        return revisions.stream().map(q -> {
            String customerName = customerNameCache.computeIfAbsent(q.getCustomerId(), id ->
                    crmApi.getCustomerById(id).map(CustomerSummaryDto::customerName).orElse(null)
            );
            String createdByName = userNameCache.computeIfAbsent(q.getCreatedBy(), id -> {
                try {
                    UserSummaryDto user = iamApi.getUserById(id);
                    return user != null ? (user.fullName() != null ? user.fullName() : user.username()) : null;
                } catch (Exception ex) {
                    return null;
                }
            });
            Boolean requiresApproval = revApprovalMap.getOrDefault(q.getQuotationId(), false);
            return quotationMapper.toListItemResponse(q, customerName, createdByName, requiresApproval);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuotationListItemResponse> listQuotations(QuotationFilterRequest filterRequest) {
        log.info("Listing quotations with filters");

        if (filterRequest == null) {
            filterRequest = new QuotationFilterRequest();
        }

        Pageable pageable = PageableUtils.createPageable(
                filterRequest.getPage(),
                filterRequest.getSize(),
                filterRequest.getSortBy(),
                filterRequest.getDirection(),
                ALLOWED_SORT_FIELDS,
                DEFAULT_SORT_FIELD,
                Sort.Direction.DESC
        );

        UUID currentUserId = iamApi.getCurrentUserId();
        boolean isOwnerOrAdmin = isShopOwner();

        QuotationFilterRequest finalFilter = filterRequest;
        Specification<Quotation> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Phân quyền dữ liệu: Nhân viên chỉ xem được báo giá của chính mình, Admin xem được tất cả
            if (!isOwnerOrAdmin) {
                predicates.add(cb.equal(root.get("createdBy"), currentUserId));
            }

            // Lọc theo trạng thái
            if (finalFilter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), finalFilter.getStatus()));
            }

            // Lọc theo khách hàng
            if (finalFilter.getCustomerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), finalFilter.getCustomerId()));
            }

            // Lọc theo khoảng ngày lập báo giá
            if (finalFilter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("quotationDate"), finalFilter.getFromDate()));
            }
            if (finalFilter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("quotationDate"), finalFilter.getToDate()));
            }

            // Tìm kiếm tự do theo mã báo giá hoặc tên công trình
            if (finalFilter.getSearch() != null && !finalFilter.getSearch().isBlank()) {
                String term = "%" + finalFilter.getSearch().trim().toLowerCase(Locale.ROOT) + "%";
                Predicate codeMatch = cb.like(cb.lower(root.get("quotationCode")), term);
                Predicate projectMatch = cb.like(cb.lower(root.get("projectName")), term);
                predicates.add(cb.or(codeMatch, projectMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Quotation> pageResult = quotationRepository.findAll(spec, pageable);

        Map<UUID, String> customerNameCache = new HashMap<>();
        Map<UUID, String> userNameCache = new HashMap<>();

        List<UUID> quotationIds = pageResult.getContent().stream().map(Quotation::getQuotationId).toList();
        Map<UUID, Boolean> approvalMap = new HashMap<>();
        if (!quotationIds.isEmpty()) {
            List<QuotationItem> pageItems = quotationItemRepository.findByQuotationIdIn(quotationIds);
            Map<UUID, List<BigDecimal>> discountsByQuotationId = pageItems.stream()
                    .filter(i -> i.getParentItemId() == null && i.getDiscountRate() != null)
                    .collect(Collectors.groupingBy(
                            QuotationItem::getQuotationId,
                            Collectors.mapping(QuotationItem::getDiscountRate, Collectors.toList())
                    ));
            for (UUID qId : quotationIds) {
                List<BigDecimal> rates = discountsByQuotationId.getOrDefault(qId, Collections.emptyList());
                boolean requiresApproval = discountPolicyChecker.check(rates, DEFAULT_STAFF_DISCOUNT_LIMIT).requiresApproval();
                approvalMap.put(qId, requiresApproval);
            }
        }

        List<QuotationListItemResponse> items = pageResult.getContent().stream().map(q -> {
            String customerName = customerNameCache.computeIfAbsent(q.getCustomerId(), id ->
                    crmApi.getCustomerById(id).map(CustomerSummaryDto::customerName).orElse(null)
            );
            String createdByName = userNameCache.computeIfAbsent(q.getCreatedBy(), id -> {
                try {
                    UserSummaryDto user = iamApi.getUserById(id);
                    return user != null ? (user.fullName() != null ? user.fullName() : user.username()) : null;
                } catch (Exception ex) {
                    return null;
                }
            });
            Boolean requiresApproval = approvalMap.getOrDefault(q.getQuotationId(), false);
            return quotationMapper.toListItemResponse(q, customerName, createdByName, requiresApproval);
        }).toList();

        return PageResponse.of(
                items,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages()
        );
    }

    // =========================================================================
    // PRIVATE HELPER METHODS
    // =========================================================================
    private boolean isShopOwner() {
        UserSummaryDto user = iamApi.getCurrentUser();
        if (user == null || user.roles() == null) {
            return false;
        }
        return user.roles().stream()
                .anyMatch(r -> "SHOP_OWNER".equalsIgnoreCase(r) || "ROLE_SHOP_OWNER".equalsIgnoreCase(r));
    }

    private QuotationResponse assembleFullResponse(Quotation quotation, List<QuotationItem> items, DiscountCheckResult discountCheck) {
        String customerName = crmApi.getCustomerById(quotation.getCustomerId())
                .map(CustomerSummaryDto::customerName)
                .orElse(null);

        Set<UUID> varIds = items.stream().map(QuotationItem::getVariantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> skuNames = varIds.isEmpty() ? Collections.emptyMap() : catalogApi.getSkuSummaries(varIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().variantName()));

        List<CalculationLine> lines = items.stream()
                .map(i -> new CalculationLine(i.getQuantity(), i.getUnitPrice(), i.getDiscountRate(), i.getUnitCost(), i.getParentItemId() != null))
                .toList();

        QuotationTotals totals = quotationCalculator.calculate(lines, quotation.getVatRate(), quotation.getShippingFee());

        List<QuotationPaymentMilestone> milestones = quotationPaymentMilestoneRepository.findByQuotationIdOrderBySortOrderAsc(quotation.getQuotationId());

        boolean isOwner = isShopOwner();
        return quotationMapper.toResponse(
                quotation,
                items,
                milestones,
                totals,
                discountCheck,
                customerName,
                skuNames,
                isOwner
        );
    }

    private Map<UUID, CatalogSkuSummaryDto> validateVariantsAndGetSkuMap(List<QuotationItemRequest> itemRequests) {
        if (itemRequests == null || itemRequests.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<UUID> seenVariants = new HashSet<>();
        for (QuotationItemRequest itemReq : itemRequests) {
            if (!seenVariants.add(itemReq.getVariantId())) {
                throw new AppException(SalesErrorCode.DUPLICATE_SKU_ITEM, itemReq.getVariantId());
            }
        }

        Set<UUID> variantIds = itemRequests.stream()
                .map(QuotationItemRequest::getVariantId)
                .collect(Collectors.toSet());

        Map<UUID, CatalogSkuSummaryDto> skuMap = catalogApi.getSkuSummaries(variantIds);

        for (UUID varId : variantIds) {
            CatalogSkuSummaryDto skuDto = skuMap.get(varId);
            if (skuDto == null) {
                throw new AppException(SalesErrorCode.SKU_NOT_FOUND, varId);
            }
            if (!"ACTIVE".equalsIgnoreCase(skuDto.status())) {
                throw new AppException(SalesErrorCode.SKU_INACTIVE, skuDto.sku());
            }
        }

        for (QuotationItemRequest itemReq : itemRequests) {
            CatalogSkuSummaryDto skuDto = skuMap.get(itemReq.getVariantId());
            validateUnitQuantity(skuDto.unit(), itemReq.getQuantity());
        }

        return skuMap;
    }

    private void buildQuotationLinesAndEntities(
            UUID quotationId,
            List<QuotationItemRequest> itemRequests,
            Map<UUID, CatalogSkuSummaryDto> skuMap,
            Map<UUID, StockSummaryDto> stockMap,
            List<CalculationLine> calcLines,
            List<BigDecimal> discountRates,
            List<QuotationItem> quotationItems,
            Map<UUID, String> skuNames
    ) {
        int sortOrder = 1;
        for (QuotationItemRequest itemReq : itemRequests) {
            CatalogSkuSummaryDto skuDto = skuMap.get(itemReq.getVariantId());
            StockSummaryDto stockDto = stockMap != null ? stockMap.get(itemReq.getVariantId()) : null;

            BigDecimal availableQty = (stockDto != null && stockDto.availableQty() != null)
                    ? stockDto.availableQty()
                    : BigDecimal.ZERO;

            BigDecimal qty = itemReq.getQuantity();
            BigDecimal backorderQty = qty.subtract(availableQty).max(BigDecimal.ZERO);
            Integer leadTimeDays = backorderQty.compareTo(BigDecimal.ZERO) > 0 ? DEFAULT_BACKORDER_LEAD_TIME_DAYS : 0;

            BigDecimal unitPrice = skuDto.sellingPrice() != null ? skuDto.sellingPrice() : BigDecimal.ZERO;
            BigDecimal unitCost = skuDto.costPrice() != null ? skuDto.costPrice() : BigDecimal.ZERO;
            BigDecimal discountRate = itemReq.getDiscountRate() != null ? itemReq.getDiscountRate() : BigDecimal.ZERO;

            calcLines.add(new CalculationLine(qty, unitPrice, discountRate, unitCost, false));
            discountRates.add(discountRate);
            skuNames.put(skuDto.variantId(), skuDto.variantName());

            QuotationItem quotationItem = QuotationItem.builder()
                    .quotationItemId(UUID.randomUUID())
                    .quotationId(quotationId)
                    .parentItemId(null)
                    .variantId(skuDto.variantId())
                    .sku(skuDto.sku())
                    .unit(skuDto.unit() != null ? skuDto.unit() : "")
                    .quantity(qty)
                    .unitPrice(unitPrice)
                    .discountRate(discountRate)
                    .unitCost(unitCost)
                    .backorderQty(backorderQty)
                    .leadTimeDays(leadTimeDays)
                    .sortOrder(sortOrder++)
                    .build();

            quotationItems.add(quotationItem);
        }
    }

    private synchronized String generateQuotationCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String prefix = "QT-" + datePart + "-";

        Optional<Quotation> top = quotationRepository.findTopByQuotationCodeStartingWithOrderByQuotationCodeDesc(prefix);
        int nextSeq = 1;
        if (top.isPresent()) {
            String lastCode = top.get().getQuotationCode();
            try {
                String seqStr = lastCode.substring(prefix.length());
                nextSeq = Integer.parseInt(seqStr) + 1;
            } catch (Exception ex) {
                nextSeq = (int) quotationRepository.countByQuotationCodeStartingWith(prefix) + 1;
            }
        }
        return String.format("%s%04d", prefix, nextSeq);
    }

    private void validateUnitQuantity(String unit, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String normalizedUnit = unit != null ? unit.trim().toLowerCase(Locale.ROOT) : "";
        if (DISCRETE_UNITS.contains(normalizedUnit)) {
            if (quantity.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                throw new AppException(SalesErrorCode.INVALID_UNIT_QUANTITY, quantity, unit);
            }
        } else {
            BigDecimal stripped = quantity.stripTrailingZeros();
            if (stripped.scale() > 2) {
                throw new AppException(SalesErrorCode.INVALID_UNIT_QUANTITY, quantity, unit);
            }
        }
    }
}
