package com.aquasuite.ims.service.sales.impl;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogBundleSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.dto.crm.response.CustomerAddressDto;
import com.aquasuite.ims.dto.crm.response.CustomerSummaryDto;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.dto.inventory.response.StockSummaryDto;
import com.aquasuite.ims.entity.sales.CartItem;
import com.aquasuite.ims.entity.sales.OnlinePayosPayment;
import com.aquasuite.ims.entity.sales.Order;
import com.aquasuite.ims.entity.sales.OrderItem;
import com.aquasuite.ims.enums.sales.OrderChannel;
import com.aquasuite.ims.enums.sales.OrderStatus;
import com.aquasuite.ims.enums.sales.OrderType;
import com.aquasuite.ims.enums.sales.PaymentMethod;
import com.aquasuite.ims.enums.sales.PaymentStatus;
import com.aquasuite.ims.exception.sales.SalesErrorCode;
import com.aquasuite.ims.mapper.sales.OnlineCheckoutMapper;
import com.aquasuite.ims.mapper.sales.SalesOrderMapper;
import com.aquasuite.ims.integration.sales.PayosClient;
import com.aquasuite.ims.config.PayosProperties;
import com.aquasuite.ims.dto.sales.request.PlaceOnlineOrderRequest;
import com.aquasuite.ims.dto.sales.response.CartResponse;
import com.aquasuite.ims.dto.sales.response.OnlineCheckoutPreviewResponse;
import com.aquasuite.ims.dto.sales.response.OnlineOrderCheckoutResponse;
import com.aquasuite.ims.dto.sales.response.PayosPaymentLinkResponse;
import com.aquasuite.ims.dto.sales.response.SalesOrderItemResponse;
import com.aquasuite.ims.repository.sales.CartItemRepository;
import com.aquasuite.ims.repository.sales.OnlinePayosPaymentRepository;
import com.aquasuite.ims.repository.sales.OrderItemRepository;
import com.aquasuite.ims.repository.sales.OrderRepository;
import com.aquasuite.ims.service.finance.PaymentService;
import com.aquasuite.ims.service.sales.helper.CartComboSupport;
import com.aquasuite.ims.service.sales.helper.OrderCodeGenerator;
import com.aquasuite.ims.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Triển khai nghiệp vụ Đặt hàng và Thanh toán trực tuyến (UC-18) kèm PayOS
 * (Napas 24/7).
 * Đơn hàng Online không sử dụng mua nợ (CREDIT) và bắt đầu ở trạng thái
 * PENDING_CONFIRMATION.
 */
@Slf4j
@Service
public class OnlineCheckoutServiceImpl {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final Set<String> REUSABLE_PAYOS_STATUSES = Set.of("PENDING", "PROCESSING", "UNDERPAID");

    private final CartServiceImpl cartService;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OnlinePayosPaymentRepository onlinePayosPaymentRepository;
    private final OrderCodeGenerator orderCodeGenerator;
    private final SalesOrderMapper salesOrderMapper;
    private final OnlineCheckoutMapper onlineCheckoutMapper;
    private final PayosClient payosClient;
    private final PayosProperties payosProperties;
    private final IamQueryService iamApi;
    private final CustomerService crmApi;
    private final CatalogLookupService catalogApi;
    private final InventoryService inventoryApi;
    private final PaymentService paymentService;

    @Autowired
    public OnlineCheckoutServiceImpl(
            CartServiceImpl cartService,
            CartItemRepository cartItemRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OnlinePayosPaymentRepository onlinePayosPaymentRepository,
            OrderCodeGenerator orderCodeGenerator,
            SalesOrderMapper salesOrderMapper,
            OnlineCheckoutMapper onlineCheckoutMapper,
            PayosClient payosClient,
            PayosProperties payosProperties,
            IamQueryService iamApi,
            CustomerService crmApi,
            CatalogLookupService catalogApi,
            InventoryService inventoryApi,
            @Autowired(required = false) PaymentService paymentService) {
        this.cartService = cartService;
        this.cartItemRepository = cartItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.onlinePayosPaymentRepository = onlinePayosPaymentRepository;
        this.orderCodeGenerator = orderCodeGenerator;
        this.salesOrderMapper = salesOrderMapper;
        this.onlineCheckoutMapper = onlineCheckoutMapper;
        this.payosClient = payosClient;
        this.payosProperties = payosProperties;
        this.iamApi = iamApi;
        this.crmApi = crmApi;
        this.catalogApi = catalogApi;
        this.inventoryApi = inventoryApi;
        this.paymentService = paymentService;
    }

    public OnlineCheckoutServiceImpl(
            CartServiceImpl cartService,
            CartItemRepository cartItemRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OnlinePayosPaymentRepository onlinePayosPaymentRepository,
            OrderCodeGenerator orderCodeGenerator,
            SalesOrderMapper salesOrderMapper,
            OnlineCheckoutMapper onlineCheckoutMapper,
            PayosClient payosClient,
            PayosProperties payosProperties,
            IamQueryService iamApi,
            CustomerService crmApi,
            CatalogLookupService catalogApi,
            InventoryService inventoryApi) {
        this(cartService, cartItemRepository, orderRepository, orderItemRepository,
                onlinePayosPaymentRepository, orderCodeGenerator, salesOrderMapper,
                onlineCheckoutMapper, payosClient, payosProperties, iamApi, crmApi,
                catalogApi, inventoryApi, null);
    }

    @Transactional(readOnly = true)
    public OnlineCheckoutPreviewResponse getCheckoutPreview() {
        CustomerSummaryDto customer = resolveCurrentCustomer();
        CartResponse cart = cartService.getCurrentCart();
        List<CustomerAddressDto> savedAddresses = crmApi.getDeliveryAddressesByCustomerId(customer.customerId());
        CustomerAddressDto defaultAddress = savedAddresses.stream()
                .filter(CustomerAddressDto::isDefault)
                .findFirst()
                .orElse(savedAddresses.isEmpty() ? null : savedAddresses.get(0));

        BigDecimal shippingFee = BigDecimal.ZERO;
        BigDecimal grandTotal = defaultAmount(cart.getSubtotal()).add(shippingFee);

        return OnlineCheckoutPreviewResponse.builder()
                .cart(cart)
                .savedAddresses(savedAddresses)
                .defaultAddress(defaultAddress)
                .shippingFee(shippingFee)
                .grandTotal(grandTotal)
                .payosConfigured(payosProperties.isConfigured())
                .build();
    }

    @Transactional
    public OnlineOrderCheckoutResponse placeOnlineOrder(PlaceOnlineOrderRequest request) {
        validatePaymentMethod(request.getPaymentMethod());
        CustomerSummaryDto customer = resolveCurrentCustomer();
        UUID customerId = customer.customerId();

        List<CartItem> cartItems = cartItemRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId);
        if (cartItems.isEmpty()) {
            throw new AppException(SalesErrorCode.CART_EMPTY_FOR_CHECKOUT);
        }

        String resolvedAddress = resolveDeliveryAddress(customerId, request);
        List<OrderItem> itemsToSave = buildAndValidateOrderItems(cartItems);

        BigDecimal subtotal = itemsToSave.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shippingFee = BigDecimal.ZERO;
        BigDecimal totalAmount = subtotal.add(shippingFee);

        UUID orderId = UUID.randomUUID();
        String orderCode = orderCodeGenerator.generateOrderCode();
        OffsetDateTime now = OffsetDateTime.now();

        for (OrderItem item : itemsToSave) {
            item.setOrderId(orderId);
        }

        Order order = Order.builder()
                .orderId(orderId)
                .orderCode(orderCode)
                .customerId(customerId)
                .orderType(OrderType.RETAIL.name())
                .orderChannel(OrderChannel.ONLINE.name())
                .orderStatus(OrderStatus.PENDING_CONFIRMATION.name())
                .paymentStatus(PaymentStatus.UNPAID.name())
                .paymentMethod(request.getPaymentMethod().name())
                .orderDate(now)
                .subtotal(subtotal)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(shippingFee)
                .totalAmount(totalAmount)
                .deliveryAddress(resolvedAddress)
                .deliverySchedule(trimToNull(request.getDeliverySchedule()))
                .note(trimToNull(request.getNote()))
                .createdAt(now)
                .updatedAt(now)
                .build();

        Order savedOrder = orderRepository.saveAndFlush(order);
        List<OrderItem> savedItems = orderItemRepository.saveAllAndFlush(itemsToSave);

        OnlinePayosPayment payosPayment = null;
        if (request.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            payosPayment = createPayosPaymentForOrder(savedOrder, savedItems, customer);
        }

        cartItemRepository.deleteAllByCustomerId(customerId);

        log.info("Placed online order (UC-18): orderCode={}, customerId={}, method={}, total={}",
                orderCode, customerId, request.getPaymentMethod(), totalAmount);

        return buildCheckoutResponse(savedOrder, savedItems, payosPayment);
    }

    @Transactional(readOnly = true)
    public List<OnlineOrderCheckoutResponse> getMyOnlineOrders() {
        UUID customerId = resolveCurrentCustomer().customerId();
        List<Order> orders = orderRepository.findByCustomerIdAndOrderChannelOrderByCreatedAtDesc(
                customerId,
                OrderChannel.ONLINE.name());
        if (orders.isEmpty()) {
            return List.of();
        }

        List<UUID> orderIds = orders.stream().map(Order::getOrderId).toList();
        Map<UUID, List<OrderItem>> itemsByOrder = orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        Map<UUID, OnlinePayosPayment> payosByOrder = onlinePayosPaymentRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(
                        OnlinePayosPayment::getOrderId,
                        p -> p,
                        (existing, replacement) -> existing));

        return orders.stream()
                .map(order -> buildCheckoutResponse(
                        order,
                        itemsByOrder.getOrDefault(order.getOrderId(), List.of()),
                        payosByOrder.get(order.getOrderId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OnlineOrderCheckoutResponse getOnlineOrderDetail(UUID orderId) {
        UUID customerId = resolveCurrentCustomer().customerId();
        Order order = findOwnedOrder(orderId, customerId);
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        OnlinePayosPayment payosPayment = onlinePayosPaymentRepository.findByOrderId(orderId).orElse(null);
        return buildCheckoutResponse(order, items, payosPayment);
    }

    @Transactional
    public OnlineOrderCheckoutResponse verifyAndSyncPayosPayment(UUID orderId) {
        UUID customerId = resolveCurrentCustomer().customerId();
        Order order = findOwnedOrder(orderId, customerId);
        OnlinePayosPayment payosPayment = onlinePayosPaymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_PAYABLE_VIA_PAYOS));

        PayosClient.PaymentLinkStatusResult remoteStatus = payosClient.getPaymentLinkInfo(payosPayment.getPayosOrderCode());
        String statusName = remoteStatus.status() != null ? remoteStatus.status().toUpperCase() : "PENDING";

        payosPayment.setStatus(statusName);
        payosPayment.setAmountPaid(remoteStatus.amountPaid());
        onlinePayosPaymentRepository.save(payosPayment);

        if ("PAID".equals(statusName)) {
            if (!PaymentStatus.PAID.name().equalsIgnoreCase(order.getPaymentStatus())) {
                order.setPaymentStatus(PaymentStatus.PAID.name());
                order.setUpdatedAt(OffsetDateTime.now());
                order = orderRepository.save(order);
                log.info("Synced PayOS payment PAID for orderCode={}, payosOrderCode={}",
                        order.getOrderCode(), payosPayment.getPayosOrderCode());
            }
            if (paymentService != null) {
                UUID actorId = null;
                try {
                    actorId = iamApi.getCurrentUserId();
                } catch (Exception ignored) {
                    actorId = null;
                }
                paymentService.ensureOnlinePayosReceiptRecorded(
                        order.getOrderId(),
                        payosPayment.getPayosPaymentId(),
                        payosPayment.getPayosOrderCode(),
                        order.getTotalAmount(),
                        payosPayment.getUpdatedAt() != null ? payosPayment.getUpdatedAt() : OffsetDateTime.now(),
                        actorId);
            }
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        return buildCheckoutResponse(order, items, payosPayment);
    }

    @Transactional
    public OnlineOrderCheckoutResponse createOrRefreshPayosPaymentLink(UUID orderId) {
        CustomerSummaryDto customer = resolveCurrentCustomer();
        Order order = findOwnedOrder(orderId, customer.customerId());

        if (!OrderChannel.ONLINE.name().equalsIgnoreCase(order.getOrderChannel())
                || OrderStatus.CANCELLED.name().equalsIgnoreCase(order.getOrderStatus())
                || PaymentStatus.PAID.name().equalsIgnoreCase(order.getPaymentStatus())) {
            throw new AppException(SalesErrorCode.ORDER_NOT_PAYABLE_VIA_PAYOS);
        }

        Optional<OnlinePayosPayment> existingOpt = onlinePayosPaymentRepository.findByOrderId(orderId);
        if (existingOpt.isPresent()) {
            OnlinePayosPayment existing = existingOpt.get();
            if (REUSABLE_PAYOS_STATUSES.contains(existing.getStatus())) {
                List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
                return buildCheckoutResponse(order, items, existing);
            }
            onlinePayosPaymentRepository.delete(existing);
            onlinePayosPaymentRepository.flush();
        }

        if (!PaymentMethod.BANK_TRANSFER.name().equalsIgnoreCase(order.getPaymentMethod())) {
            order.setPaymentMethod(PaymentMethod.BANK_TRANSFER.name());
            order = orderRepository.save(order);
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        OnlinePayosPayment created = createPayosPaymentForOrder(order, items, customer);
        return buildCheckoutResponse(order, items, created);
    }

    private void validatePaymentMethod(PaymentMethod method) {
        if (method != PaymentMethod.BANK_TRANSFER && method != PaymentMethod.CASH) {
            throw new AppException(SalesErrorCode.ONLINE_PAYMENT_METHOD_INVALID);
        }
    }

    private CustomerSummaryDto resolveCurrentCustomer() {
        UUID userId = iamApi.getCurrentUserId();
        return crmApi.getCustomerByUserId(userId)
                .orElseThrow(() -> new AppException(SalesErrorCode.CUSTOMER_NOT_FOUND));
    }

    private Order findOwnedOrder(UUID orderId, UUID customerId) {
        return orderRepository.findByOrderIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));
    }

    private String resolveDeliveryAddress(UUID customerId, PlaceOnlineOrderRequest request) {
        if (request.getAddressId() != null) {
            CustomerAddressDto address = crmApi.getDeliveryAddressesByCustomerId(customerId).stream()
                    .filter(a -> request.getAddressId().equals(a.addressId()))
                    .findFirst()
                    .orElseThrow(() -> new AppException(SalesErrorCode.DELIVERY_ADDRESS_REQUIRED));
            return formatAddress(address.recipientName(), address.phone(), address.addressText());
        }

        String manualAddress = trimToNull(request.getDeliveryAddress());
        if (manualAddress == null) {
            throw new AppException(SalesErrorCode.DELIVERY_ADDRESS_REQUIRED);
        }
        return formatAddress(request.getRecipientName(), request.getRecipientPhone(), manualAddress);
    }

    private String formatAddress(String recipientName, String phone, String addressText) {
        String name = trimToNull(recipientName);
        String mobile = trimToNull(phone);
        if (name == null && mobile == null) {
            return addressText;
        }
        String contact = (name != null ? name : "") + (mobile != null ? " (" + mobile + ")" : "");
        return contact.trim() + " - " + addressText;
    }

    private List<OrderItem> buildAndValidateOrderItems(List<CartItem> cartItems) {
        Set<UUID> standaloneVariantIds = cartItems.stream()
                .map(CartItem::getVariantId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> comboIds = cartItems.stream()
                .map(CartItem::getComboId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, CatalogSkuSummaryDto> skuMap = standaloneVariantIds.isEmpty()
                ? Collections.emptyMap()
                : catalogApi.getSkuSummaries(standaloneVariantIds);
        Map<UUID, CatalogBundleSummaryDto> bundleMap = comboIds.isEmpty()
                ? Collections.emptyMap()
                : catalogApi.getBundleSummaries(comboIds);

        Set<UUID> allStockVariantIds = new LinkedHashSet<>(standaloneVariantIds);
        for (CatalogBundleSummaryDto bundle : bundleMap.values()) {
            if (bundle == null || bundle.items() == null) continue;
            for (CatalogBundleSummaryDto.CatalogBundleItemDto comp : bundle.items()) {
                if (comp != null && comp.variantId() != null) {
                    allStockVariantIds.add(comp.variantId());
                }
            }
        }
        Map<UUID, StockSummaryDto> stockMap = allStockVariantIds.isEmpty()
                ? Collections.emptyMap()
                : inventoryApi.getStockSummaries(allStockVariantIds);

        Map<UUID, BigDecimal> cumulativeVariantDemand = new LinkedHashMap<>();
        List<OrderItem> items = new ArrayList<>();
        Instant now = Instant.now();

        for (CartItem cartItem : cartItems) {
            if (cartItem.getComboId() != null) {
                CatalogBundleSummaryDto bundle = bundleMap.get(cartItem.getComboId());
                if (bundle == null) {
                    throw new AppException(SalesErrorCode.COMBO_NOT_FOUND, cartItem.getComboId());
                }
                if (!CartComboSupport.isComboActiveAndPublished(bundle, now)) {
                    throw new AppException(SalesErrorCode.INVALID_COMBO,
                            bundle.bundleName() != null ? bundle.bundleName() : cartItem.getComboId());
                }

                BigDecimal rawQty = defaultAmount(cartItem.getQuantity());
                if (rawQty.compareTo(BigDecimal.ZERO) <= 0 || rawQty.stripTrailingZeros().scale() > 0) {
                    throw new AppException(SalesErrorCode.INVALID_CART_QUANTITY);
                }
                int comboQty = rawQty.intValueExact();
                UUID comboGroupId = UUID.randomUUID();
                List<OrderItem> comboOrderItems = new ArrayList<>(bundle.items().size());

                for (CatalogBundleSummaryDto.CatalogBundleItemDto comp : bundle.items()) {
                    BigDecimal totalCompQty = BigDecimal.valueOf((long) comp.quantity() * comboQty);
                    BigDecimal newDemand = cumulativeVariantDemand.getOrDefault(comp.variantId(), BigDecimal.ZERO)
                            .add(totalCompQty);
                    BigDecimal availableQty = Optional.ofNullable(stockMap.get(comp.variantId()))
                            .map(StockSummaryDto::availableQty)
                            .orElse(BigDecimal.ZERO);
                    if (availableQty.compareTo(newDemand) < 0) {
                        throw new AppException(SalesErrorCode.INSUFFICIENT_STOCK,
                                comp.sku() + " (thuộc combo " + bundle.bundleName() + ")");
                    }
                    cumulativeVariantDemand.put(comp.variantId(), newDemand);

                    BigDecimal compUnitPrice = defaultAmount(comp.sellingPrice());
                    BigDecimal compLineTotal = compUnitPrice.multiply(totalCompQty).setScale(2, RoundingMode.HALF_UP);

                    comboOrderItems.add(OrderItem.builder()
                            .orderItemId(UUID.randomUUID())
                            .comboId(bundle.bundleId())
                            .comboGroupId(comboGroupId)
                            .comboQuantity(BigDecimal.valueOf(comboQty))
                            .comboSku(bundle.bundleCode())
                            .comboUnitPrice(bundle.packagePrice())
                            .variantId(comp.variantId())
                            .sku(comp.sku())
                            .productName(bundle.bundleName())
                            .variantName(comp.variantName())
                            .quantity(totalCompQty)
                            .unitPrice(compUnitPrice)
                            .discountAmount(BigDecimal.ZERO)
                            .lineTotal(compLineTotal)
                            .build());
                }

                Map<UUID, BigDecimal> weights = comboOrderItems.stream()
                        .collect(Collectors.toMap(OrderItem::getOrderItemId, OrderItem::getLineTotal));
                BigDecimal packageTotal = defaultAmount(bundle.packagePrice())
                        .multiply(BigDecimal.valueOf(comboQty));
                Map<UUID, BigDecimal> allocated = CartComboSupport.allocateComboNetPrices(packageTotal, weights);

                for (OrderItem comboOrderItem : comboOrderItems) {
                    BigDecimal net = allocated.get(comboOrderItem.getOrderItemId());
                    BigDecimal gross = comboOrderItem.getLineTotal();
                    comboOrderItem.setDiscountAmount(gross.subtract(net).max(BigDecimal.ZERO));
                    comboOrderItem.setLineTotal(net);
                    items.add(comboOrderItem);
                }
                continue;
            }

            CatalogSkuSummaryDto sku = skuMap.get(cartItem.getVariantId());
            if (sku == null) {
                throw new AppException(SalesErrorCode.SKU_NOT_FOUND, cartItem.getVariantId());
            }
            if (!ACTIVE_STATUS.equalsIgnoreCase(sku.status())) {
                throw new AppException(SalesErrorCode.SKU_NOT_ACTIVE, sku.sku());
            }

            BigDecimal quantity = defaultAmount(cartItem.getQuantity());
            BigDecimal newDemand = cumulativeVariantDemand.getOrDefault(cartItem.getVariantId(), BigDecimal.ZERO)
                    .add(quantity);
            BigDecimal availableQty = Optional.ofNullable(stockMap.get(cartItem.getVariantId()))
                    .map(StockSummaryDto::availableQty)
                    .orElse(BigDecimal.ZERO);
            if (availableQty.compareTo(newDemand) < 0) {
                throw new AppException(SalesErrorCode.INSUFFICIENT_STOCK, sku.sku());
            }
            cumulativeVariantDemand.put(cartItem.getVariantId(), newDemand);

            BigDecimal unitPrice = defaultAmount(sku.sellingPrice());
            BigDecimal lineTotal = unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);

            items.add(OrderItem.builder()
                    .orderItemId(UUID.randomUUID())
                    .variantId(cartItem.getVariantId())
                    .sku(sku.sku())
                    .productName(sku.productName())
                    .variantName(sku.variantName())
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .discountAmount(BigDecimal.ZERO)
                    .lineTotal(lineTotal)
                    .build());
        }
        return items;
    }

    private OnlinePayosPayment createPayosPaymentForOrder(
            Order order,
            List<OrderItem> items,
            CustomerSummaryDto customer) {
        long payosOrderCode = generateUniquePayosOrderCode();
        long amount = resolvePayosAmount(order.getTotalAmount());
        String description = buildPayosDescription(order.getOrderCode());

        List<PayosClient.PaymentItemData> payosItems = items.stream()
                .map(i -> {
                    int qty = Math.max(1, i.getQuantity().intValue());
                    long itemUnitPrice = i.getComboId() != null
                            ? Math.max(0L, defaultAmount(i.getLineTotal())
                                    .divide(BigDecimal.valueOf(qty), 0, RoundingMode.HALF_UP)
                                    .longValue())
                            : i.getUnitPrice().setScale(0, RoundingMode.HALF_UP).longValue();
                    String itemName = i.getVariantName() != null && !i.getVariantName().isBlank()
                            ? i.getVariantName()
                            : i.getSku();
                    return new PayosClient.PaymentItemData(itemName, qty, itemUnitPrice, "Cái");
                })
                .toList();

        String returnUrl = appendOrderIdQuery(payosProperties.getReturnUrl(), order.getOrderId());
        String cancelUrl = appendOrderIdQuery(payosProperties.getCancelUrl(), order.getOrderId());

        PayosClient.PaymentLinkResult result = payosClient.createPaymentLink(
                new PayosClient.CreatePaymentCommand(
                        payosOrderCode,
                        amount,
                        description,
                        returnUrl,
                        cancelUrl,
                        customer.customerName(),
                        customer.email(),
                        customer.phone(),
                        order.getDeliveryAddress(),
                        payosItems));

        OnlinePayosPayment entity = OnlinePayosPayment.builder()
                .orderId(order.getOrderId())
                .payosOrderCode(result.orderCode())
                .paymentLinkId(result.paymentLinkId())
                .checkoutUrl(result.checkoutUrl())
                .qrCode(result.qrCode())
                .bin(result.bin())
                .accountNumber(result.accountNumber())
                .accountName(result.accountName())
                .amount(result.amount())
                .amountPaid(0L)
                .description(result.description())
                .status(result.status())
                .build();

        return onlinePayosPaymentRepository.saveAndFlush(entity);
    }

    private long generateUniquePayosOrderCode() {
        long candidate;
        do {
            long secondsPart = System.currentTimeMillis() / 100L;
            int randomSuffix = ThreadLocalRandom.current().nextInt(100, 999);
            candidate = secondsPart * 1000L + randomSuffix;
        } while (onlinePayosPaymentRepository.existsByPayosOrderCode(candidate));
        return candidate;
    }

    private long resolvePayosAmount(BigDecimal orderTotal) {
        if (payosProperties.getOverrideAmount() != null && payosProperties.getOverrideAmount() > 0) {
            return payosProperties.getOverrideAmount();
        }
        long total = defaultAmount(orderTotal).setScale(0, RoundingMode.HALF_UP).longValue();
        return Math.max(1000L, total);
    }

    private String buildPayosDescription(String orderCode) {
        String compact = orderCode != null ? orderCode.replaceAll("[^A-Za-z0-9]", "") : "AQUASUITE";
        String raw = "AQS " + compact;
        return raw.length() > 25 ? raw.substring(0, 25) : raw;
    }

    private String appendOrderIdQuery(String baseUrl, UUID orderId) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:5173/checkout/result?orderId=" + orderId;
        }
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "orderId=" + orderId;
    }

    private OnlineOrderCheckoutResponse buildCheckoutResponse(
            Order order,
            List<OrderItem> items,
            OnlinePayosPayment payosPayment) {
        List<SalesOrderItemResponse> itemResponses = salesOrderMapper.toItemResponseList(items);
        PayosPaymentLinkResponse payosResponse = payosPayment != null
                ? onlineCheckoutMapper.toPayosPaymentLinkResponse(payosPayment)
                : null;
        return onlineCheckoutMapper.toOrderCheckoutResponse(order, itemResponses, payosResponse);
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
