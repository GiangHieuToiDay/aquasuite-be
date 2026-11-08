package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.DepositReservationOrderSpecification;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
class DepositReservationOrderListPostgresTest extends PostgreSqlIntegrationTestSupport {
    @Autowired OrderRepository orders;
    @Autowired JdbcTemplate jdbc;
    private final java.util.List<UUID> fixtureIds = new java.util.ArrayList<>();

    private org.springframework.data.jpa.domain.Specification<Order> eligible() {
        return DepositReservationOrderSpecification.eligible().and(
                (root,query,cb) -> root.get("orderId").in(fixtureIds));
    }

    private UUID order(boolean intent, String deposit, String status) {
        UUID customer = UUID.randomUUID(), id = UUID.randomUUID();
        jdbc.update("insert into customers(customer_id,customer_code,customer_name,phone) values(?,?,?,?)",
                customer, "C-" + customer, "List fixture", String.format("09%08d",Math.floorMod(customer.hashCode(),100000000)));
        jdbc.update("""
                insert into orders(order_id,order_code,customer_id,order_channel,order_status,payment_status,
                    order_date,total_amount,deposit_amount,reserve_stock_after_deposit,deposit_percent)
                values(?,?,?,'IN_STORE',?,'UNPAID',current_timestamp,100,?,?,20)
                """, id, "O-" + id, customer, status, deposit == null ? null : new BigDecimal(deposit), intent);
        fixtureIds.add(id);
        return id;
    }

    @Test void candidatesUsePercentageAndStatusWithoutRequiringHistoricalFlag() {
        long existing = orders.count();
        UUID opted = order(true,"20","PROCESSING");
        UUID legacy = order(false,"20","CONFIRMED");
        jdbc.update("""
                insert into sales_reservation_requests(order_id,selection,pricing,reservation_value,actor_id)
                values(?,'[]','{}',100,?)
                """,legacy,UUID.randomUUID());
        UUID noIntent = order(false,"20","PROCESSING");
        UUID noAmount = order(true,null,"PROCESSING");
        UUID zeroAmount = order(true,"0","PROCESSING");
        order(true,"20","CANCELLED");
        order(true,"20","COMPLETED");
        var pageable = PageRequest.of(0,1,Sort.by("orderCode"));
        var first = orders.findAll(eligible(),pageable);
        var second = orders.findAll(eligible(),pageable.next());
        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(5);
        assertThat(second.getContent()).hasSize(1);
        assertThat(orders.findAll(eligible())).extracting(Order::getOrderId)
                .containsExactlyInAnyOrder(opted,legacy,noIntent,noAmount,zeroAmount);
        assertThat(orders.count()).isEqualTo(existing + 7);
        assertThat(orders.findAll(eligible().and(
                (root,query,cb) -> cb.equal(root.get("orderId"),opted)))).hasSize(1);
    }

    @Test void candidatesIncludeOtherPercentagesAmountOnlyAndMilestoneOrders() {
        UUID exact = order(false,"20","PROCESSING");
        UUID thirty = order(false,null,"PROCESSING");
        jdbc.update("update orders set deposit_percent=30 where order_id=?",thirty);
        UUID amountOnly = order(false,"10","PROCESSING");
        jdbc.update("update orders set deposit_percent=null where order_id=?",amountOnly);
        UUID milestone = order(false,null,"PROCESSING");
        jdbc.update("update orders set deposit_percent=null where order_id=?",milestone);
        jdbc.update("insert into order_payment_milestones(milestone_id,order_id,stage_name,percent,amount,sort_order) values(?,?,'Deposit',10,10,0)",UUID.randomUUID(),milestone);
        UUID noDeposit = order(false,null,"PROCESSING");
        jdbc.update("update orders set deposit_percent=null where order_id=?",noDeposit);
        assertThat(orders.findAll(eligible())).extracting(Order::getOrderId)
                .containsExactlyInAnyOrder(exact,thirty,amountOnly,milestone);
    }

    @Test void defaultIsFalseAndIntentPersistsWithoutChangingOtherData() {
        UUID id = order(false,"20","PROCESSING");
        var order = orders.findById(id).orElseThrow();
        assertThat(order.isReserveStockAfterDeposit()).isFalse();
        order.setReserveStockAfterDeposit(true);
        orders.saveAndFlush(order);
        assertThat(jdbc.queryForObject("select reserve_stock_after_deposit from orders where order_id=?",Boolean.class,id)).isTrue();
        assertThat(jdbc.queryForObject("select deposit_amount from orders where order_id=?",BigDecimal.class,id))
                .isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("select payment_status from orders where order_id=?",String.class,id)).isEqualTo("UNPAID");
    }
}
