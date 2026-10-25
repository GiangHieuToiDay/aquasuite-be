package com.aquasuite.ims.module.sales.internal.mapper;

import com.aquasuite.ims.module.sales.internal.calculator.DiscountPolicyChecker;
import com.aquasuite.ims.module.sales.internal.calculator.QuotationCalculator.LineCalculated;
import com.aquasuite.ims.module.sales.internal.calculator.QuotationCalculator.QuotationTotals;
import com.aquasuite.ims.module.sales.internal.entity.Quotation;
import com.aquasuite.ims.module.sales.internal.entity.QuotationItem;
import com.aquasuite.ims.module.sales.internal.payload.request.QuotationUpsertRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationApprovalInfo;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationComponentResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationItemCostResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationItemResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationListItemResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationOwnerData;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface QuotationMapper {

    // 1. Ánh xạ phẳng cho màn hình danh sách (MapStruct tự sinh)
    @Mapping(target = "quotationId", source = "quotation.quotationId")
    @Mapping(target = "quotationCode", source = "quotation.quotationCode")
    @Mapping(target = "revisionNo", source = "quotation.revisionNo")
    @Mapping(target = "status", source = "quotation.status")
    @Mapping(target = "quotationDate", source = "quotation.quotationDate")
    @Mapping(target = "validUntil", source = "quotation.validUntil")
    @Mapping(target = "totalAmount", source = "quotation.totalAmount")
    @Mapping(target = "customerName", source = "customerName")
    @Mapping(target = "projectName", source = "quotation.projectName")
    @Mapping(target = "createdByName", source = "createdByName")
    QuotationListItemResponse toListItemResponse(Quotation quotation, String customerName, String createdByName);

    // 2. Ánh xạ phẳng cho dòng thành phần combo (MapStruct tự sinh)
    @Mapping(target = "sku", source = "item.sku")
    @Mapping(target = "variantName", source = "variantName")
    @Mapping(target = "unit", source = "item.unit")
    @Mapping(target = "quantity", source = "item.quantity")
    @Mapping(target = "unitPrice", source = "item.unitPrice")
    @Mapping(target = "backorderQty", source = "item.backorderQty")
    @Mapping(target = "leadTimeDays", source = "item.leadTimeDays")
    QuotationComponentResponse toComponentResponse(QuotationItem item, String variantName);

    // 3. Ánh xạ phẳng cho summary 7 con số (MapStruct tự sinh)
    QuotationSummaryResponse toSummaryResponse(QuotationTotals totals);

    // 4. Ánh xạ request sang entity (MapStruct tự sinh)
    @Mapping(target = "customerId", source = "request.customerId")
    @Mapping(target = "projectName", source = "request.projectName")
    @Mapping(target = "deliveryAddress", source = "request.deliveryAddress")
    @Mapping(target = "validUntil", source = "request.validUntil")
    @Mapping(target = "vatRate", source = "request.vatRate")
    @Mapping(target = "shippingFee", source = "request.shippingFee")
    @Mapping(target = "note", source = "request.note")
    Quotation toEntity(QuotationUpsertRequest request);

    /**
     * Phương thức khởi tạo entity đầy đủ từ request với các trường định danh bổ sung.
     */
    default Quotation toEntity(
            QuotationUpsertRequest request,
            UUID quotationId,
            String quotationCode,
            Integer revisionNo,
            UUID createdBy
    ) {
        if (request == null) {
            return null;
        }

        Quotation quotation = toEntity(request);
        quotation.setQuotationId(quotationId != null ? quotationId : UUID.randomUUID());
        quotation.setQuotationCode(quotationCode);
        quotation.setRevisionNo(revisionNo != null ? revisionNo : 1);
        quotation.setCreatedBy(createdBy);

        LocalDate qDate = LocalDate.now();
        quotation.setQuotationDate(qDate);
        if (quotation.getValidUntil() == null) {
            quotation.setValidUntil(qDate.plusDays(30));
        }
        if (quotation.getVatRate() == null) {
            quotation.setVatRate(BigDecimal.valueOf(10));
        }
        if (quotation.getShippingFee() == null) {
            quotation.setShippingFee(BigDecimal.ZERO);
        }
        return quotation;
    }

    /**
     * Phương thức điều phối chính dựng cây cha - con components và phân nhánh dữ liệu nội bộ ownerData.
     */
    default QuotationResponse toResponse(
            Quotation quotation,
            List<QuotationItem> items,
            QuotationTotals totals,
            DiscountPolicyChecker.DiscountCheckResult approvalResult,
            String customerName,
            Map<UUID, String> skuNames,
            boolean includeOwnerData
    ) {
        if (quotation == null) {
            return null;
        }

        Map<UUID, String> safeSkuNames = (skuNames != null) ? skuNames : Collections.emptyMap();

        // 1. Dựng danh sách dòng chi tiết (cây cha - con components)
        List<QuotationItemResponse> rootItemResponses = new ArrayList<>();
        List<QuotationItemCostResponse> itemCostResponses = new ArrayList<>();
        Map<UUID, List<QuotationComponentResponse>> componentsMap = new HashMap<>();

        if (items != null) {
            // Bước 1a: Gom các dòng con (parentItemId != null)
            for (QuotationItem item : items) {
                if (item.getParentItemId() != null) {
                    QuotationComponentResponse comp = toComponentResponse(
                            item,
                            safeSkuNames.getOrDefault(item.getVariantId(), null)
                    );
                    componentsMap.computeIfAbsent(item.getParentItemId(), k -> new ArrayList<>()).add(comp);
                }
            }

            // Bước 1b: Map các dòng gốc (parentItemId == null) kèm lineTotals
            for (int i = 0; i < items.size(); i++) {
                QuotationItem item = items.get(i);
                if (item.getParentItemId() == null) {
                    LineCalculated lineCalc = (totals != null && totals.lineTotals() != null && i < totals.lineTotals().size())
                            ? totals.lineTotals().get(i)
                            : null;

                    BigDecimal discountAmt = (lineCalc != null) ? lineCalc.discountAmount() : BigDecimal.ZERO;
                    BigDecimal lineTot = (lineCalc != null) ? lineCalc.lineTotal() : BigDecimal.ZERO;
                    BigDecimal lineProf = (lineCalc != null) ? lineCalc.lineProfit() : BigDecimal.ZERO;

                    boolean isBackorder = item.getBackorderQty() != null
                            && item.getBackorderQty().compareTo(BigDecimal.ZERO) > 0;

                    List<QuotationComponentResponse> components = componentsMap.getOrDefault(
                            item.getQuotationItemId(),
                            Collections.emptyList()
                    );

                    QuotationItemResponse itemResp = QuotationItemResponse.builder()
                            .quotationItemId(item.getQuotationItemId())
                            .variantId(item.getVariantId())
                            .sku(item.getSku())
                            .variantName(safeSkuNames.getOrDefault(item.getVariantId(), null))
                            .unit(item.getUnit())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .discountRate(item.getDiscountRate())
                            .discountAmount(discountAmt)
                            .lineTotal(lineTot)
                            .backorderQty(item.getBackorderQty())
                            .isBackorder(isBackorder)
                            .leadTimeDays(item.getLeadTimeDays())
                            .sortOrder(item.getSortOrder())
                            .components(components)
                            .build();

                    rootItemResponses.add(itemResp);

                    if (includeOwnerData) {
                        itemCostResponses.add(QuotationItemCostResponse.builder()
                                .quotationItemId(item.getQuotationItemId())
                                .unitCost(item.getUnitCost())
                                .lineProfit(lineProf)
                                .build());
                    }
                }
            }
        }

        // 2. Financial summary
        QuotationSummaryResponse summary = (totals != null) ? toSummaryResponse(totals) : null;

        // 3. Approval Info
        QuotationApprovalInfo approval = QuotationApprovalInfo.builder()
                .requiresApproval(approvalResult != null ? approvalResult.requiresApproval() : null)
                .maxDiscountRate(approvalResult != null ? approvalResult.maxDiscountRate() : null)
                .staffDiscountLimit(approvalResult != null ? approvalResult.staffDiscountLimit() : null)
                .approvedBy(quotation.getApprovedBy())
                .approvedAt(quotation.getApprovedAt())
                .build();

        // 4. Owner data (chỉ điền khi includeOwnerData = true)
        QuotationOwnerData ownerData = null;
        if (includeOwnerData && totals != null) {
            ownerData = QuotationOwnerData.builder()
                    .grossProfit(totals.grossProfit())
                    .marginPercent(totals.marginPercent())
                    .marginPercentAtApproval(quotation.getMarginPercentAtApproval())
                    .approvalNote(quotation.getApprovalNote())
                    .itemCosts(itemCostResponses)
                    .build();
        }

        return QuotationResponse.builder()
                .quotationId(quotation.getQuotationId())
                .quotationCode(quotation.getQuotationCode())
                .revisionNo(quotation.getRevisionNo())
                .parentQuotationId(quotation.getParentQuotationId())
                .status(quotation.getStatus())
                .quotationDate(quotation.getQuotationDate())
                .validUntil(quotation.getValidUntil())
                .customerId(quotation.getCustomerId())
                .customerName(customerName)
                .projectName(quotation.getProjectName())
                .deliveryAddress(quotation.getDeliveryAddress())
                .note(quotation.getNote())
                .items(rootItemResponses)
                .summary(summary)
                .approval(approval)
                .sentAt(quotation.getSentAt())
                .pdfUrl(quotation.getPdfUrl())
                .acceptedAt(quotation.getAcceptedAt())
                .signedFileUrl(quotation.getSignedFileUrl())
                .lostAt(quotation.getLostAt())
                .lostReason(quotation.getLostReason())
                .createdBy(quotation.getCreatedBy())
                .ownerData(ownerData)
                .build();
    }
}
