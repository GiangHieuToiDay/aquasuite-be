package com.aquasuite.ims.service.sales.impl;

import com.aquasuite.ims.dto.sales.response.SalesOrderMilestoneDto;
import com.aquasuite.ims.dto.sales.response.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.dto.sales.response.SalesOrderItemSummaryDto;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogBundleSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.dto.crm.response.CustomerSummaryDto;
import com.aquasuite.ims.service.finance.CreditControlService;
import com.aquasuite.ims.dto.finance.command.CreditAuthorizationCommand;
import com.aquasuite.ims.dto.finance.response.CreditAuthorizationDecision;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.dto.iam.response.UserSummaryDto;
import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.dto.inventory.response.StockSummaryDto;
import com.aquasuite.ims.entity.sales.OnlinePayosPayment;
import com.aquasuite.ims.entity.sales.Order;
import com.aquasuite.ims.entity.sales.OrderItem;
import com.aquasuite.ims.entity.sales.OrderPaymentMilestone;
import com.aquasuite.ims.entity.sales.Quotation;
import com.aquasuite.ims.entity.sales.QuotationItem;
import com.aquasuite.ims.entity.sales.QuotationPaymentMilestone;
import com.aquasuite.ims.entity.sales.SalesInvoice;
import com.aquasuite.ims.entity.sales.SalesInvoiceItem;
import com.aquasuite.ims.enums.sales.FulfillmentMethod;
import com.aquasuite.ims.enums.sales.OrderChannel;
import com.aquasuite.ims.enums.sales.OrderStatus;
import com.aquasuite.ims.enums.sales.OrderType;
import com.aquasuite.ims.enums.sales.PaymentMethod;
import com.aquasuite.ims.enums.sales.PaymentStatus;
import com.aquasuite.ims.enums.sales.QuotationStatus;
import com.aquasuite.ims.exception.sales.SalesErrorCode;
import com.aquasuite.ims.integration.sales.PayosClient;
import com.aquasuite.ims.mapper.sales.SalesOrderMapper;
import com.aquasuite.ims.dto.sales.request.ConvertQuotationToOrderRequest;
import com.aquasuite.ims.dto.sales.request.CreateComboItemRequest;
import com.aquasuite.ims.dto.sales.request.CreateOrderItemRequest;
import com.aquasuite.ims.dto.sales.request.CreateSalesOrderRequest;
import com.aquasuite.ims.dto.sales.request.SalesOrderFilterRequest;
import com.aquasuite.ims.dto.sales.request.UpdateOrderFulfillmentRequest;
import com.aquasuite.ims.dto.sales.request.*;
import com.aquasuite.ims.dto.sales.response.OrderModificationRequestResponse;
import com.aquasuite.ims.dto.sales.response.CustomerOrderModificationRequestResponse;
import com.aquasuite.ims.dto.sales.response.PayosPaymentLinkResponse;
import com.aquasuite.ims.dto.sales.response.SalesOrderDetailResponse;
import com.aquasuite.ims.dto.sales.response.SalesOrderItemResponse;
import com.aquasuite.ims.dto.sales.response.SalesOrderMilestoneResponse;
import com.aquasuite.ims.dto.sales.response.SalesOrderSummaryResponse;
import com.aquasuite.ims.repository.sales.OnlinePayosPaymentRepository;
import com.aquasuite.ims.repository.sales.OrderItemRepository;
import com.aquasuite.ims.repository.sales.OrderPaymentMilestoneRepository;
import com.aquasuite.ims.repository.sales.OrderRepository;
import com.aquasuite.ims.repository.sales.QuotationItemRepository;
import com.aquasuite.ims.repository.sales.QuotationPaymentMilestoneRepository;
import com.aquasuite.ims.repository.sales.QuotationRepository;
import com.aquasuite.ims.repository.sales.SalesInvoiceItemRepository;
import com.aquasuite.ims.repository.sales.SalesInvoiceRepository;
import com.aquasuite.ims.service.sales.SalesOrderService;
import com.aquasuite.ims.service.sales.helper.OrderCodeGenerator;
import com.aquasuite.ims.exception.AppException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Tạo đơn tại quầy và cung cấp danh sách, chi tiết đơn hàng bán. */
@Slf4j
@Service
public class SalesOrderServiceImpl implements SalesOrderService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern PHONE_CHAR_PATTERN = Pattern.compile("^[0-9+\\s().-]+$");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesInvoiceItemRepository salesInvoiceItemRepository;
    private final CreditControlService creditControlApi;
    private final CatalogLookupService catalogApi;
    private final InventoryService inventoryApi;
    private final CustomerService crmApi;
    private final IamQueryService iamApi;
    private final SalesOrderMapper salesOrderMapper;
    private final OrderCodeGenerator orderCodeGenerator;
    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository quotationItemRepository;
    private final QuotationPaymentMilestoneRepository quotationPaymentMilestoneRepository;
    private final OrderPaymentMilestoneRepository orderPaymentMilestoneRepository;
    private final com.aquasuite.ims.service.sales.impl.OrderReservationServiceImpl orderStockReservationService;
    private final com.aquasuite.ims.service.finance.PaymentService orderReceivableApi;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final OrderModificationRequestServiceImpl modificationService;
    private final OnlinePayosPaymentRepository onlinePayosPaymentRepository;
    private final PayosClient payosClient;

    @org.springframework.beans.factory.annotation.Autowired
    public SalesOrderServiceImpl(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            SalesInvoiceRepository salesInvoiceRepository,
            SalesInvoiceItemRepository salesInvoiceItemRepository,
            CreditControlService creditControlApi,
            CatalogLookupService catalogApi,
            InventoryService inventoryApi,
            CustomerService crmApi,
            IamQueryService iamApi,
            SalesOrderMapper salesOrderMapper,
            OrderCodeGenerator orderCodeGenerator,
            QuotationRepository quotationRepository,
            QuotationItemRepository quotationItemRepository,
            QuotationPaymentMilestoneRepository quotationPaymentMilestoneRepository,
            OrderPaymentMilestoneRepository orderPaymentMilestoneRepository,
            com.aquasuite.ims.service.sales.impl.OrderReservationServiceImpl orderStockReservationService,
            com.aquasuite.ims.service.finance.PaymentService orderReceivableApi,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            @org.springframework.beans.factory.annotation.Autowired(required = false) OrderModificationRequestServiceImpl modificationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) OnlinePayosPaymentRepository onlinePayosPaymentRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PayosClient payosClient) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.salesInvoiceItemRepository = salesInvoiceItemRepository;
        this.creditControlApi = creditControlApi;
        this.catalogApi = catalogApi;
        this.inventoryApi = inventoryApi;
        this.crmApi = crmApi;
        this.iamApi = iamApi;
        this.salesOrderMapper = salesOrderMapper;
        this.orderCodeGenerator = orderCodeGenerator;
        this.quotationRepository = quotationRepository;
        this.quotationItemRepository = quotationItemRepository;
        this.quotationPaymentMilestoneRepository = quotationPaymentMilestoneRepository;
        this.orderPaymentMilestoneRepository = orderPaymentMilestoneRepository;
        this.orderStockReservationService = orderStockReservationService;
        this.orderReceivableApi = orderReceivableApi;
        this.eventPublisher = eventPublisher;
        this.modificationService = modificationService;
        this.onlinePayosPaymentRepository = onlinePayosPaymentRepository;
        this.payosClient = payosClient;
    }

    public SalesOrderServiceImpl(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            SalesInvoiceRepository salesInvoiceRepository,
            SalesInvoiceItemRepository salesInvoiceItemRepository,
            CreditControlService creditControlApi,
            CatalogLookupService catalogApi,
            InventoryService inventoryApi,
            CustomerService crmApi,
            IamQueryService iamApi,
            SalesOrderMapper salesOrderMapper,
            OrderCodeGenerator orderCodeGenerator,
            QuotationRepository quotationRepository,
            QuotationItemRepository quotationItemRepository,
            QuotationPaymentMilestoneRepository quotationPaymentMilestoneRepository,
            OrderPaymentMilestoneRepository orderPaymentMilestoneRepository,
            com.aquasuite.ims.service.sales.impl.OrderReservationServiceImpl orderStockReservationService,
            com.aquasuite.ims.service.finance.PaymentService orderReceivableApi,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            OrderModificationRequestServiceImpl modificationService) {
        this(orderRepository, orderItemRepository, salesInvoiceRepository, salesInvoiceItemRepository,
                creditControlApi, catalogApi, inventoryApi, crmApi, iamApi, salesOrderMapper,
                orderCodeGenerator, quotationRepository, quotationItemRepository,
                quotationPaymentMilestoneRepository, orderPaymentMilestoneRepository,
                orderStockReservationService, orderReceivableApi, eventPublisher, modificationService, null, null);
    }

    public SalesOrderServiceImpl(OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            SalesInvoiceRepository salesInvoiceRepository,
            SalesInvoiceItemRepository salesInvoiceItemRepository,
            CreditControlService creditControlApi,
            CatalogLookupService catalogApi,
            InventoryService inventoryApi,
            CustomerService crmApi,
            IamQueryService iamApi,
            SalesOrderMapper salesOrderMapper,
            OrderCodeGenerator orderCodeGenerator,
            QuotationRepository quotationRepository,
            QuotationItemRepository quotationItemRepository,
            QuotationPaymentMilestoneRepository quotationPaymentMilestoneRepository,
            OrderPaymentMilestoneRepository orderPaymentMilestoneRepository,
            com.aquasuite.ims.service.sales.impl.OrderReservationServiceImpl orderStockReservationService,
            com.aquasuite.ims.service.finance.PaymentService orderReceivableApi,
            org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this(orderRepository, orderItemRepository, salesInvoiceRepository, salesInvoiceItemRepository,
                creditControlApi, catalogApi, inventoryApi, crmApi, iamApi, salesOrderMapper,
                orderCodeGenerator, quotationRepository, quotationItemRepository,
                quotationPaymentMilestoneRepository, orderPaymentMilestoneRepository,
                orderStockReservationService, orderReceivableApi, eventPublisher, null, null, null);
    }

    public SalesOrderServiceImpl(OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            SalesInvoiceRepository salesInvoiceRepository,
            OrderPaymentMilestoneRepository orderPaymentMilestoneRepository,
            org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this(orderRepository, orderItemRepository, salesInvoiceRepository, null, null, null, null, null, null, null,
                null, null, null, null, orderPaymentMilestoneRepository, null, null, eventPublisher, null, null, null);
    }

    /**
     * Tạo đơn tại quầy: kiểm tra khách, tính tổng, kiểm tra mua nợ nếu cần,
     * giữ tồn kho và lưu đơn cùng hóa đơn cửa hàng trong một transaction.
     * 
     * @param request khách hàng, hàng hóa và thông tin đơn cần tạo
     * @return chi tiết đơn và mã hóa đơn cửa hàng
     * @throws AppException nếu dữ liệu, khách, hàng hóa hoặc điều kiện mua nợ không
     *                      hợp lệ
     */
    @Override
    @Transactional
    public SalesOrderDetailResponse createSalesOrder(CreateSalesOrderRequest request) {
        log.info("Processing POS order creation (UC-21): channel={}, fulfillment={}, paymentMethod={}",
                request.getOrderChannel(), request.getFulfillmentMethod(), request.getPaymentMethod());

        // 1. Kiểm tra danh sách mặt hàng
        boolean hasItems = request.getItems() != null && !request.getItems().isEmpty();
        boolean hasCombos = request.getCombos() != null && !request.getCombos().isEmpty();
        if (!hasItems && !hasCombos) {
            throw new AppException(SalesErrorCode.ORDER_ITEMS_EMPTY);
        }

        // 2. Định danh khách hàng (Mặc định Walk-in Customer nếu không cung cấp)
        CustomerSummaryDto customer = resolveCustomer(request);

        // 3. Nhân viên bán hàng hiện tại
        UUID salesStaffId = resolveCurrentStaffId();
        if (salesStaffId == null) {
            throw new AppException(SalesErrorCode.ACTOR_REQUIRED);
        }
        if (request.getPaymentMethod() == null || request.getPaymentMethod() == PaymentMethod.CARD) {
            throw new AppException(SalesErrorCode.UNSUPPORTED_PAYMENT_METHOD);
        }

        // Dùng cùng khóa theo khách với thao tác chặn mua hàng và kiểm tra công nợ.
        creditControlApi.requireCustomerOrderAllowed(customer.customerId());

        // 5. Chuẩn bị đơn hàng
        UUID orderId = UUID.randomUUID();
        String orderCode = orderCodeGenerator.generateOrderCode();
        List<OrderItem> orderItemsToSave = new ArrayList<>();
        BigDecimal calculatedSubtotal = BigDecimal.ZERO;

        // 6. Kiểm tra & chuẩn bị các biến thể SKU lẻ
        if (hasItems) {
            for (CreateOrderItemRequest itemReq : request.getItems()) {
                CatalogSkuSummaryDto skuDto = catalogApi.getSkuSummary(itemReq.getVariantId())
                        .orElseThrow(() -> new AppException(SalesErrorCode.SKU_NOT_FOUND, itemReq.getVariantId()));

                if ("INACTIVE".equalsIgnoreCase(skuDto.status())) {
                    throw new AppException(SalesErrorCode.SKU_NOT_ACTIVE, skuDto.sku());
                }

                // Kiểm tra tồn kho khả dụng (available_qty = on_hand_qty - reserved_qty)
                boolean isStockAvailable = request.isReserveStockAfterDeposit()
                        || inventoryApi.checkAvailability(skuDto.variantId(), itemReq.getQuantity());
                if (!isStockAvailable) {
                    log.warn("Insufficient stock for variant SKU={}, requiredQty={}", skuDto.sku(),
                            itemReq.getQuantity());
                    throw new AppException(SalesErrorCode.INSUFFICIENT_STOCK,
                            skuDto.sku() + " (" + skuDto.variantName() + ")");
                }

                BigDecimal unitPrice = skuDto.sellingPrice() != null ? skuDto.sellingPrice() : BigDecimal.ZERO;
                BigDecimal itemDiscount = itemReq.getDiscountAmount() != null ? itemReq.getDiscountAmount()
                        : BigDecimal.ZERO;
                BigDecimal lineTotal = unitPrice.multiply(itemReq.getQuantity()).subtract(itemDiscount);
                if (lineTotal.compareTo(BigDecimal.ZERO) < 0) {
                    lineTotal = BigDecimal.ZERO;
                }

                OrderItem orderItem = OrderItem.builder()
                        .orderItemId(UUID.randomUUID())
                        .orderId(orderId)
                        .variantId(skuDto.variantId())
                        .sku(skuDto.sku())
                        .productName(skuDto.productName() != null ? skuDto.productName() : skuDto.variantName())
                        .variantName(skuDto.variantName())
                        .quantity(itemReq.getQuantity())
                        .unitPrice(unitPrice)
                        .discountAmount(itemDiscount)
                        .lineTotal(lineTotal)
                        .build();

                orderItemsToSave.add(orderItem);
                calculatedSubtotal = calculatedSubtotal.add(lineTotal);
            }
        }

        // 7. Kiểm tra & chuẩn bị các gói Combo
        if (hasCombos) {
            for (CreateComboItemRequest comboReq : request.getCombos()) {
                CatalogBundleSummaryDto bundleDto = catalogApi.getBundleSummary(comboReq.getComboId())
                        .orElseThrow(() -> new AppException(SalesErrorCode.COMBO_NOT_FOUND, comboReq.getComboId()));

                int comboQty = Math.max(comboReq.getQuantity(), 1);
                UUID comboGroupId = UUID.randomUUID();
                if (bundleDto.packagePrice() == null || bundleDto.packagePrice().signum() <= 0
                        || bundleDto.items() == null || bundleDto.items().isEmpty()) {
                    throw new AppException(com.aquasuite.ims.exception.CommonErrorCode.INVALID_REQUEST_PARAM);
                }
                List<OrderItem> comboItems = new ArrayList<>();

                // Kiểm tra tồn kho khả dụng cho toàn bộ component SKU trong gói combo
                if (bundleDto.items() != null) {
                    for (CatalogBundleSummaryDto.CatalogBundleItemDto comp : bundleDto.items()) {
                        BigDecimal requiredQty = BigDecimal.valueOf((long) comp.quantity() * comboQty);
                        boolean isCompAvailable = request.isReserveStockAfterDeposit()
                                || inventoryApi.checkAvailability(comp.variantId(), requiredQty);
                        if (!isCompAvailable) {
                            log.warn("Insufficient stock for combo component SKU={}, combo={}", comp.sku(),
                                    bundleDto.bundleName());
                            throw new AppException(SalesErrorCode.INSUFFICIENT_STOCK,
                                    comp.sku() + " (thuộc combo " + bundleDto.bundleName() + ")");
                        }
                    }

                    // Ghi nhận snapshot order items cho từng component
                    for (CatalogBundleSummaryDto.CatalogBundleItemDto comp : bundleDto.items()) {
                        BigDecimal totalCompQty = BigDecimal.valueOf((long) comp.quantity() * comboQty);
                        BigDecimal compUnitPrice = comp.sellingPrice() != null ? comp.sellingPrice() : BigDecimal.ZERO;
                        BigDecimal compLineTotal = compUnitPrice.multiply(totalCompQty);

                        OrderItem comboItem = OrderItem.builder()
                                .orderItemId(UUID.randomUUID())
                                .orderId(orderId)
                                .comboId(bundleDto.bundleId())
                                .comboGroupId(comboGroupId)
                                .comboQuantity(BigDecimal.valueOf(comboQty))
                                .comboSku(bundleDto.bundleCode())
                                .comboUnitPrice(bundleDto.packagePrice())
                                .variantId(comp.variantId())
                                .sku(comp.sku())
                                .productName(bundleDto.bundleName())
                                .variantName(comp.variantName())
                                .quantity(totalCompQty)
                                .unitPrice(compUnitPrice)
                                .discountAmount(BigDecimal.ZERO)
                                .lineTotal(compLineTotal)
                                .build();

                        comboItems.add(comboItem);
                    }
                    Map<UUID, BigDecimal> weights = comboItems.stream().collect(java.util.stream.Collectors.toMap(
                            OrderItem::getOrderItemId, OrderItem::getLineTotal));
                    var allocated = com.aquasuite.ims.service.sales.helper.QuotationComboAllocator
                            .allocate(bundleDto.packagePrice().multiply(BigDecimal.valueOf(comboQty)), weights);
                    for (OrderItem item : comboItems) {
                        BigDecimal net = allocated.get(item.getOrderItemId());
                        item.setDiscountAmount(item.getLineTotal().subtract(net));
                        item.setLineTotal(net);
                        orderItemsToSave.add(item);
                        calculatedSubtotal = calculatedSubtotal.add(net);
                    }
                }
            }
        }

        // 7. Tính tổng tiền và chiết khấu
        BigDecimal orderDiscount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal finalTotal = calculatedSubtotal.subtract(orderDiscount);
        if (finalTotal.compareTo(BigDecimal.ZERO) < 0) {
            finalTotal = BigDecimal.ZERO;
        }
        OrderChannel channel = request.getOrderChannel() == null || request.getOrderChannel() == OrderChannel.POS
                ? OrderChannel.COUNTER
                : request.getOrderChannel();

        // 8. Kiểm tra hạn mức mua nợ (đồng bộ trong cùng transaction tạo đơn)
        if (request.getPaymentMethod() == PaymentMethod.CREDIT) {
            CreditAuthorizationDecision decision = creditControlApi.authorize(new CreditAuthorizationCommand(
                    customer.customerId(), orderId, channel.name(), finalTotal));
            if (decision == null || !decision.approved()) {
                log.warn("Credit order denied for customer {}: reason={}",
                        customer.customerId(), decision != null ? decision.reasonCode() : "NULL");
                throw new AppException(SalesErrorCode.CREDIT_LIMIT_EXCEEDED);
            }
        }

        // 9. Khởi tạo trạng thái đơn hàng và trạng thái thanh toán ban đầu
        OrderStatus resolvedOrderStatus = OrderStatus.PROCESSING;
        PaymentStatus resolvedPaymentStatus = PaymentStatus.UNPAID;
        LocalDate dueDate = null; // Hạn nợ theo đợt được tính khi có hóa đơn điện tử tương ứng.

        // 10. Đặt giữ tồn kho (reserved_qty) cho các mặt hàng trong đơn
        if (!request.isReserveStockAfterDeposit()) {
            for (OrderItem item : orderItemsToSave) {
                inventoryApi.reserveStock(item.getVariantId(), item.getQuantity());
            }
        }

        // 11. Lưu Order Entity & Order Items
        OffsetDateTime now = OffsetDateTime.now();
        Order order = Order.builder()
                .orderId(orderId)
                .orderCode(orderCode)
                .customerId(customer.customerId())
                .salesStaffId(salesStaffId)
                .orderType(request.getOrderType() != null ? request.getOrderType().name() : OrderType.RETAIL.name())
                .orderChannel(channel.name())
                .orderStatus(resolvedOrderStatus.name())
                .paymentStatus(resolvedPaymentStatus.name())
                .orderDate(now)
                .dueDate(dueDate)
                .subtotal(calculatedSubtotal)
                .discountAmount(orderDiscount)
                .totalAmount(finalTotal)
                .deliveryAddress(request.getDeliveryAddress())
                .reserveStockAfterDeposit(request.isReserveStockAfterDeposit())
                .stagedDelivery(false)
                .note(request.getNote())
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Liên kết qua ID nên ghi các bản ghi cha trước khi ghi dòng phụ thuộc.
        Order savedOrder = orderRepository.saveAndFlush(order);
        List<OrderItem> savedItems = orderItemRepository.saveAllAndFlush(orderItemsToSave);
        UUID storeInvoiceId = UUID.randomUUID();
        salesInvoiceRepository.saveAndFlush(SalesInvoice.builder()
                .invoiceId(storeInvoiceId)
                .invoiceCode("STORE-" + orderId)
                .orderId(orderId)
                .customerId(customer.customerId())
                .invoiceType("STORE")
                .batchNo(1)
                .batchName("Hóa đơn bán lẻ")
                .issueDate(now)
                .subtotal(calculatedSubtotal)
                .discountAmount(orderDiscount)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(finalTotal)
                .status("DRAFT")
                .createdBy(salesStaffId)
                .createdAt(now)
                .build());
        salesInvoiceItemRepository.saveAll(savedItems.stream().map(item -> SalesInvoiceItem.builder()
                .invoiceItemId(UUID.randomUUID())
                .invoiceId(storeInvoiceId)
                .orderItemId(item.getOrderItemId())
                .variantId(item.getVariantId())
                .description(item.getProductName() + " - " + item.getVariantName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .discountAmount(item.getDiscountAmount())
                .taxRate(BigDecimal.ZERO)
                .lineTotal(item.getLineTotal())
                .build()).toList());

        log.info("Sales Order {} committed successfully (UC-21): totalAmount={}, status={}, paymentStatus={}",
                orderCode, finalTotal, resolvedOrderStatus, resolvedPaymentStatus);

        List<SalesOrderItemResponse> itemResponses = salesOrderMapper.toItemResponseList(savedItems);
        String staffName = getStaffName(salesStaffId);

        String cName = customer != null ? customer.customerName() : null;
        String finalCustomerName = (cName != null && !cName.isBlank()
                && !"Walk-in Retail Customer".equalsIgnoreCase(cName.trim()))
                        ? cName.trim()
                        : "Khách lẻ tại quầy";
        String cPhone = customer != null ? customer.phone() : null;
        String finalCustomerPhone = (cPhone != null && !cPhone.isBlank() && !"0000000000".equals(cPhone.trim()))
                ? cPhone.trim()
                : null;

        SalesOrderDetailResponse response = salesOrderMapper.toDetailResponse(
                savedOrder,
                itemResponses,
                finalCustomerName,
                finalCustomerPhone,
                staffName);
        response.setStoreInvoiceId(storeInvoiceId);
        response.setTotalInvoiceCount(1);
        response.setDraftInvoiceCount(1);
        response.setIssuedInvoiceCount(0);
        populateDisplayItems(response, savedOrder, savedItems, itemResponses);
        return response;
    }

    @Override
    @Transactional
    public SalesOrderDetailResponse convertQuotationToOrder(UUID quotationId, ConvertQuotationToOrderRequest request) {
        log.info("Processing quotation conversion to project sales order (UC-28 / UC-073) for quotationId={}",
                quotationId);

        if (quotationId == null) {
            throw new AppException(SalesErrorCode.QUOTATION_NOT_FOUND, "null");
        }
        // 1. Kiểm tra tồn tại của báo giá
        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new AppException(SalesErrorCode.QUOTATION_NOT_FOUND, quotationId));

        // 2. Kiểm tra trạng thái báo giá: Bắt buộc là ACCEPTED hoặc APPROVED
        if (quotation.getStatus() != QuotationStatus.ACCEPTED && quotation.getStatus() != QuotationStatus.APPROVED) {
            log.warn("Quotation {} has status {}, cannot convert to order", quotation.getQuotationCode(),
                    quotation.getStatus());
            throw new AppException(SalesErrorCode.QUOTATION_NOT_ACCEPTED_OR_APPROVED, quotation.getStatus());
        }

        // 3. Kiểm tra xem báo giá đã được chuyển đổi thành đơn hàng trước đó chưa
        Optional<Order> existingOrder = orderRepository.findByQuotationId(quotationId);
        if (existingOrder.isPresent()) {
            log.warn("Quotation {} has already been converted to order {}", quotation.getQuotationCode(),
                    existingOrder.get().getOrderCode());
            throw new AppException(SalesErrorCode.ALREADY_CONVERTED_TO_ORDER, quotation.getQuotationCode(),
                    existingOrder.get().getOrderCode());
        }

        // 4. Lấy danh sách sản phẩm trong báo giá
        List<QuotationItem> quotationItems = quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId);
        if (quotationItems == null || quotationItems.isEmpty()) {
            throw new AppException(SalesErrorCode.ORDER_ITEMS_EMPTY);
        }

        // 5. Định danh khách hàng
        CustomerSummaryDto customer = crmApi.getCustomerById(quotation.getCustomerId())
                .orElse(null);
        String customerName = customer != null ? customer.customerName() : "Khách hàng dự án";
        String customerPhone = customer != null ? customer.phone() : null;

        // 6. Nhân viên bán hàng phụ trách (ưu tiên user hiện tại, nếu không lấy người
        // tạo báo giá)
        UUID currentStaffId = resolveCurrentStaffId();
        if (currentStaffId == null) {
            currentStaffId = quotation.getCreatedBy();
        }

        // 7. Tạo mã và chuẩn bị các dòng đơn hàng từ quotation items
        UUID orderId = UUID.randomUUID();
        String orderCode = orderCodeGenerator.generateOrderCode();
        List<OrderItem> orderItemsToSave = new ArrayList<>();
        BigDecimal calculatedSubtotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        boolean quotationRequiresDeposit = quotation.getDepositPercent() != null
                && quotation.getDepositPercent().signum() > 0;
        boolean reserveAfterDeposit = quotationRequiresDeposit
                || (request != null && request.isReserveStockAfterDeposit());

        var comboNet = com.aquasuite.ims.service.sales.helper.QuotationComboAllocator.allocate(quotationItems);
        Map<UUID, QuotationItem> comboRoots = quotationItems.stream()
                .filter(item -> item.getComboId() != null && item.getVariantId() == null)
                .collect(java.util.stream.Collectors.toMap(QuotationItem::getQuotationItemId, item -> item));
        for (QuotationItem qItem : quotationItems) {
            if (comboRoots.containsKey(qItem.getQuotationItemId()))
                continue;
            CatalogSkuSummaryDto skuDto = catalogApi.getSkuSummary(qItem.getVariantId())
                    .orElseThrow(() -> new AppException(SalesErrorCode.SKU_NOT_FOUND, qItem.getVariantId()));

            if ("INACTIVE".equalsIgnoreCase(skuDto.status())) {
                throw new AppException(SalesErrorCode.SKU_NOT_ACTIVE, skuDto.sku());
            }

            BigDecimal qty = qItem.getQuantity() != null ? qItem.getQuantity() : BigDecimal.ONE;
            BigDecimal unitPrice = qItem.getUnitPrice() != null ? qItem.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal grossLine = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

            BigDecimal lineDiscount = BigDecimal.ZERO;
            if (qItem.getDiscountRate() != null && qItem.getDiscountRate().compareTo(BigDecimal.ZERO) > 0) {
                lineDiscount = grossLine.multiply(qItem.getDiscountRate())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }

            BigDecimal lineTotal = grossLine.subtract(lineDiscount);
            QuotationItem comboRoot = comboRoots.get(qItem.getParentItemId());
            if (comboRoot != null) {
                lineTotal = comboNet.get(qItem.getQuotationItemId());
                lineDiscount = grossLine.subtract(lineTotal);
            }
            if (lineTotal.compareTo(BigDecimal.ZERO) < 0) {
                lineTotal = BigDecimal.ZERO;
            }

            OrderItem orderItem = OrderItem.builder()
                    .orderItemId(UUID.randomUUID())
                    .orderId(orderId)
                    .variantId(skuDto.variantId())
                    .comboId(comboRoot == null ? null : comboRoot.getComboId())
                    .comboGroupId(comboRoot == null ? null : comboRoot.getQuotationItemId())
                    .comboQuantity(comboRoot == null ? null : comboRoot.getQuantity())
                    .comboSku(comboRoot == null ? null : comboRoot.getSku())
                    .comboUnitPrice(comboRoot == null ? null : comboRoot.getUnitPrice())
                    .sku(skuDto.sku())
                    .productName(comboRoot != null ? comboRoot.getComboName()
                            : skuDto.productName() != null ? skuDto.productName() : skuDto.variantName())
                    .variantName(skuDto.variantName())
                    .quantity(qty)
                    .unitPrice(unitPrice)
                    .discountAmount(lineDiscount)
                    .lineTotal(lineTotal)
                    .build();

            orderItemsToSave.add(orderItem);
            calculatedSubtotal = calculatedSubtotal.add(grossLine);
            totalDiscount = totalDiscount.add(lineDiscount);

            // Giữ chỗ tồn kho khả dụng nếu kho đang có sẵn hàng (UC-086)
            if (!reserveAfterDeposit) {
                try {
                    Optional<StockSummaryDto> stockOpt = inventoryApi.getStockSummary(skuDto.variantId());
                    if (stockOpt.isPresent() && stockOpt.get().availableQty() != null) {
                        BigDecimal avail = stockOpt.get().availableQty();
                        if (avail.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal toReserve = qty.min(avail);
                            inventoryApi.reserveStock(skuDto.variantId(), toReserve);
                            log.info("Reserved stock {}/{} for variant SKU {} on project order {}",
                                    toReserve, qty, skuDto.sku(), orderCode);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Stock reservation skipped for variant {}: {}", skuDto.variantId(), ex.getMessage());
                }
            }
        }

        // 8. Tính tổng tiền đơn hàng
        // Kế thừa tổng tiền báo giá (đã gồm VAT/phí vận chuyển nếu có), hoặc tính từ
        // subtotal - discount
        BigDecimal finalTotal = quotation.getTotalAmount() != null
                && quotation.getTotalAmount().compareTo(BigDecimal.ZERO) > 0
                        ? quotation.getTotalAmount()
                        : calculatedSubtotal.subtract(totalDiscount);
        if (finalTotal.compareTo(BigDecimal.ZERO) < 0) {
            finalTotal = BigDecimal.ZERO;
        }

        // 9. Chuẩn bị địa chỉ, hạn thanh toán và ghi chú
        String deliveryAddress = (request != null && request.getDeliveryAddress() != null
                && !request.getDeliveryAddress().isBlank())
                        ? request.getDeliveryAddress().trim()
                        : quotation.getDeliveryAddress();

        String userNote = (request != null && request.getNote() != null && !request.getNote().isBlank())
                ? request.getNote().trim()
                : quotation.getNote();

        String combinedNote = String.format("[Báo giá: %s (rev.%d)%s]",
                quotation.getQuotationCode(),
                quotation.getRevisionNo(),
                quotation.getProjectName() != null && !quotation.getProjectName().isBlank()
                        ? " - " + quotation.getProjectName()
                        : "");

        if (quotation.getDepositPercent() != null && quotation.getDepositPercent().compareTo(BigDecimal.ZERO) > 0) {
            combinedNote += String.format(" [Cọc: %s%%]",
                    quotation.getDepositPercent().stripTrailingZeros().toPlainString());
        }
        if (quotation.getCreditTermDays() != null && quotation.getCreditTermDays() > 0) {
            combinedNote += String.format(" [Công nợ: %d ngày]", quotation.getCreditTermDays());
        }

        if (userNote != null && !userNote.isBlank()) {
            combinedNote += " " + userNote;
        }

        LocalDate dueDate = (request != null && request.getDueDate() != null)
                ? request.getDueDate()
                : (quotation.getCreditTermDays() != null && quotation.getCreditTermDays() > 0
                        ? LocalDate.now().plusDays(quotation.getCreditTermDays())
                        : (quotation.getValidUntil() != null ? quotation.getValidUntil()
                                : LocalDate.now().plusDays(30)));

        OffsetDateTime now = OffsetDateTime.now();

        BigDecimal depositPercent = quotation.getDepositPercent();
        BigDecimal depositAmount = quotation.getDepositAmount();
        if (depositAmount == null && depositPercent != null && depositPercent.compareTo(BigDecimal.ZERO) > 0) {
            depositAmount = finalTotal.multiply(depositPercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        BigDecimal remainingDebtAmount = quotation.getRemainingDebtAmount();
        if (remainingDebtAmount == null && depositAmount != null) {
            remainingDebtAmount = finalTotal.subtract(depositAmount).max(BigDecimal.ZERO);
        }

        // 9.1. Kiểm tra công nợ và quyết định trạng thái Order (DRAFT vs PROCESSING)
        boolean hasDebtTerms = quotation.getCreditTermDays() != null
                && quotation.getCreditTermDays() > 0
                && remainingDebtAmount != null
                && remainingDebtAmount.compareTo(BigDecimal.ZERO) > 0;

        OrderStatus initialOrderStatus = OrderStatus.PROCESSING;
        UUID creditRequestId = null;

        if (hasDebtTerms) {
            // Kiểm tra khách hàng không bị khóa giao dịch
            creditControlApi.requireCustomerOrderAllowed(quotation.getCustomerId());

            // Kiểm tra khả dụng công nợ cho đơn hàng dự án
            CreditAuthorizationDecision decision = creditControlApi.authorize(new CreditAuthorizationCommand(
                    quotation.getCustomerId(),
                    orderId,
                    OrderChannel.DIRECT_SALES.name(),
                    remainingDebtAmount));

            boolean creditApproved = decision != null && decision.approved();

            if (!creditApproved) {
                // Khách hàng không đủ hạn mức hoặc chưa có hồ sơ công nợ
                if (request != null && Boolean.TRUE.equals(request.getRequestCreditIncrease())) {
                    // Tạo yêu cầu cấp/tăng hạn mức công nợ trình Chủ cửa hàng duyệt (UC-24)
                    String reason = request.getCreditIncreaseReason();
                    if (reason == null || reason.isBlank()) {
                        reason = String.format("Đề xuất cấp hạn mức cho Đơn hàng dự án %s (Báo giá %s)",
                                orderCode, quotation.getQuotationCode());
                    }

                    var debtProfile = crmApi.getDebtProfile(quotation.getCustomerId()).orElse(null);
                    BigDecimal currentLimit = debtProfile != null && debtProfile.creditLimit() != null
                            ? debtProfile.creditLimit()
                            : BigDecimal.ZERO;
                    BigDecimal currentAvailable = decision != null && decision.availableCredit() != null
                            ? decision.availableCredit()
                            : BigDecimal.ZERO;
                    BigDecimal currentOutstanding = currentLimit.subtract(currentAvailable).max(BigDecimal.ZERO);

                    BigDecimal proposedLimit = currentOutstanding.add(remainingDebtAmount);
                    if (proposedLimit.compareTo(new BigDecimal("500000000.00")) > 0) {
                        proposedLimit = new BigDecimal("500000000.00");
                    }
                    if (proposedLimit.compareTo(BigDecimal.ZERO) <= 0) {
                        proposedLimit = remainingDebtAmount;
                    }

                    creditRequestId = crmApi.submitCreditProfileRequestFromQuotation(
                            quotation.getCustomerId(),
                            proposedLimit,
                            quotation.getCreditTermDays(),
                            reason,
                            currentStaffId);

                    initialOrderStatus = OrderStatus.DRAFT;
                    combinedNote += " [Chờ Chủ duyệt tăng hạn mức công nợ]";
                    log.info("Quotation {} converted to DRAFT Order {} waiting for credit approval (requestId={})",
                            quotation.getQuotationCode(), orderCode, creditRequestId);
                } else {
                    // Không yêu cầu tăng hạn mức -> Báo lỗi hạn mức không đủ
                    log.warn("Credit order rejected for customer {}: reason={}",
                            quotation.getCustomerId(), decision != null ? decision.reasonCode() : "LIMIT_EXCEEDED");
                    throw new AppException(SalesErrorCode.CREDIT_LIMIT_EXCEEDED);
                }
            }
        } else {
            // Đơn không nợ: Kiểm tra khách không bị khóa
            creditControlApi.requireCustomerOrderAllowed(quotation.getCustomerId());
        }

        // 10. Khởi tạo và lưu Order kèm toàn bộ thông tin snapshot từ Báo giá
        boolean stagedDelivery = request != null && request.getBatches() != null && request.getBatches().size() > 1;
        Order order = Order.builder()
                .orderId(orderId)
                .orderCode(orderCode)
                .customerId(quotation.getCustomerId())
                .quotationId(quotationId)
                .quotationCode(quotation.getQuotationCode())
                .projectName(quotation.getProjectName())
                .deliverySchedule(quotation.getDeliverySchedule())
                .vatRate(quotation.getVatRate())
                .shippingFee(quotation.getShippingFee())
                .depositPercent(depositPercent)
                .depositAmount(depositAmount)
                .reserveStockAfterDeposit(reserveAfterDeposit)
                .stagedDelivery(stagedDelivery)
                .creditTermDays(quotation.getCreditTermDays())
                .remainingDebtAmount(remainingDebtAmount)
                .debtCondition(quotation.getDebtCondition())
                .salesStaffId(currentStaffId)
                .orderType(OrderType.PROJECT.name())
                .orderChannel(OrderChannel.DIRECT_SALES.name())
                .orderStatus(initialOrderStatus.name())
                .paymentStatus(PaymentStatus.UNPAID.name())
                .creditRequestId(creditRequestId)
                .orderDate(now)
                .dueDate(dueDate)
                .subtotal(calculatedSubtotal)
                .discountAmount(totalDiscount)
                .totalAmount(finalTotal)
                .deliveryAddress(deliveryAddress)
                .note(combinedNote)
                .createdAt(now)
                .updatedAt(now)
                .build();

        com.aquasuite.ims.service.sales.helper.SalesInvoiceDraftInitializer
                .validateBatches(orderItemsToSave, request != null ? request.getBatches() : null);

        Order savedOrder = orderRepository.save(order);
        List<OrderItem> savedItems = orderItemRepository.saveAll(orderItemsToSave);

        // Khởi tạo 1 hoặc nhiều hóa đơn PROJECT ở trạng thái DRAFT theo các đợt giao
        List<SalesInvoice> initializedInvoices = com.aquasuite.ims.service.sales.helper.SalesInvoiceDraftInitializer
                .initializeForProjectOrder(
                        savedOrder != null ? savedOrder : order,
                        savedItems != null && !savedItems.isEmpty() ? savedItems : orderItemsToSave,
                        request != null ? request.getBatches() : null,
                        currentStaffId,
                        salesInvoiceRepository,
                        salesInvoiceItemRepository);

        // 10.1. Snapshot các đợt thanh toán (milestones) từ Báo giá sang Đơn hàng
        List<QuotationPaymentMilestone> quotationMilestones = quotationPaymentMilestoneRepository != null
                ? quotationPaymentMilestoneRepository.findByQuotationIdOrderBySortOrderAsc(quotationId)
                : List.of();
        List<OrderPaymentMilestone> savedMilestones = new ArrayList<>();
        if (quotationMilestones != null && !quotationMilestones.isEmpty()) {
            List<OrderPaymentMilestone> milestonesToSave = new ArrayList<>();
            int idx = 1;
            for (QuotationPaymentMilestone qm : quotationMilestones) {
                BigDecimal pct = qm.getPercent() != null ? qm.getPercent() : BigDecimal.ZERO;
                BigDecimal milestoneAmount = finalTotal.multiply(pct)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                milestonesToSave.add(OrderPaymentMilestone.builder()
                        .orderId(orderId)
                        .stageName(qm.getStageName())
                        .percent(pct)
                        .amount(milestoneAmount)
                        .sortOrder(qm.getSortOrder() != null ? qm.getSortOrder() : idx)
                        .build());
                idx++;
            }
            if (orderPaymentMilestoneRepository != null) {
                savedMilestones = orderPaymentMilestoneRepository.saveAll(milestonesToSave);
                if (savedMilestones == null) {
                    savedMilestones = milestonesToSave;
                }
            } else {
                savedMilestones = milestonesToSave;
            }
        }

        if (quotationRequiresDeposit) {
            orderStockReservationService.registerConvertedOrder(savedOrder, savedItems, currentStaffId);
        }

        // 11. Cập nhật báo giá sang ACCEPTED nếu trước đó đang là APPROVED
        if (quotation.getStatus() == QuotationStatus.APPROVED) {
            quotation.setStatus(QuotationStatus.ACCEPTED);
            if (quotation.getAcceptedAt() == null) {
                quotation.setAcceptedAt(now);
            }
            quotationRepository.save(quotation);
        }

        log.info("Quotation {} converted to Project Order {} successfully (UC-28 / UC-073): totalAmount={}",
                quotation.getQuotationCode(), orderCode, finalTotal);

        List<SalesOrderItemResponse> itemResponses = salesOrderMapper.toItemResponseList(savedItems);
        List<SalesOrderMilestoneResponse> milestoneResponses = salesOrderMapper
                .toMilestoneResponseList(savedMilestones);
        String staffName = getStaffName(currentStaffId);

        SalesOrderDetailResponse response = salesOrderMapper.toDetailResponse(
                savedOrder,
                itemResponses,
                customerName,
                customerPhone,
                staffName);
        response.setMilestones(milestoneResponses != null ? milestoneResponses : new ArrayList<>());
        applyInvoiceCounts(response, initializedInvoices);
        populateDisplayItems(response, savedOrder, savedItems, itemResponses);
        return response;
    }

    /**
     * Lấy danh sách đơn theo bộ lọc và thông tin phân trang.
     * 
     * @param filter   điều kiện lọc đơn
     * @param pageable trang và số lượng kết quả
     * @return trang tóm tắt đơn hàng
     */
    @Override
    @Transactional(readOnly = true)
    public Page<SalesOrderSummaryResponse> getSalesOrders(SalesOrderFilterRequest filter, Pageable pageable) {
        if (filter != null) {
            com.aquasuite.ims.util.PageableUtils.validateDateRange(filter.getFromDate(), filter.getToDate());
        }
        Pageable safePageable = com.aquasuite.ims.util.PageableUtils.sanitizePageable(
                pageable,
                java.util.Set.of("orderDate", "createdAt", "updatedAt", "orderCode", "totalAmount", "orderStatus", "paymentStatus", "dueDate"));
        Specification<Order> spec = buildFilterSpecification(filter);
        var payments = new java.util.HashMap<UUID, com.aquasuite.ims.dto.finance.response.OrderReceivableView>();
        if (filter != null && filter.isDepositReservationOnly()) {
            spec = spec.and(com.aquasuite.ims.repository.sales.spec.DepositReservationOrderSpecification.eligible());
            // shortcut: per-order Finance reads; introduce a batch public query if
            // candidate volume grows large.
            for (Order candidate : orderRepository.findAll(spec)) {
                orderReceivableApi.getOrderReceivable(candidate.getOrderId())
                        .filter(payment -> payment.depositRequired() != null && payment.depositRequired().signum() > 0)
                        .ifPresent(payment -> payments.put(candidate.getOrderId(), payment));
            }
            spec = spec.and((root, query, cb) -> root.get("orderId").in(payments.keySet()));
        }
        Page<Order> orderPage = orderRepository.findAll(spec, safePageable);
        Map<UUID, CustomerSummaryDto> customerMap = loadCustomerSummaries(orderPage.getContent());

        return orderPage.map(order -> {
            int itemCount = orderItemRepository.findByOrderId(order.getOrderId()).size();
            CustomerSummaryDto customer = resolveCustomerSummary(customerMap, order.getCustomerId());
            return buildSummaryResponse(order, itemCount, customer, payments.get(order.getOrderId()));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesOrderSummaryResponse> getMyCustomerOrders() {
        UUID userId = iamApi.getCurrentUserId();
        if (userId == null) {
            return List.of();
        }
        Optional<CustomerSummaryDto> customerOpt = crmApi.getCustomerByUserId(userId);
        if (customerOpt.isEmpty()) {
            return List.of();
        }
        CustomerSummaryDto customer = customerOpt.get();
        List<Order> orders = orderRepository.findByCustomerIdOrderByOrderDateDesc(customer.customerId());
        return orders.stream().map(order -> {
            int itemCount = orderItemRepository.findByOrderId(order.getOrderId()).size();
            com.aquasuite.ims.dto.finance.response.OrderReceivableView payment = null;
            if (orderReceivableApi != null && !OrderStatus.CANCELLED.name().equalsIgnoreCase(order.getOrderStatus())) {
                payment = orderReceivableApi.getOrderReceivable(order.getOrderId())
                        .filter(p -> p.depositRequired() != null && p.depositRequired().signum() > 0)
                        .orElse(null);
            }
            return buildSummaryResponse(order, itemCount, customer, payment);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SalesOrderDetailResponse getMyCustomerOrderDetail(UUID orderId) {
        UUID userId = iamApi.getCurrentUserId();
        CustomerSummaryDto customer = userId == null ? null
                : crmApi.getCustomerByUserId(userId).orElse(null);
        if (customer == null) {
            throw new AppException(SalesErrorCode.ORDER_NOT_FOUND);
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));
        if (order.getCustomerId() == null || !customer.customerId().equals(order.getCustomerId())) {
            throw new AppException(SalesErrorCode.ORDER_NOT_FOUND);
        }
        return buildOrderDetail(order);
    }

    private SalesOrderSummaryResponse buildSummaryResponse(
            Order order,
            int itemCount,
            CustomerSummaryDto customer,
            com.aquasuite.ims.dto.finance.response.OrderReceivableView payment) {
        String customerName = formatCustomerName(customer);
        String customerPhone = formatCustomerPhone(customer);
        String staffName = getStaffName(order.getSalesStaffId());
        var response = salesOrderMapper.toSummaryResponse(
                order,
                itemCount,
                customerName,
                customerPhone,
                staffName);
        if (payment != null) {
            response.setPayment(payment);
            response.setDepositStatus(payment.depositSatisfied() ? "SATISFIED" : "AWAITING_DEPOSIT");
        }
        return response;
    }

    /**
     * Lấy thông tin đơn, mặt hàng và hóa đơn cửa hàng nếu có.
     * 
     * @param orderId mã đơn cần xem
     * @return chi tiết đơn hàng
     * @throws AppException nếu không tìm thấy đơn
     */
    @Override
    @Transactional(readOnly = true)
    public SalesOrderDetailResponse getSalesOrderDetail(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));
        return buildOrderDetail(order);
    }

    private SalesOrderDetailResponse buildOrderDetail(Order order) {
        UUID orderId = order.getOrderId();

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        List<SalesOrderItemResponse> itemResponses = salesOrderMapper.toItemResponseList(items);

        CustomerSummaryDto customer = resolveCustomerSummary(Map.of(), order.getCustomerId());
        String customerName = formatCustomerName(customer);
        String customerPhone = formatCustomerPhone(customer);
        String staffName = getStaffName(order.getSalesStaffId());

        SalesOrderDetailResponse response = salesOrderMapper.toDetailResponse(
                order,
                itemResponses,
                customerName,
                customerPhone,
                staffName);
        if (orderPaymentMilestoneRepository != null) {
            List<OrderPaymentMilestone> milestones = orderPaymentMilestoneRepository
                    .findByOrderIdOrderBySortOrderAsc(orderId);
            List<SalesOrderMilestoneResponse> milestoneResponses = salesOrderMapper.toMilestoneResponseList(milestones);
            response.setMilestones(milestoneResponses != null ? milestoneResponses : new ArrayList<>());
        }
        List<SalesInvoice> orderInvoices = salesInvoiceRepository.findByOrderIdOrderByBatchNoAscCreatedAtAsc(orderId);
        orderInvoices.stream()
                .filter(inv -> "STORE".equalsIgnoreCase(inv.getInvoiceType()))
                .findFirst()
                .ifPresent(invoice -> response.setStoreInvoiceId(invoice.getInvoiceId()));
        if (response.getStoreInvoiceId() == null) {
            salesInvoiceRepository.findByOrderIdAndInvoiceType(orderId, "STORE")
                    .ifPresent(invoice -> response.setStoreInvoiceId(invoice.getInvoiceId()));
        }
        applyInvoiceCounts(response, orderInvoices);
        populateDisplayItems(response, order, items, itemResponses);
        response.setPaymentMethod(order.getPaymentMethod());
        if (onlinePayosPaymentRepository != null) {
            onlinePayosPaymentRepository.findByOrderId(orderId)
                    .ifPresent(payos -> response.setPayosPayment(toPayosPaymentLinkResponse(payos)));
        }
        return response;
    }

    /**
     * Cập nhật thông tin giao nhận và điều phối (địa chỉ, lịch giao, ghi chú)
     * khi đơn hàng đang ở trạng thái PROCESSING.
     */
    @Override
    @Transactional
    public SalesOrderDetailResponse updateFulfillmentInfo(UUID orderId, UpdateOrderFulfillmentRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        if (!OrderStatus.PROCESSING.name().equalsIgnoreCase(order.getOrderStatus())) {
            log.warn("Cannot update fulfillment info for order {} with status {}", order.getOrderCode(),
                    order.getOrderStatus());
            throw new AppException(SalesErrorCode.ORDER_NOT_MODIFIABLE);
        }

        if (request != null) {
            if (request.getDeliveryAddress() != null) {
                order.setDeliveryAddress(request.getDeliveryAddress().trim());
            }
            if (request.getDeliverySchedule() != null) {
                order.setDeliverySchedule(request.getDeliverySchedule().trim());
            }
            if (request.getNote() != null) {
                order.setNote(request.getNote().trim());
            }
        }
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);

        log.info("Updated fulfillment info for order {}: address={}, schedule={}",
                order.getOrderCode(), order.getDeliveryAddress(), order.getDeliverySchedule());

        return getSalesOrderDetail(orderId);
    }

    /**
     * Xác nhận hoàn tất đơn hàng (chuyển PROCESSING -> COMPLETED)
     * khi đơn hàng đã thanh toán đủ (PAID).
     */
    @Override
    @Transactional
    public SalesOrderDetailResponse completeSalesOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        if (!OrderStatus.PROCESSING.name().equalsIgnoreCase(order.getOrderStatus())) {
            log.warn("Cannot complete order {} with status {}", order.getOrderCode(), order.getOrderStatus());
            throw new AppException(SalesErrorCode.ORDER_NOT_MODIFIABLE);
        }

        if (!PaymentStatus.PAID.name().equalsIgnoreCase(order.getPaymentStatus())) {
            log.warn("Cannot complete order {}: payment status is {}, must be PAID",
                    order.getOrderCode(), order.getPaymentStatus());
            throw new AppException(SalesErrorCode.ORDER_CANNOT_COMPLETE);
        }

        order.setOrderStatus(OrderStatus.COMPLETED.name());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);

        log.info("Sales Order {} marked as COMPLETED successfully", order.getOrderCode());

        return getSalesOrderDetail(orderId);
    }

    /**
     * Xác nhận đơn hàng trực tuyến (UC-22: PENDING_CONFIRMATION -> PROCESSING):
     * kiểm tra điều kiện thanh toán PayOS (nếu chọn chuyển khoản), kiểm tra và giữ tồn kho,
     * gán nhân viên phụ trách, cập nhật điều phối giao nhận và khởi tạo hóa đơn cửa hàng nháp (STORE - DRAFT).
     */
    @Override
    @Transactional
    public SalesOrderDetailResponse confirmOnlineOrder(UUID orderId, ConfirmOnlineOrderRequest request) {
        Order order = lockOrFindOrder(orderId);

        if (!OrderStatus.PENDING_CONFIRMATION.name().equalsIgnoreCase(order.getOrderStatus())) {
            log.warn("Cannot confirm online order {} with status {}", order.getOrderCode(), order.getOrderStatus());
            throw new AppException(SalesErrorCode.ORDER_NOT_PENDING_CONFIRMATION, order.getOrderStatus());
        }

        OnlinePayosPayment payosPayment = onlinePayosPaymentRepository != null
                ? onlinePayosPaymentRepository.findByOrderId(orderId).orElse(null)
                : null;

        if (PaymentMethod.BANK_TRANSFER.name().equalsIgnoreCase(order.getPaymentMethod())) {
            if (!PaymentStatus.PAID.name().equalsIgnoreCase(order.getPaymentStatus())) {
                if (payosPayment != null && payosClient != null) {
                    syncPayosStatusWithGateway(order, payosPayment, false);
                }
                if (!PaymentStatus.PAID.name().equalsIgnoreCase(order.getPaymentStatus())) {
                    log.warn("Blocked confirming online BANK_TRANSFER order {} because paymentStatus is {}",
                            order.getOrderCode(), order.getPaymentStatus());
                    throw new AppException(SalesErrorCode.ONLINE_ORDER_PAYOS_UNPAID);
                }
            }
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        if (items == null || items.isEmpty()) {
            throw new AppException(SalesErrorCode.ORDER_ITEMS_EMPTY);
        }

        // Kiểm tra SKU hoạt động và đủ tồn kho khả dụng trước khi đặt giữ
        for (OrderItem item : items) {
            CatalogSkuSummaryDto sku = catalogApi.getSkuSummary(item.getVariantId())
                    .orElseThrow(() -> new AppException(SalesErrorCode.SKU_NOT_FOUND, item.getVariantId()));
            if (!"ACTIVE".equalsIgnoreCase(sku.status())) {
                throw new AppException(SalesErrorCode.SKU_NOT_ACTIVE, sku.sku());
            }
            if (!inventoryApi.checkAvailability(item.getVariantId(), item.getQuantity())) {
                throw new AppException(SalesErrorCode.INSUFFICIENT_STOCK,
                        sku.productName() + " (" + sku.sku() + ")");
            }
        }

        // Đặt giữ tồn kho cho toàn bộ mặt hàng trong đơn
        for (OrderItem item : items) {
            inventoryApi.reserveStock(item.getVariantId(), item.getQuantity());
        }

        UUID currentStaffId = resolveCurrentStaffId();
        if (order.getSalesStaffId() == null && currentStaffId != null) {
            order.setSalesStaffId(currentStaffId);
        }

        if (request != null) {
            if (request.getDeliveryAddress() != null && !request.getDeliveryAddress().isBlank()) {
                order.setDeliveryAddress(request.getDeliveryAddress().trim());
            }
            if (request.getDeliverySchedule() != null) {
                order.setDeliverySchedule(request.getDeliverySchedule().trim());
            }
            if (request.getNote() != null) {
                order.setNote(request.getNote().trim());
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        order.setOrderStatus(OrderStatus.PROCESSING.name());
        order.setUpdatedAt(now);
        orderRepository.saveAndFlush(order);

        // Khởi tạo hóa đơn cửa hàng nháp (STORE - DRAFT) để sẵn sàng phát hành & tạo phiếu giao hàng (UC-39)
        Optional<SalesInvoice> existingStoreInvoice = salesInvoiceRepository.findByOrderIdAndInvoiceType(orderId, "STORE");
        if (existingStoreInvoice.isEmpty()) {
            UUID invoiceCreatorId = currentStaffId != null ? currentStaffId : order.getSalesStaffId();
            UUID storeInvoiceId = UUID.randomUUID();
            salesInvoiceRepository.saveAndFlush(SalesInvoice.builder()
                    .invoiceId(storeInvoiceId)
                    .invoiceCode("STORE-" + orderId)
                    .orderId(orderId)
                    .customerId(order.getCustomerId())
                    .invoiceType("STORE")
                    .batchNo(1)
                    .batchName("Hóa đơn bán lẻ")
                    .issueDate(now)
                    .subtotal(order.getSubtotal() != null ? order.getSubtotal() : order.getTotalAmount())
                    .discountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO)
                    .totalAmount(order.getTotalAmount())
                    .status("DRAFT")
                    .createdBy(invoiceCreatorId)
                    .createdAt(now)
                    .build());
            if (salesInvoiceItemRepository != null) {
                salesInvoiceItemRepository.saveAll(items.stream().map(item -> SalesInvoiceItem.builder()
                        .invoiceItemId(UUID.randomUUID())
                        .invoiceId(storeInvoiceId)
                        .orderItemId(item.getOrderItemId())
                        .variantId(item.getVariantId())
                        .description(item.getProductName()
                                + (item.getVariantName() != null && !item.getVariantName().isBlank()
                                        ? " - " + item.getVariantName()
                                        : ""))
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .discountAmount(item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO)
                        .taxRate(BigDecimal.ZERO)
                        .lineTotal(item.getLineTotal())
                        .build()).toList());
            }
        }

        if (PaymentStatus.PAID.name().equalsIgnoreCase(order.getPaymentStatus())) {
            recordPayosFinanceReceipt(order, payosPayment, currentStaffId);
        }

        log.info("Online Order {} confirmed (UC-22): status=PROCESSING, paymentStatus={}, staffId={}",
                order.getOrderCode(), order.getPaymentStatus(), order.getSalesStaffId());

        return getSalesOrderDetail(orderId);
    }

    /**
     * Từ chối hoặc hủy đơn hàng trực tuyến khi đang ở trạng thái PENDING_CONFIRMATION (UC-22).
     * Nếu đơn có link PayOS chưa thanh toán thì hủy link; nếu khách đã thanh toán PayOS thì
     * đảm bảo ghi nhận phiếu thu VERIFIED và tạo giao dịch chờ hoàn tiền cho Kế toán.
     */
    @Override
    @Transactional
    public SalesOrderDetailResponse rejectOnlineOrder(UUID orderId, RejectOnlineOrderRequest request) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new AppException(SalesErrorCode.ORDER_REJECT_REASON_REQUIRED);
        }
        String reason = request.getReason().trim();

        Order order = lockOrFindOrder(orderId);
        if (!OrderStatus.PENDING_CONFIRMATION.name().equalsIgnoreCase(order.getOrderStatus())) {
            log.warn("Cannot reject online order {} with status {}", order.getOrderCode(), order.getOrderStatus());
            throw new AppException(SalesErrorCode.ORDER_NOT_PENDING_CONFIRMATION, order.getOrderStatus());
        }

        UUID currentStaffId = resolveCurrentStaffId();
        OnlinePayosPayment payosPayment = onlinePayosPaymentRepository != null
                ? onlinePayosPaymentRepository.findByOrderId(orderId).orElse(null)
                : null;

        if (payosPayment != null && !PaymentStatus.PAID.name().equalsIgnoreCase(order.getPaymentStatus())
                && payosClient != null) {
            syncPayosStatusWithGateway(order, payosPayment, false);
        }

        if (PaymentStatus.PAID.name().equalsIgnoreCase(order.getPaymentStatus())) {
            recordPayosFinanceReceipt(order, payosPayment, currentStaffId);
            if (orderReceivableApi != null && order.getTotalAmount() != null && order.getTotalAmount().signum() > 0) {
                UUID storeInvoiceId = salesInvoiceRepository.findByOrderIdAndInvoiceType(orderId, "STORE")
                        .map(SalesInvoice::getInvoiceId)
                        .orElse(null);
                orderReceivableApi.createPendingCustomerRefund(
                        new com.aquasuite.ims.dto.finance.command.PendingCustomerRefundCommand(
                                order.getCustomerId(),
                                order.getOrderId(),
                                storeInvoiceId,
                                order.getTotalAmount(),
                                "Hoàn tiền do từ chối/hủy đơn hàng Online " + order.getOrderCode() + ": " + reason,
                                currentStaffId));
            }
        } else if (payosPayment != null && !"CANCELLED".equalsIgnoreCase(payosPayment.getStatus())) {
            if (payosClient != null && payosPayment.getPayosOrderCode() != null) {
                try {
                    payosClient.cancelPaymentLink(payosPayment.getPayosOrderCode(), reason);
                } catch (Exception ex) {
                    log.warn("Best-effort cancel PayOS link {} failed: {}",
                            payosPayment.getPayosOrderCode(), ex.getMessage());
                }
            }
            payosPayment.setStatus("CANCELLED");
            payosPayment.setUpdatedAt(OffsetDateTime.now());
            onlinePayosPaymentRepository.save(payosPayment);
        }

        if (order.getSalesStaffId() == null && currentStaffId != null) {
            order.setSalesStaffId(currentStaffId);
        }

        String cancelNote = "[Từ chối/Hủy đơn Online]: " + reason;
        String existingNote = order.getNote() != null && !order.getNote().isBlank() ? order.getNote().trim() : null;
        order.setNote(existingNote != null ? existingNote + "\n" + cancelNote : cancelNote);
        order.setOrderStatus(OrderStatus.CANCELLED.name());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.saveAndFlush(order);

        log.info("Online Order {} rejected/cancelled (UC-22): reason={}, paymentStatus={}",
                order.getOrderCode(), reason, order.getPaymentStatus());

        return getSalesOrderDetail(orderId);
    }

    /**
     * Đồng bộ trạng thái thanh toán PayOS cho nhân viên trên trang quản trị đơn hàng (UC-22).
     */
    @Override
    @Transactional
    public SalesOrderDetailResponse syncOnlineOrderPayosPayment(UUID orderId) {
        Order order = lockOrFindOrder(orderId);
        if (onlinePayosPaymentRepository == null) {
            throw new AppException(SalesErrorCode.ORDER_NOT_PAYABLE_VIA_PAYOS);
        }
        OnlinePayosPayment payosPayment = onlinePayosPaymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_PAYABLE_VIA_PAYOS));

        syncPayosStatusWithGateway(order, payosPayment, true);
        return getSalesOrderDetail(orderId);
    }

    private Order lockOrFindOrder(UUID orderId) {
        if (orderId == null) {
            throw new AppException(SalesErrorCode.ORDER_NOT_FOUND);
        }
        return orderRepository.findByIdForUpdate(orderId)
                .or(() -> orderRepository.findById(orderId))
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));
    }

    private void syncPayosStatusWithGateway(Order order, OnlinePayosPayment payosPayment, boolean rethrowGatewayError) {
        UUID currentStaffId = resolveCurrentStaffId();
        if (payosClient != null && payosPayment.getPayosOrderCode() != null) {
            try {
                PayosClient.PaymentLinkStatusResult remote = payosClient.getPaymentLinkInfo(payosPayment.getPayosOrderCode());
                if (remote != null) {
                    String statusName = remote.status() != null ? remote.status().toUpperCase(Locale.ROOT) : payosPayment.getStatus();
                    payosPayment.setStatus(statusName);
                    payosPayment.setAmountPaid(remote.amountPaid());
                    payosPayment.setUpdatedAt(OffsetDateTime.now());
                    onlinePayosPaymentRepository.save(payosPayment);
                }
            } catch (Exception ex) {
                log.warn("Could not sync PayOS status from gateway for order {}: {}",
                        order.getOrderCode(), ex.getMessage());
                if (rethrowGatewayError && !PaymentStatus.PAID.name().equalsIgnoreCase(order.getPaymentStatus())
                        && !"PAID".equalsIgnoreCase(payosPayment.getStatus())) {
                    if (ex instanceof AppException appEx) {
                        throw appEx;
                    }
                    throw new AppException(SalesErrorCode.PAYOS_API_ERROR, ex.getMessage());
                }
            }
        }

        if ("PAID".equalsIgnoreCase(payosPayment.getStatus())) {
            if (!PaymentStatus.PAID.name().equalsIgnoreCase(order.getPaymentStatus())) {
                order.setPaymentStatus(PaymentStatus.PAID.name());
                order.setUpdatedAt(OffsetDateTime.now());
                orderRepository.saveAndFlush(order);
            }
            recordPayosFinanceReceipt(order, payosPayment, currentStaffId);
        } else if (PaymentStatus.PAID.name().equalsIgnoreCase(order.getPaymentStatus())) {
            recordPayosFinanceReceipt(order, payosPayment, currentStaffId);
        }
    }

    private void recordPayosFinanceReceipt(Order order, OnlinePayosPayment payosPayment, UUID actorId) {
        if (orderReceivableApi == null || order == null) {
            return;
        }
        UUID payosPaymentId = payosPayment != null ? payosPayment.getPayosPaymentId() : null;
        Long payosOrderCode = payosPayment != null ? payosPayment.getPayosOrderCode() : null;
        OffsetDateTime paidAt = payosPayment != null && payosPayment.getUpdatedAt() != null
                ? payosPayment.getUpdatedAt()
                : OffsetDateTime.now();
        UUID effectiveActor = actorId != null ? actorId : order.getSalesStaffId();
        orderReceivableApi.ensureOnlinePayosReceiptRecorded(
                order.getOrderId(),
                payosPaymentId,
                payosOrderCode,
                order.getTotalAmount(),
                paidAt,
                effectiveActor);
    }

    private PayosPaymentLinkResponse toPayosPaymentLinkResponse(OnlinePayosPayment payment) {
        if (payment == null) {
            return null;
        }
        return PayosPaymentLinkResponse.builder()
                .payosOrderCode(payment.getPayosOrderCode())
                .paymentLinkId(payment.getPaymentLinkId())
                .checkoutUrl(payment.getCheckoutUrl())
                .qrCode(payment.getQrCode())
                .bin(payment.getBin())
                .accountNumber(payment.getAccountNumber())
                .accountName(payment.getAccountName())
                .amount(payment.getAmount())
                .amountPaid(payment.getAmountPaid())
                .description(payment.getDescription())
                .status(payment.getStatus())
                .build();
    }

    // =========================================================================
    // PRIVATE HELPER METHODS
    // =========================================================================
    private CustomerSummaryDto resolveCustomer(CreateSalesOrderRequest request) {
        if (request.getCustomerId() != null) {
            return crmApi.getCustomerById(request.getCustomerId())
                    .orElseThrow(() -> new AppException(SalesErrorCode.CUSTOMER_NOT_FOUND));
        }

        String phone = (request.getCustomerPhone() != null && !request.getCustomerPhone().isBlank()
                && !"0000000000".equals(request.getCustomerPhone().trim()))
                        ? request.getCustomerPhone().trim()
                        : null;
        String name = (request.getCustomerName() != null && !request.getCustomerName().isBlank())
                ? request.getCustomerName().trim()
                : null;

        if (phone != null) {
            return crmApi.getCustomerByPhone(phone)
                    .orElseGet(() -> crmApi.getOrCreateWalkInCustomer(name, phone));
        }

        if (name != null) {
            return crmApi.getOrCreateWalkInCustomer(name, null);
        }

        return crmApi.getOrCreateWalkInCustomer();
    }

    /** Lấy mã nhân viên đang đăng nhập; trả null nếu không xác định được. */
    private UUID resolveCurrentStaffId() {
        try {
            return iamApi.getCurrentUserId();
        } catch (Exception e) {
            log.debug("Current user not authenticated or resolving fallback");
            return null;
        }
    }

    /** Lấy tên nhân viên để hiển thị, dùng tên mặc định khi không tra được. */
    private String getStaffName(UUID staffId) {
        if (staffId == null)
            return "Nhân viên bán hàng";
        try {
            UserSummaryDto user = iamApi.getUserById(staffId);
            return user != null && user.fullName() != null ? user.fullName() : "Nhân viên bán hàng";
        } catch (Exception e) {
            return "Nhân viên bán hàng";
        }
    }

    /** Tạo điều kiện truy vấn đơn hàng từ các trường lọc được cung cấp. */
    private Specification<Order> buildFilterSpecification(SalesOrderFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                    String trimmed = filter.getKeyword().trim();
                    String compactPhone = extractCompactPhoneCandidate(trimmed);
                    List<String> likePatterns = new ArrayList<>();
                    likePatterns.add(toLikePattern(trimmed));
                    if (compactPhone != null) {
                        likePatterns.add(toLikePattern(compactPhone));
                    }

                    List<Predicate> keywordPredicates = new ArrayList<>();
                    keywordPredicates.add(cb.like(cb.lower(root.get("orderCode")), likePatterns.get(0), '!'));
                    keywordPredicates.add(cb.like(cb.lower(root.get("projectName")), likePatterns.get(0), '!'));
                    for (String pattern : likePatterns) {
                        keywordPredicates.add(cb.like(cb.lower(root.get("deliveryAddress")), pattern, '!'));
                    }

                    // Tìm kiếm kết hợp theo SĐT, tên hoặc mã khách hàng trong CRM (hỗ trợ khớp gần đúng)
                    Set<UUID> matchedCustomerIds = lookupMatchedCustomerIds(trimmed, compactPhone);
                    if (!matchedCustomerIds.isEmpty()) {
                        keywordPredicates.add(root.get("customerId").in(matchedCustomerIds));
                    }

                    predicates.add(cb.or(keywordPredicates.toArray(new Predicate[0])));
                }
                if (filter.getOrderStatus() != null) {
                    predicates.add(cb.equal(root.get("orderStatus"), filter.getOrderStatus().name()));
                }
                if (filter.getPaymentStatus() != null) {
                    predicates.add(cb.equal(root.get("paymentStatus"), filter.getPaymentStatus().name()));
                }
                if (filter.getOrderType() != null) {
                    predicates.add(cb.equal(root.get("orderType"), filter.getOrderType().name()));
                }
                if (filter.getOrderChannel() != null) {
                    if (filter.getOrderChannel() == OrderChannel.COUNTER) {
                        predicates.add(root.get("orderChannel").in(OrderChannel.COUNTER.name(), OrderChannel.POS.name()));
                    } else {
                        predicates.add(cb.equal(root.get("orderChannel"), filter.getOrderChannel().name()));
                    }
                }
                if (filter.getFromDate() != null) {
                    OffsetDateTime fromDateTime = filter.getFromDate().atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
                    predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), fromDateTime));
                }
                if (filter.getToDate() != null) {
                    OffsetDateTime toDateTime = filter.getToDate().atTime(LocalTime.MAX).atZone(VIETNAM_ZONE).toOffsetDateTime();
                    predicates.add(cb.lessThanOrEqualTo(root.get("orderDate"), toDateTime));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<UUID, CustomerSummaryDto> loadCustomerSummaries(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return Map.of();
        }
        Set<UUID> customerIds = orders.stream()
                .map(Order::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<UUID, CustomerSummaryDto> batch = crmApi.getCustomersByIds(customerIds);
            return batch != null ? batch : Map.of();
        } catch (Exception e) {
            log.debug("Batch customer lookup ignored: {}", e.getMessage());
            return Map.of();
        }
    }

    private CustomerSummaryDto resolveCustomerSummary(Map<UUID, CustomerSummaryDto> preloaded, UUID customerId) {
        if (customerId == null) {
            return null;
        }
        CustomerSummaryDto cached = preloaded != null ? preloaded.get(customerId) : null;
        if (cached != null) {
            return cached;
        }
        try {
            return crmApi.getCustomerById(customerId).orElse(null);
        } catch (Exception e) {
            log.debug("Customer {} lookup ignored", customerId);
            return null;
        }
    }

    private String formatCustomerName(CustomerSummaryDto customer) {
        if (customer == null || customer.customerName() == null || customer.customerName().isBlank()
                || "Walk-in Retail Customer".equalsIgnoreCase(customer.customerName().trim())) {
            return "Khách lẻ tại quầy";
        }
        return customer.customerName().trim();
    }

    private String formatCustomerPhone(CustomerSummaryDto customer) {
        if (customer == null || customer.phone() == null || customer.phone().isBlank()
                || "0000000000".equals(customer.phone().trim())) {
            return null;
        }
        return customer.phone().trim();
    }

    private Set<UUID> lookupMatchedCustomerIds(String trimmedKeyword, String compactPhone) {
        Set<UUID> ids = new LinkedHashSet<>();
        addMatchedCustomerIds(ids, trimmedKeyword);
        if (compactPhone != null) {
            addMatchedCustomerIds(ids, compactPhone);
        }
        return ids;
    }

    private void addMatchedCustomerIds(Set<UUID> target, String keyword) {
        try {
            List<UUID> found = crmApi.findCustomerIdsByKeyword(keyword);
            if (found != null) {
                target.addAll(found);
            }
        } catch (Exception e) {
            log.debug("Customer keyword lookup ignored: {}", e.getMessage());
        }
    }

    private String extractCompactPhoneCandidate(String trimmed) {
        if (trimmed == null || !PHONE_CHAR_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        String compact = trimmed.replaceAll("[\\s().-]+", "");
        if (compact.length() < 2 || compact.equals(trimmed)) {
            return null;
        }
        return compact;
    }

    private String toLikePattern(String keyword) {
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private void populateDisplayItems(SalesOrderDetailResponse response, Order order,
            List<OrderItem> items, List<SalesOrderItemResponse> mapped) {
        List<QuotationItem> snapshots = order.getQuotationId() != null
                && items.stream().anyMatch(i -> i.getComboId() != null
                        && (i.getComboQuantity() == null || i.getComboSku() == null || i.getComboUnitPrice() == null))
                                ? quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(order.getQuotationId())
                                : List.of();
        response.setDisplayItems(com.aquasuite.ims.service.sales.helper.OrderDisplayItems
                .build(items, mapped, snapshots));
    }

    private void applyInvoiceCounts(SalesOrderDetailResponse response, List<SalesInvoice> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            response.setTotalInvoiceCount(0);
            response.setDraftInvoiceCount(0);
            response.setIssuedInvoiceCount(0);
            return;
        }
        int total = 0;
        int draft = 0;
        int issued = 0;
        for (SalesInvoice inv : invoices) {
            if (inv == null || "CANCELLED".equalsIgnoreCase(inv.getStatus())) {
                continue;
            }
            total++;
            if ("DRAFT".equalsIgnoreCase(inv.getStatus())) {
                draft++;
            } else if ("ISSUED".equalsIgnoreCase(inv.getStatus())) {
                issued++;
            }
        }
        response.setTotalInvoiceCount(total);
        response.setDraftInvoiceCount(draft);
        response.setIssuedInvoiceCount(issued);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SalesOrderPaymentInfoDto> getOrderPaymentInfo(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return orderRepository.findById(orderId).map(this::toPaymentInfo);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<SalesOrderPaymentInfoDto> lockOrderPaymentInfo(UUID orderId) {
        return orderRepository.findForPaymentUpdate(orderId).map(this::toPaymentInfo);
    }

    private SalesOrderPaymentInfoDto toPaymentInfo(Order order) {
        boolean isValid = !OrderStatus.CANCELLED.name().equalsIgnoreCase(order.getOrderStatus());
        return SalesOrderPaymentInfoDto.builder()
                .orderId(order.getOrderId())
                .orderCode(order.getOrderCode())
                .customerId(order.getCustomerId())
                .storeInvoiceId(salesInvoiceRepository.findByOrderIdAndInvoiceType(order.getOrderId(), "STORE")
                        .map(com.aquasuite.ims.entity.sales.SalesInvoice::getInvoiceId).orElse(null))
                .orderType(order.getOrderType())
                .orderChannel(order.getOrderChannel())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .totalAmount(order.getTotalAmount())
                .depositAmount(order.getDepositAmount())
                .stagedDelivery(order.isStagedDelivery())
                .validForPayment(isValid)
                .build();
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void updatePaymentStatus(UUID orderId, String paymentStatus) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID must not be null");
        }
        if (paymentStatus == null || paymentStatus.isBlank()) {
            throw new AppException(SalesErrorCode.INVALID_PAYMENT_STATUS, String.valueOf(paymentStatus));
        }

        PaymentStatus resolvedStatus;
        try {
            resolvedStatus = PaymentStatus.valueOf(paymentStatus.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException(SalesErrorCode.INVALID_PAYMENT_STATUS, paymentStatus);
        }

        Order order = orderRepository.findForPaymentUpdate(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        order.setPaymentStatus(resolvedStatus.name());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);

        if (resolvedStatus != PaymentStatus.UNPAID) {
            eventPublisher.publishEvent(new com.aquasuite.ims.event.sales.OrderPaymentRecorded(
                    UUID.randomUUID(), orderId, java.time.Instant.now()));
        }

        log.info("Updated paymentStatus of order {} ({}) to {}",
                order.getOrderCode(), order.getOrderId(), resolvedStatus.name());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesOrderMilestoneDto> getOrderPaymentMilestones(UUID orderId) {
        if (orderId == null) {
            return List.of();
        }
        return orderPaymentMilestoneRepository.findByOrderIdOrderBySortOrderAsc(orderId).stream()
                .sorted(java.util.Comparator
                        .comparing((OrderPaymentMilestone m) -> m.getSortOrder() != null ? m.getSortOrder()
                                : Integer.MAX_VALUE)
                        .thenComparing(OrderPaymentMilestone::getMilestoneId))
                .map(m -> SalesOrderMilestoneDto.builder()
                        .milestoneId(m.getMilestoneId())
                        .orderId(m.getOrderId())
                        .stageName(m.getStageName())
                        .percent(m.getPercent())
                        .amount(m.getAmount())
                        .sortOrder(m.getSortOrder())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SalesOrderItemSummaryDto> getOrderItemSummary(UUID orderItemId) {
        if (orderItemId == null)
            return Optional.empty();
        return orderItemRepository.findById(orderItemId).flatMap(item -> orderRepository.findById(item.getOrderId())
                .map(order -> toOrderItemSummary(item, order)));
    }

    private SalesOrderItemSummaryDto toOrderItemSummary(OrderItem item, Order order) {
        return SalesOrderItemSummaryDto.builder().orderItemId(item.getOrderItemId()).orderId(order.getOrderId())
                .orderCode(order.getOrderCode()).variantId(item.getVariantId()).sku(item.getSku())
                .productName(item.getProductName()).variantName(item.getVariantName()).build();
    }

    // ── OrderModificationRequestService Delegations ──────────────────────────

    @Override
    public Page<OrderModificationRequestResponse> list(Pageable pageable, boolean accountantOnly) {
        return modificationService != null ? modificationService.list(pageable, accountantOnly) : Page.empty();
    }

    @Override
    public OrderModificationRequestResponse detail(UUID requestId, boolean accountantOnly) {
        return modificationService != null ? modificationService.detail(requestId, accountantOnly) : null;
    }

    @Override
    public OrderModificationRequestResponse create(CreateOrderModificationRequest body, UUID actorId) {
        return modificationService != null ? modificationService.create(body, actorId) : null;
    }

    @Override
    @Transactional
    public CustomerOrderModificationRequestResponse createMyOrderModificationRequest(
            UUID orderId, CreateCustomerOrderModificationRequest request, UUID userId) {
        if (modificationService == null) {
            throw new IllegalStateException("Order modification service is not configured");
        }
        CustomerSummaryDto customer = userId == null ? null : crmApi.getCustomerByUserId(userId).orElse(null);
        if (customer == null) {
            throw new AppException(SalesErrorCode.CUSTOMER_NOT_FOUND);
        }
        CreateOrderModificationRequest createRequest = CreateOrderModificationRequest.builder()
                .orderId(orderId)
                .requestType(request.getRequestType())
                .customerReason(request.getCustomerReason())
                .requestedDeliveryAddress(request.getRequestedDeliveryAddress())
                .items(request.getItems())
                .build();
        OrderModificationRequestResponse created =
                modificationService.createForCustomer(createRequest, userId, customer.customerId());
        return new CustomerOrderModificationRequestResponse(
                created.getId(), created.getOrderId(), created.getRequestType(),
                created.getRequestStatus(), created.getCustomerReason(), created.getCreatedAt());
    }

    @Override
    public OrderModificationRequestResponse reject(UUID requestId, ReviewOrderModificationRequest request,
            UUID actorId) {
        return modificationService != null ? modificationService.reject(requestId, request, actorId) : null;
    }

    @Override
    public OrderModificationRequestResponse approve(UUID requestId, ApproveOrderModificationRequest request,
            UUID actorId, boolean shopOwner) {
        return modificationService != null ? modificationService.approve(requestId, request, actorId, shopOwner) : null;
    }

    @Override
    public OrderModificationRequestResponse recordTaxResult(UUID requestId,
            RecordOrderModificationTaxResultRequest request, UUID actorId) {
        return modificationService != null ? modificationService.recordTaxResult(requestId, request, actorId) : null;
    }

    @Override
    public void sendResultEmail(UUID requestId, UUID actorId) {
        if (modificationService != null)
            modificationService.sendResultEmail(requestId, actorId);
    }
}
