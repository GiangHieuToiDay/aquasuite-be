package com.aquasuite.ims.repository.inventory;

import com.aquasuite.ims.dto.inventory.request.CreateGoodsReceiptItemRequest;
import com.aquasuite.ims.dto.inventory.request.CreateGoodsReceiptRequest;
import com.aquasuite.ims.dto.inventory.response.GoodsReceiptResponse;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.BaseErrorCode;
import com.aquasuite.ims.exception.inventory.InventoryErrorCode;
import com.aquasuite.ims.exception.purchasing.PurchasingErrorCode;
import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.inventory.GoodsReceiptService;
import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.service.inventory.impl.GoodsReceiptServiceImpl;
import com.aquasuite.ims.service.purchasing.PurchaseOrderService;
import com.aquasuite.ims.service.purchasing.helper.PoCodeGenerator;
import com.aquasuite.ims.service.purchasing.helper.PurchaseOrderCalculator;
import com.aquasuite.ims.service.purchasing.helper.PurchaseOrderPdfService;
import com.aquasuite.ims.service.purchasing.impl.PurchaseOrderServiceImpl;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * UC-32 trên PostgreSQL thật: nhận nhiều phiếu, chống ghi trùng, khóa PO khi nhận đồng thời,
 * cạnh tranh với hủy PO và cuộn ngược toàn bộ khi một bước lỗi.
 */
@DataJpaTest(showSql = false, properties = "spring.datasource.hikari.maximum-pool-size=6")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({GoodsReceiptServiceImpl.class, PurchaseOrderServiceImpl.class, PoCodeGenerator.class,
        PurchaseOrderCalculator.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GoodsReceiptPostgresTest extends PostgreSqlIntegrationTestSupport {

    @Autowired GoodsReceiptService service;
    @SpyBean PurchaseOrderService purchasing;
    @SpyBean StockMovementRepository movements;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;
    @MockBean IamQueryService iam;
    @MockBean CatalogLookupService catalog;
    @MockBean InventoryService inventoryService;
    @MockBean PurchaseOrderPdfService pdf;

    UUID actor, supplier, variant, po, poItem, category, product;

    @BeforeEach
    void fixture() {
        reset(purchasing, movements);
        actor = UUID.randomUUID();
        supplier = UUID.randomUUID();
        variant = UUID.randomUUID();
        po = UUID.randomUUID();
        poItem = UUID.randomUUID();
        category = UUID.randomUUID();
        product = UUID.randomUUID();
        String s = actor.toString().substring(0, 8).toUpperCase();
        jdbc.update("insert into users(id,username,email,password_hash,status) values(?,?,?,?,'ACTIVE')",
                actor, "gr_" + actor, "gr_" + actor + "@test.invalid", "{disabled-test-account}");
        jdbc.update("insert into categories(category_id,category_code,category_name) values(?,?,?)",
                category, "GRC" + s, "GR test " + s);
        jdbc.update("insert into products(product_id,product_code,category_id,product_name) values(?,?,?,'GR test')",
                product, "GRP" + s, category);
        jdbc.update("insert into product_variants(variant_id,product_id,sku,variant_name,unit) values(?,?,?,'GR test','piece')",
                variant, product, "GRS" + s);
        jdbc.update("insert into suppliers(supplier_id,supplier_code,supplier_name,created_at,updated_at) "
                + "values(?,?,'GR supplier',now(),now())", supplier, "GRN" + s);
        jdbc.update("insert into purchase_orders(purchase_order_id,po_code,supplier_id,warehouse_code,status,order_date,created_by) "
                + "values(?,?,?,'WH_MAIN','CONFIRMED',current_date,?)", po, "PO-GR-" + s, supplier, actor);
        jdbc.update("insert into purchase_order_items(purchase_order_item_id,purchase_order_id,variant_id,unit,quantity_ordered,"
                + "quantity_received,unit_cost,line_total,sku) values(?,?,?,'piece',10,0,1000,10000,?)",
                poItem, po, variant, "GRS" + s);
    }

    /** Xóa toàn bộ fixture theo thứ tự FK để không làm bẩn CSDL Testcontainers dùng chung giữa các test class. */
    @AfterEach
    void cleanup() {
        jdbc.update("delete from stock_movements where variant_id=?", variant);
        jdbc.update("delete from inventories where variant_id=?", variant);
        jdbc.update("delete from goods_receipt_items where goods_receipt_id in "
                + "(select goods_receipt_id from goods_receipts where purchase_order_id=?)", po);
        jdbc.update("delete from goods_receipts where purchase_order_id=?", po);
        jdbc.update("delete from purchase_order_items where purchase_order_id=?", po);
        jdbc.update("delete from purchase_orders where purchase_order_id=?", po);
        jdbc.update("delete from suppliers where supplier_id=?", supplier);
        jdbc.update("delete from product_variants where variant_id=?", variant);
        jdbc.update("delete from products where product_id=?", product);
        jdbc.update("delete from categories where category_id=?", category);
        jdbc.update("delete from users where id=?", actor);
    }

    private CreateGoodsReceiptRequest request(UUID key, String qty) {
        return CreateGoodsReceiptRequest.builder()
                .idempotencyKey(key)
                .purchaseOrderId(po)
                .items(List.of(CreateGoodsReceiptItemRequest.builder()
                        .purchaseOrderItemId(poItem).quantity(new BigDecimal(qty)).build()))
                .build();
    }

    private GoodsReceiptResponse receive(String qty) {
        return service.createAndConfirm(request(UUID.randomUUID(), qty), actor);
    }

    private BigDecimal onHand() {
        List<BigDecimal> rows = jdbc.queryForList(
                "select on_hand_qty from inventories where variant_id=? and warehouse_code='WH_MAIN'", BigDecimal.class, variant);
        return rows.isEmpty() ? BigDecimal.ZERO : rows.get(0);
    }

    private BigDecimal receivedQty() {
        return jdbc.queryForObject("select quantity_received from purchase_order_items where purchase_order_item_id=?",
                BigDecimal.class, poItem);
    }

    private String poStatus() {
        return jdbc.queryForObject("select status from purchase_orders where purchase_order_id=?", String.class, po);
    }

    private int receiptCount() {
        return jdbc.queryForObject("select count(*) from goods_receipts where purchase_order_id=?", Integer.class, po);
    }

    private int receiptItemCount() {
        return jdbc.queryForObject("select count(*) from goods_receipt_items i join goods_receipts r "
                + "on r.goods_receipt_id=i.goods_receipt_id where r.purchase_order_id=?", Integer.class, po);
    }

    private int movementCount() {
        return jdbc.queryForObject("select count(*) from stock_movements where variant_id=? and movement_type='GOODS_RECEIPT'",
                Integer.class, variant);
    }

    private void assertNothingRecorded() {
        assertThat(receiptCount()).isZero();
        assertThat(receiptItemCount()).isZero();
        assertThat(movementCount()).isZero();
        assertThat(onHand()).isEqualByComparingTo("0");
        assertThat(receivedQty()).isEqualByComparingTo("0");
        assertThat(poStatus()).isEqualTo("CONFIRMED");
    }

    private static BaseErrorCode codeOf(Throwable t) {
        Throwable cause = t instanceof ExecutionException ? t.getCause() : t;
        return cause instanceof AppException app ? app.getErrorCode() : null;
    }

    @Test
    @DisplayName("Nhận 7 rồi 3 qua hai phiếu: PARTIALLY_RECEIVED → COMPLETED, tồn 10, 2 thẻ kho")
    void partialThenFull() {
        receive("7");
        assertThat(poStatus()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(onHand()).isEqualByComparingTo("7");

        receive("3");
        assertThat(poStatus()).isEqualTo("COMPLETED");
        assertThat(receivedQty()).isEqualByComparingTo("10");
        assertThat(onHand()).isEqualByComparingTo("10");
        assertThat(receiptCount()).isEqualTo(2);
        assertThat(movementCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from goods_receipts where purchase_order_id=? and status='CONFIRMED'",
                Integer.class, po)).isEqualTo(2);

        assertThatThrownBy(() -> receive("1"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(InventoryErrorCode.GOODS_RECEIPT_PO_NOT_RECEIVABLE));
    }

    @Test
    @DisplayName("Retry cùng key + payload: một phiếu, cộng tồn một lần; key reuse khác payload → INV_019")
    void idempotentRetry() {
        UUID key = UUID.randomUUID();
        GoodsReceiptResponse first = service.createAndConfirm(request(key, "4"), actor);
        GoodsReceiptResponse retry = service.createAndConfirm(request(key, "4.00"), actor);

        assertThat(retry.goodsReceiptId()).isEqualTo(first.goodsReceiptId());
        assertThat(retry.items()).hasSize(1);
        assertThat(receiptCount()).isEqualTo(1);
        assertThat(onHand()).isEqualByComparingTo("4");
        assertThat(receivedQty()).isEqualByComparingTo("4");

        assertThatThrownBy(() -> service.createAndConfirm(request(key, "5"), actor))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(InventoryErrorCode.GOODS_RECEIPT_IDEMPOTENCY_CONFLICT));
        assertThat(onHand()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("Hai phiếu đồng thời 6 + 6 trên PO đặt 10: đúng một thành công, không nhận vượt")
    void concurrentReceiptsCannotOverReceive() throws Exception {
        List<Future<GoodsReceiptResponse>> results = runConcurrently(
                () -> receive("6"), () -> receive("6"));

        int ok = 0;
        List<BaseErrorCode> errors = new ArrayList<>();
        for (Future<GoodsReceiptResponse> f : results) {
            try {
                f.get(30, TimeUnit.SECONDS);
                ok++;
            } catch (ExecutionException e) {
                errors.add(codeOf(e));
            }
        }
        assertThat(ok).isEqualTo(1);
        assertThat(errors).containsExactly(InventoryErrorCode.GOODS_RECEIPT_EXCEEDS_REMAINING);
        assertThat(receivedQty()).isEqualByComparingTo("6");
        assertThat(onHand()).isEqualByComparingTo("6");
        assertThat(receiptCount()).isEqualTo(1);
        assertThat(movementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cùng key gửi đồng thời hai lần: một phiếu, cả hai nhận cùng kết quả")
    void concurrentSameKeyRecordsOnce() throws Exception {
        UUID key = UUID.randomUUID();
        List<Future<GoodsReceiptResponse>> results = runConcurrently(
                () -> service.createAndConfirm(request(key, "3"), actor),
                () -> service.createAndConfirm(request(key, "3"), actor));

        UUID a = results.get(0).get(30, TimeUnit.SECONDS).goodsReceiptId();
        UUID b = results.get(1).get(30, TimeUnit.SECONDS).goodsReceiptId();
        assertThat(a).isEqualTo(b);
        assertThat(receiptCount()).isEqualTo(1);
        assertThat(onHand()).isEqualByComparingTo("3");
        assertThat(receivedQty()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("PO bị hủy trong lúc phiếu chờ khóa: phiếu bị từ chối, không ghi gì")
    void cancelWhileWaitingForLockRejectsReceipt() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> canceller = pool.submit(() -> new TransactionTemplate(txManager).executeWithoutResult(tx -> {
                purchasing.lockReceivablePurchaseOrder(po);
                jdbc.update("update purchase_orders set status='CANCELLED' where purchase_order_id=?", po);
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();
            Future<GoodsReceiptResponse> receipt = pool.submit(() -> receive("2"));
            Thread.sleep(500);
            assertThat(receipt.isDone()).as("receipt must wait for the PO row lock").isFalse();

            release.countDown();
            canceller.get(30, TimeUnit.SECONDS);
            assertThatThrownBy(() -> receipt.get(30, TimeUnit.SECONDS))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(InventoryErrorCode.GOODS_RECEIPT_PO_NOT_RECEIVABLE));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertThat(poStatus()).isEqualTo("CANCELLED");
        assertThat(receiptCount()).isZero();
        assertThat(movementCount()).isZero();
        assertThat(onHand()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Cập nhật PO lỗi sau khi đã ghi phiếu/tồn/thẻ kho → cuộn ngược toàn bộ")
    void purchasingFailureRollsBackEverything() {
        // applyGoodsReceipt là MANDATORY nên lời gọi stub (qua proxy transaction) phải nằm trong một transaction.
        new TransactionTemplate(txManager).executeWithoutResult(tx ->
                doThrow(new AppException(PurchasingErrorCode.PO_NOT_RECEIVABLE, "CANCELLED"))
                        .when(purchasing).applyGoodsReceipt(any(), anyMap()));

        assertThatThrownBy(() -> receive("5"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(PurchasingErrorCode.PO_NOT_RECEIVABLE));
        assertNothingRecorded();
    }

    @Test
    @DisplayName("Ghi thẻ kho lỗi → cuộn ngược phiếu, tồn và tiến độ PO")
    void movementFailureRollsBackEverything() {
        doThrow(new IllegalStateException("movement insert failed")).when(movements).save(any());

        assertThatThrownBy(() -> receive("5")).isInstanceOf(IllegalStateException.class);
        assertNothingRecorded();
    }

    @Test
    @DisplayName("Lỗi validate trên PO thật: vượt remaining, dòng ngoài PO → không ghi gì")
    void validationAgainstRealPo() {
        assertThatThrownBy(() -> receive("11"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(InventoryErrorCode.GOODS_RECEIPT_EXCEEDS_REMAINING));
        CreateGoodsReceiptRequest foreign = CreateGoodsReceiptRequest.builder()
                .idempotencyKey(UUID.randomUUID()).purchaseOrderId(po)
                .items(List.of(CreateGoodsReceiptItemRequest.builder()
                        .purchaseOrderItemId(UUID.randomUUID()).quantity(BigDecimal.ONE).build()))
                .build();
        assertThatThrownBy(() -> service.createAndConfirm(foreign, actor))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(InventoryErrorCode.GOODS_RECEIPT_ITEM_NOT_IN_PO));
        assertNothingRecorded();
    }

    @SafeVarargs
    private static <T> List<Future<T>> runConcurrently(Callable<T>... tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
        return futures;
    }
}
