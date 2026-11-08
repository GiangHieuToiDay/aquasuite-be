package com.aquasuite.ims.module.sales.internal.dev;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.error.ProjectDepositErrorCode;
import com.aquasuite.ims.module.sales.internal.payload.request.ProjectDepositRequests.CreateOrderDeposit;
import com.aquasuite.ims.share.exception.AppException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;

/** Demo replacement for the UC-21/28 order-creation service. Never persists orders. */
@Service
@Profile("uc29-demo & !prod & !production")
public class DemoProjectReservationOrderFactory {
    private final CatalogApi catalog;
    private final CrmApi crm;
    private final Clock clock;
    private final Map<UUID, Creation> requests = new HashMap<>();
    private final Map<UUID, Snapshot> orders = new HashMap<>();

    public DemoProjectReservationOrderFactory(CatalogApi catalog, CrmApi crm,
                                              @Qualifier("projectDepositClock") Clock clock) {
        this.catalog = catalog;
        this.crm = crm;
        this.clock = clock;
    }

    public synchronized Snapshot create(CreateOrderDeposit request, UUID actorId) {
        Creation existing = requests.get(request.requestId());
        if (existing != null) {
            if (!existing.request.equals(request)) throw new AppException(ProjectDepositErrorCode.IDEMPOTENCY_CONFLICT);
            return orders.get(existing.orderId);
        }
        if (request.requestId() == null || request.customerId() == null || request.lines() == null || request.lines().isEmpty()
                || request.lines().size() > 200 || actorId == null || request.amount() == null || request.amount().signum() <= 0) {
            throw new AppException(ProjectDepositErrorCode.INVALID_REQUEST);
        }
        var customer = crm.getCustomerById(request.customerId()).filter(c -> request.customerId().equals(c.customerId()) && "ACTIVE".equals(c.status()))
                .orElseThrow(() -> new AppException(ProjectDepositErrorCode.INVALID_REQUEST));
        UUID orderId = UUID.nameUUIDFromBytes(("uc29-order:"+request.requestId()).getBytes(StandardCharsets.UTF_8));
        List<OrderItem> lines = new ArrayList<>();
        Set<UUID> variants = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var input : request.lines()) {
            if (input == null || input.variantId() == null || input.quantity() == null || input.quantity().signum() <= 0
                    || input.quantity().scale() > 2 || input.quantity().compareTo(new BigDecimal("9999999999.99")) > 0
                    || !variants.add(input.variantId())) throw new AppException(ProjectDepositErrorCode.INVALID_REQUEST);
            var sku = catalog.getSkuSummary(input.variantId()).filter(s -> input.variantId().equals(s.variantId()) && "ACTIVE".equals(s.status()))
                    .orElseThrow(() -> new AppException(ProjectDepositErrorCode.INVALID_REQUEST));
            if (sku.sellingPrice() == null || sku.sellingPrice().signum() < 0) throw new AppException(ProjectDepositErrorCode.INVALID_REQUEST);
            String unit = sku.unit() == null ? "" : Normalizer.normalize(sku.unit(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
            if (Set.of("cai", "bo", "chiec", "thung", "piece", "set", "pcs").contains(unit)
                    && input.quantity().stripTrailingZeros().scale() > 0) throw new AppException(ProjectDepositErrorCode.INVALID_REQUEST);
            BigDecimal value = sku.sellingPrice().multiply(input.quantity()).setScale(2, RoundingMode.HALF_UP);
            total = total.add(value);
            UUID lineId = UUID.nameUUIDFromBytes((orderId+":"+input.variantId()).getBytes(StandardCharsets.UTF_8));
            lines.add(OrderItem.builder().orderItemId(lineId).orderId(orderId).variantId(input.variantId())
                    .quantity(input.quantity()).unitPrice(sku.sellingPrice()).lineTotal(value).discountAmount(BigDecimal.ZERO).build());
        }
        if (total.signum() <= 0 || total.compareTo(new BigDecimal("9999999999999.99")) > 0
                || request.amount().compareTo(total) > 0) throw new AppException(ProjectDepositErrorCode.INVALID_REQUEST);
        // Demo uses current catalog prices only: negotiated discounts still belong to UC-21/27/28.
        Order order = Order.builder().orderId(orderId).orderCode("DEMO-HOLD-"+request.requestId())
                .customerId(customer.customerId()).salesStaffId(actorId).orderChannel("IN_STORE").orderStatus("CONFIRMED")
                .paymentStatus("UNPAID").orderDate(OffsetDateTime.now(clock)).subtotal(total).totalAmount(total).build();
        Snapshot result = new Snapshot(order, lines);
        orders.put(orderId, result);
        requests.put(request.requestId(), new Creation(request, orderId));
        return result;
    }

    public synchronized boolean hasRequest(UUID requestId) { return requests.containsKey(requestId); }
    public synchronized Optional<Order> findOrder(UUID id) { return Optional.ofNullable(orders.get(id)).map(Snapshot::order); }
    public synchronized Optional<List<OrderItem>> findItems(UUID id) { return Optional.ofNullable(orders.get(id)).map(Snapshot::items); }

    public record Snapshot(Order order, List<OrderItem> items) {
        public Snapshot { items = List.copyOf(items); }
    }
    private record Creation(CreateOrderDeposit request, UUID orderId) {}
}
