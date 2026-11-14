package com.aquasuite.ims.module.fulfillment;

import com.aquasuite.ims.module.fulfillment.internal.service.DeliveryIssueService;
import com.aquasuite.ims.module.fulfillment.internal.repository.DeliveryIssueStore;
import com.aquasuite.ims.module.fulfillment.internal.payload.request.DeliveryIssueRequest;
import com.aquasuite.ims.module.inventory.api.*;
import com.aquasuite.ims.module.inventory.internal.service.impl.*;
import com.aquasuite.ims.module.inventory.internal.repository.StockReservationStore;
import com.aquasuite.ims.module.sales.internal.service.impl.OrderDispatchApiImpl;
import com.aquasuite.ims.module.sales.internal.service.impl.SalesOrderApiImpl;
import com.aquasuite.ims.module.finance.internal.service.impl.DeliveryCodCapacityApiImpl;
import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql=false,properties="spring.datasource.hikari.maximum-pool-size=4")
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({DeliveryIssueService.class,com.aquasuite.ims.module.fulfillment.internal.service.DeliveryService.class,DeliveryIssueStore.class,OrderDispatchApiImpl.class,SalesOrderApiImpl.class,
        DeliveryCodCapacityApiImpl.class,InventoryDispatchService.class,StockReservationStore.class,
        StockReservationService.class,InventoryServiceImpl.class,DeliveryIssuePostgresTest.Json.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class DeliveryIssuePostgresTest extends PostgreSqlIntegrationTestSupport {
    @TestConfiguration static class Json {
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
    }
    @Autowired DeliveryIssueService service;
    @Autowired com.aquasuite.ims.module.fulfillment.internal.service.DeliveryService deliveryService;
    @Autowired StockReservationApi reservations;
    @Autowired JdbcTemplate jdbc;
    @MockBean IamApi iam;
    @MockBean CatalogApi catalog;
    @MockBean com.aquasuite.ims.module.fulfillment.internal.storage.DeliveryPhotoStorage photoStorage;
    UUID order,item,variant,customer,actor,inventory,invoice;

    @BeforeEach void fixture() {
        reset(iam,catalog,photoStorage);
        order=UUID.randomUUID(); item=UUID.randomUUID(); variant=UUID.randomUUID();
        customer=UUID.randomUUID(); actor=UUID.randomUUID(); inventory=UUID.randomUUID();
        jdbc.update("insert into users(id,username,email,password_hash,status) values(?,?,?,?,'ACTIVE')",
                actor,"invoice_"+actor,"invoice_"+actor+"@test.invalid","{disabled-test-account}");
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("ACCOUNTANT")).build());
        when(catalog.getSkuSummary(variant)).thenReturn(Optional.of(CatalogSkuSummaryDto.builder()
                .variantId(variant).serialTracking(false).build()));
        UUID category=UUID.randomUUID(), product=UUID.randomUUID();
        jdbc.update("insert into categories(category_id,category_code,category_name) values(?,?,?)",category,
                "C_"+category.toString().replace("-","").toUpperCase(Locale.ROOT),"Dispatch test "+category);
        jdbc.update("insert into products(product_id,product_code,category_id,product_name) values(?,?,?,?)",product,"P-"+product,category,"Test");
        jdbc.update("insert into product_variants(variant_id,product_id,sku,variant_name,unit) values(?,?,?,?,?)",variant,product,"SKU-"+variant,"Test","piece");
        jdbc.update("insert into customers(customer_id,customer_code,customer_name,phone) values(?,?,?,?)",customer,"C-"+customer,"Test", "09"+String.format("%08d",Math.floorMod(customer.hashCode(),100000000)));
        jdbc.update("insert into orders(order_id,order_code,customer_id,order_channel,order_status,payment_status,order_date,total_amount) values(?,?,?,'IN_STORE','PROCESSING','UNPAID',current_timestamp,100)",order,"O-"+order,customer);
        jdbc.update("insert into order_items(order_item_id,order_id,variant_id,quantity,line_total) values(?,?,?,10,100)",item,order,variant);
        invoice=addInvoice(order,new BigDecimal("10"));
        jdbc.update("insert into inventories(inventory_id,variant_id,warehouse_code,on_hand_qty,reserved_qty,minimum_qty,updated_at) values(?,?,'WH_MAIN',20,0,0,current_timestamp)",inventory,variant);
    }
    UUID addInvoice(UUID orderId,BigDecimal quantity) {
        return addInvoice(orderId,quantity,"STORE");
    }
    UUID addInvoice(UUID orderId,BigDecimal quantity,String type) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into sales_invoices(invoice_id,invoice_code,order_id,customer_id,invoice_type,issue_date,status,created_by,total_amount)"
                +" values(?,?,?,?,?,current_timestamp,'ISSUED',?,100)",id,"INV-"+id,orderId,customer,type,actor);
        jdbc.update("insert into sales_invoice_items(invoice_item_id,invoice_id,order_item_id,variant_id,quantity,unit_price,line_total) values(?,?,?,?,?,10,100)",
                UUID.randomUUID(),id,item,variant,quantity);
        return id;
    }
    DeliveryIssueRequest request(UUID id,String qty,String cod) {
        return new DeliveryIssueRequest(id,"WH_MAIN","Recipient","0900000000","Test address",null,
                new BigDecimal(cod),null,List.of(new DeliveryIssueRequest.Line(item,new BigDecimal(qty))));
    }
    UUID draft(String qty,String cod) {
        UUID id=UUID.randomUUID(); service.create(order,request(id,qty,cod)); return id;
    }
    BigDecimal number(String column) { return jdbc.queryForObject("select "+column+" from inventories where inventory_id=?",BigDecimal.class,inventory); }
    int movements() { return jdbc.queryForObject("select count(*) from stock_movements where variant_id=?",Integer.class,variant); }

    @Test void draftAndCancelNeverChangeStockAndReplayIsStable() {
        UUID id=UUID.randomUUID(); var r=request(id,"5","40");
        assertThat(service.create(order,r).batchNo()).isEqualTo(1);
        assertThat(service.create(order,r).deliveryId()).isEqualTo(id);
        assertThatThrownBy(() -> service.create(order,request(id,"6","40"))).isInstanceOf(AppException.class);
        assertThat(number("on_hand_qty")).isEqualByComparingTo("20");
        assertThat(service.cancel(id).deliveryStatus()).isEqualTo("CANCELLED");
        assertThat(service.cancel(id).deliveryStatus()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(AppException.class);
        assertThat(service.create(order,request(UUID.randomUUID(),"5","0")).batchNo()).isEqualTo(2);
        assertThat(movements()).isZero();
    }
    @Test void noDepositRequiredAndUnreservedShipmentsCannotBeHeldAgain() {
        UUID id=draft("5","100"); service.confirm(id); service.confirm(id);
        assertThat(number("on_hand_qty")).isEqualByComparingTo("15");
        assertThat(movements()).isEqualTo(1);
        assertThat(reservations.findByOrder(order).getFirst().consumedQty()).isEqualByComparingTo("5");
        var held=reservations.reserveForDeposit(new StockReservationApi.Reserve(UUID.randomUUID(),order,item,variant,"WH_MAIN",new BigDecimal("10"),actor));
        assertThat(held.heldQty()).isEqualByComparingTo("5");
        assertThat(service.candidates(order).items().getFirst().remainingQty()).isEqualByComparingTo("5");
    }
    @Test void consumesOnlyOwnHoldAndPreservesOtherOrderHold() {
        reservations.reserveForDeposit(new StockReservationApi.Reserve(UUID.randomUUID(),order,item,variant,"WH_MAIN",new BigDecimal("10"),actor));
        reservations.reserveForDeposit(new StockReservationApi.Reserve(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),variant,"WH_MAIN",new BigDecimal("5"),actor));
        service.confirm(draft("5","0"));
        assertThat(number("on_hand_qty")).isEqualByComparingTo("15");
        assertThat(number("reserved_qty")).isEqualByComparingTo("10");
        var r=reservations.findByOrder(order).getFirst();
        assertThat(r.heldQty()).isEqualByComparingTo("5"); assertThat(r.consumedQty()).isEqualByComparingTo("5");
        assertThat(reservations.history(r.reservationId())).anyMatch(a -> "CONSUME".equals(a.action()));
    }
    @Test void twoUnreservedBatchesAndExcessThirdRejected() {
        service.confirm(draft("5","50")); service.confirm(draft("5","50"));
        assertThat(number("on_hand_qty")).isEqualByComparingTo("10");
        assertThat(reservations.findByOrder(order).getFirst().consumedQty()).isEqualByComparingTo("10");
        assertThatThrownBy(() -> draft("1","0")).isInstanceOf(AppException.class);
    }
    @Test void competingDraftsCannotExceedOrderQuantity() throws Exception {
        UUID a=draft("7","0"), b=draft("7","0");
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(UUID id:List.of(a,b)) results.add(pool.submit(() -> { gate.await(); try {service.confirm(id);return true;}catch(AppException ex){return false;} }));
            gate.countDown();
            int count=0; for(var f:results) if(f.get(20,TimeUnit.SECONDS)) count++;
            assertThat(count).isEqualTo(1); assertThat(movements()).isEqualTo(1);
            assertThat(number("on_hand_qty")).isEqualByComparingTo("13");
        } finally { pool.shutdownNow(); }
    }
    @Test void preservesLegacyHoldsWhenIssuingFreeStock() {
        UUID id=draft("5","0"); jdbc.update("update inventories set reserved_qty=3 where inventory_id=?",inventory);
        service.confirm(id);
        assertThat(number("on_hand_qty")).isEqualByComparingTo("15"); assertThat(number("reserved_qty")).isEqualByComparingTo("3");
    }
    @Test void consumesOwnRecordedHoldAndPreservesLegacyDifference() {
        reservations.reserveDeferredDeposit(new StockReservationApi.Reserve(UUID.randomUUID(),order,item,variant,"WH_MAIN",new BigDecimal("2"),actor));
        reservations.reserveDeferredDeposit(new StockReservationApi.Reserve(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),variant,"WH_MAIN",BigDecimal.ONE,actor));
        jdbc.update("update inventories set on_hand_qty=10,reserved_qty=4 where inventory_id=?",inventory);
        service.confirm(draft("2","0"));
        assertThat(number("on_hand_qty")).isEqualByComparingTo("8");
        assertThat(number("reserved_qty")).isEqualByComparingTo("2");
        assertThat(reservations.findByOrder(order).getFirst().heldQty()).isEqualByComparingTo("0");
        assertThat(reservations.findByOrder(order).getFirst().consumedQty()).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("select sum(held_qty) from stock_reservations where inventory_id=?",BigDecimal.class,inventory)).isEqualByComparingTo("1");
    }
    @Test void doesNotUseLegacyReservedUnitsAsFreeStock() {
        UUID id=draft("5","0");
        jdbc.update("update inventories set on_hand_qty=6,reserved_qty=3 where inventory_id=?",inventory);
        assertThatThrownBy(() -> service.confirm(id)).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo("FUL_006"));
        assertThat(number("on_hand_qty")).isEqualByComparingTo("6");
        assertThat(number("reserved_qty")).isEqualByComparingTo("3");
    }
    @Test void rejectsAggregateBelowRecordedHolds() {
        reservations.reserveForDeposit(new StockReservationApi.Reserve(UUID.randomUUID(),order,item,variant,"WH_MAIN",new BigDecimal("5"),actor));
        UUID id=draft("2","0"); jdbc.update("update inventories set reserved_qty=4 where inventory_id=?",inventory);
        assertThatThrownBy(() -> service.confirm(id)).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo("INV_DISPATCH_001"));
        assertThat(number("on_hand_qty")).isEqualByComparingTo("20");
        assertThat(reservations.findByOrder(order).getFirst().heldQty()).isEqualByComparingTo("5");
    }
    @Test void insufficientStockRollsBackConfirmation() {
        UUID id=draft("5","0"); jdbc.update("update inventories set on_hand_qty=2 where inventory_id=?",inventory);
        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(AppException.class);
        assertThat(movements()).isZero();
        assertThat(jdbc.queryForObject("select delivery_status from deliveries where delivery_id=?",String.class,id)).isEqualTo("DRAFT");
        assertThat(reservations.findByOrder(order)).isEmpty();
    }
    @Test void aggregateHoldWithoutSourceReservationDirectsStaffToReservationWorkspace() {
        UUID id = draft("5", "0");
        jdbc.update("update inventories set on_hand_qty=5,reserved_qty=5 where inventory_id=?", inventory);
        assertThatThrownBy(() -> service.confirm(id)).isInstanceOfSatisfying(AppException.class, error -> {
            assertThat(error.getErrorCode().getCode()).isEqualTo("FUL_006");
            assertThat(error.getMessage()).contains("Cọc & hàng giữ");
        });
        assertThat(number("on_hand_qty")).isEqualByComparingTo("5");
        assertThat(number("reserved_qty")).isEqualByComparingTo("5");
        assertThat(reservations.findByOrder(order)).isEmpty();
        assertThat(movements()).isZero();
        assertThat(jdbc.queryForObject("select delivery_status from deliveries where delivery_id=?", String.class, id)).isEqualTo("DRAFT");
    }
    @Test void ownReservationCanBeUsedAfterReviewOnTheSameDraft() {
        UUID id = draft("5", "0");
        reservations.reserveDeferredDeposit(new StockReservationApi.Reserve(UUID.randomUUID(), order, item,
                variant, "WH_MAIN", new BigDecimal("5"), actor));
        jdbc.update("update inventories set on_hand_qty=5 where inventory_id=?", inventory);
        service.confirm(id);
        assertThat(number("on_hand_qty")).isEqualByComparingTo("0");
        assertThat(number("reserved_qty")).isEqualByComparingTo("0");
        assertThat(reservations.findByOrder(order).getFirst().consumedQty()).isEqualByComparingTo("5");
        service.confirm(id);
        assertThat(movements()).isEqualTo(1);
    }
    @Test void codCommitmentCannotBeAllocatedTwiceAndFailedStillCountsAsIssued() {
        UUID a=draft("5","70"), b=draft("5","40"); service.confirm(a);
        jdbc.update("update deliveries set delivery_status='FAILED' where delivery_id=?",a);
        assertThatThrownBy(() -> service.confirm(b)).isInstanceOf(AppException.class);
        assertThat(service.candidates(order).items().getFirst().remainingQty()).isEqualByComparingTo("5");
        assertThat(number("on_hand_qty")).isEqualByComparingTo("15");
    }
    @Test void rejectsDraftOrderAndSerialTrackedSkuAndUnauthorizedActor() {
        jdbc.update("update orders set order_status='DRAFT' where order_id=?",order);
        assertThatThrownBy(() -> draft("1","0")).isInstanceOf(AppException.class);
        jdbc.update("update orders set order_status='PROCESSING' where order_id=?",order);
        when(catalog.getSkuSummary(variant)).thenReturn(Optional.of(CatalogSkuSummaryDto.builder().variantId(variant).serialTracking(true).build()));
        assertThat(service.invoiceCandidates(invoice).items().getFirst().serialTracking()).isTrue();
        assertThatThrownBy(() -> draft("1","0")).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo("FUL_005"));
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("SHIPPER")).build());
        assertThatThrownBy(() -> service.candidates(order)).isInstanceOf(AppException.class);
    }
    @Test void laterLineFailureRollsBackEarlierStockAndConsumption() {
        UUID otherVariant=UUID.randomUUID(), otherItem=UUID.randomUUID(), otherInventory=UUID.randomUUID();
        UUID product=jdbc.queryForObject("select product_id from product_variants where variant_id=?",UUID.class,variant);
        jdbc.update("insert into product_variants(variant_id,product_id,sku,variant_name,unit) values(?,?,?,?,?)",otherVariant,product,"SKU-"+otherVariant,"Other","piece");
        jdbc.update("insert into order_items(order_item_id,order_id,variant_id,quantity,line_total) values(?,?,?,2,0)",otherItem,order,otherVariant);
        jdbc.update("insert into sales_invoice_items(invoice_item_id,invoice_id,order_item_id,variant_id,quantity,unit_price,line_total) values(?,?,?,?,2,0,0)",
                UUID.randomUUID(),invoice,otherItem,otherVariant);
        jdbc.update("insert into inventories(inventory_id,variant_id,warehouse_code,on_hand_qty,reserved_qty,minimum_qty,updated_at) values(?,?,'WH_MAIN',20,0,0,current_timestamp)",otherInventory,otherVariant);
        when(catalog.getSkuSummary(otherVariant)).thenReturn(Optional.of(CatalogSkuSummaryDto.builder().variantId(otherVariant).serialTracking(false).build()));
        UUID id=UUID.randomUUID();
        service.create(order,new DeliveryIssueRequest(id,"WH_MAIN","Recipient","0900000000","Address",null,BigDecimal.ZERO,null,
                List.of(new DeliveryIssueRequest.Line(item,new BigDecimal("5")),new DeliveryIssueRequest.Line(otherItem,BigDecimal.ONE))));
        UUID last=variant.compareTo(otherVariant)>0?variant:otherVariant;
        jdbc.update("update inventories set on_hand_qty=0 where variant_id=?",last);
        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(AppException.class);
        UUID first=last.equals(variant)?otherVariant:variant;
        assertThat(jdbc.queryForObject("select on_hand_qty from inventories where variant_id=?",BigDecimal.class,first)).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("select count(*) from stock_movements where reference_id=?",Integer.class,id.toString())).isZero();
        assertThat(reservations.findByOrder(order)).isEmpty();
    }
    @Test void pendingCodIsNotCountedTwiceAgainstCommitment() {
        UUID first=draft("5","70"); service.confirm(first);
        jdbc.update("update deliveries set delivery_status='DELIVERED' where delivery_id=?",first);
        UUID receipt=UUID.randomUUID();
        jdbc.update("""
                insert into transactions(transaction_id,transaction_code,transaction_type,payment_type,category,
                customer_id,order_id,delivery_id,amount,payment_method,status,transaction_date,created_by,idempotency_key)
                values(?,?,'RECEIPT','COD','CUSTOMER_PAYMENT',?,?,?,30,'CASH','PENDING',current_timestamp,?,?)
                """,receipt,"T-"+receipt,customer,order,first,actor,UUID.randomUUID());
        UUID second=draft("5","30"); service.confirm(second);
        assertThat(movements()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select payment_status from orders where order_id=?",String.class,order)).isEqualTo("UNPAID");
    }
    @Test void issuedInvoicePickerExcludesDraftsAndOtherOrders() {
        UUID draftInvoice=addInvoice(order,BigDecimal.ONE,"NORMAL");
        jdbc.update("update sales_invoices set status='DRAFT' where invoice_id=?",draftInvoice);
        assertThat(service.issuedInvoices(order)).extracting(i -> i.invoiceId()).containsExactly(invoice);
        assertThatThrownBy(() -> service.invoiceCandidates(draftInvoice)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.createFromInvoice(draftInvoice,request(UUID.randomUUID(),"1","0")))
                .isInstanceOf(AppException.class);
        assertThat(service.candidates(order).salesInvoiceId()).isEqualTo(invoice);
    }
    @Test void orderWithoutIssuedInvoiceHasEmptyPickerAndCannotIssue() {
        jdbc.update("update sales_invoices set status='DRAFT' where invoice_id=?",invoice);
        assertThat(service.issuedInvoices(order)).isEmpty();
        assertThatThrownBy(() -> service.create(order,request(UUID.randomUUID(),"1","0"))).isInstanceOf(AppException.class);
        assertThat(movements()).isZero();
    }
    @Test void invoiceMustStillBeIssuedAtConfirmation() {
        UUID id=draft("5","0");
        jdbc.update("update sales_invoices set status='CANCELLED' where invoice_id=?",invoice);
        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(AppException.class);
        assertThat(movements()).isZero();
    }
    @Test void issuedInvoiceCanFlowThroughAssignmentToCustomerRefusalWithoutRestocking() {
        UUID id=draft("4","0");
        service.confirm(id);
        var assigned=assignToShipper(id);
        assertThat(assigned.confirmedAt()).isNotNull();
        assertThat(assigned.dispatchDate()).isNotNull();
        deliveryService.start(id);
        var fail=new com.aquasuite.ims.module.fulfillment.internal.payload.request.DeliveryRequests.Fail("Khách từ chối nhận hàng",true);
        var failed=deliveryService.fail(id,fail);
        assertThat(failed.deliveryStatus()).isEqualTo("FAILED");
        assertThat(failed.note()).contains("Khách từ chối nhận hàng");
        assertThat(deliveryService.fail(id,fail).note()).isEqualTo(failed.note());
        assertThatThrownBy(() -> deliveryService.start(id)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> deliveryService.complete(id,List.of())).isInstanceOf(AppException.class);
        assertThat(jdbc.queryForObject("select on_hand_qty from inventories where inventory_id=?",BigDecimal.class,inventory)).isEqualByComparingTo("16");
        assertThat(serviceHistoryStatus()).isEqualTo("PROCESSING/UNPAID");
        verifyNoInteractions(photoStorage);
    }
    @Test void issuedInvoiceCanFlowThroughPhotoCompletionWithoutPayingOrCompletingOrder() throws Exception {
        UUID id=draft("4","50");service.confirm(id);assignToShipper(id);deliveryService.start(id);
        var image=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        when(photoStorage.store(any(),eq("image/png"))).thenReturn(
                new com.aquasuite.ims.module.fulfillment.internal.storage.DeliveryPhotoStorage.Stored("proof-key"));
        var photo=new org.springframework.mock.web.MockMultipartFile("photos","proof.png","image/png",bytes.toByteArray());
        var completed=deliveryService.complete(id,List.of(photo));
        assertThat(completed.deliveryStatus()).isEqualTo("DELIVERED");
        assertThat(completed.deliveredAt()).isNotNull();
        assertThat(deliveryService.complete(id,List.of()).deliveredAt()).isEqualTo(completed.deliveredAt());
        assertThat(serviceHistoryStatus()).isEqualTo("PROCESSING/UNPAID");
        assertThat(jdbc.queryForObject("select count(*) from transactions where order_id=?",Integer.class,order)).isZero();
        assertThat(jdbc.queryForObject("select jsonb_array_length(proof_photos) from deliveries where delivery_id=?",Integer.class,id)).isEqualTo(1);
        verify(photoStorage,times(1)).store(any(),eq("image/png"));
    }
    private String serviceHistoryStatus() {
        return jdbc.queryForObject("select order_status||'/'||payment_status from orders where order_id=?",String.class,order);
    }
    @Test void rescheduleAndReassignReuseTheDeliveryAndDoNotIssueStockTwice() {
        UUID id=draft("4","50");service.confirm(id);assignToShipper(id);
        var oldShipper=iam.getCurrentUser();
        deliveryService.start(id);
        var pickedUp=jdbc.queryForObject("select started_at from deliveries where delivery_id=?",java.time.OffsetDateTime.class,id);
        var request=new com.aquasuite.ims.module.fulfillment.internal.payload.request.DeliveryRequests.Reschedule(
                java.time.OffsetDateTime.now().plusDays(1),"Khách hẹn giao ngày mai");
        var postponed=deliveryService.reschedule(id,request);
        assertThat(postponed.deliveryStatus()).isEqualTo("RESCHEDULED");
        assertThat(deliveryService.reschedule(id,request).note()).isEqualTo(postponed.note());
        UUID nextShipper=UUID.randomUUID();
        var next=UserSummaryDto.builder().id(nextShipper).status("ACTIVE").roles(List.of("SHIPPER")).build();
        when(iam.getUserById(nextShipper)).thenReturn(next);
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("ACCOUNTANT")).build());
        var reassigned=deliveryService.assign(new com.aquasuite.ims.module.fulfillment.internal.payload.request.DeliveryRequests.Assign(nextShipper,List.of(id))).getFirst();
        assertThat(reassigned.deliveryStatus()).isEqualTo("RESCHEDULED");
        when(iam.getCurrentUser()).thenReturn(oldShipper);
        assertThatThrownBy(() -> deliveryService.start(id)).isInstanceOf(AppException.class);
        when(iam.getCurrentUser()).thenReturn(next);
        var resumed=deliveryService.start(id);
        assertThat(resumed.deliveryStatus()).isEqualTo("IN_TRANSIT");
        assertThat(resumed.startedAt()).isEqualTo(pickedUp);
        assertThat(resumed.scheduledDate()).isEqualTo(postponed.scheduledDate());
        assertThat(movements()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from deliveries where order_id=?",Integer.class,order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select on_hand_qty from inventories where inventory_id=?",BigDecimal.class,inventory)).isEqualByComparingTo("16");
        assertThat(serviceHistoryStatus()).isEqualTo("PROCESSING/UNPAID");
        assertThat(resumed.codAmount()).isEqualByComparingTo("50");
    }
    private com.aquasuite.ims.module.fulfillment.internal.payload.response.DeliveryView.Summary assignToShipper(UUID id) {
        UUID shipper=UUID.randomUUID();
        var user=UserSummaryDto.builder().id(shipper).status("ACTIVE").roles(List.of("SHIPPER")).build();
        when(iam.getUserById(shipper)).thenReturn(user);
        var result=deliveryService.assign(new com.aquasuite.ims.module.fulfillment.internal.payload.request.DeliveryRequests.Assign(shipper,List.of(id))).getFirst();
        when(iam.getCurrentUser()).thenReturn(user);
        return result;
    }
    @Test void invoiceIsLinkedAndLimitsDeliverableQuantity() {
        jdbc.update("update sales_invoice_items set quantity=3 where invoice_id=?",invoice);
        assertThat(service.invoiceCandidates(invoice).items().getFirst().remainingQty()).isEqualByComparingTo("3");
        assertThatThrownBy(() -> service.createFromInvoice(invoice,request(UUID.randomUUID(),"4","0"))).isInstanceOf(AppException.class);
        var d=service.createFromInvoice(invoice,request(UUID.randomUUID(),"3","0"));
        assertThat(d.salesInvoiceId()).isEqualTo(invoice);
        service.confirm(d.deliveryId());
        assertThat(jdbc.queryForObject("select sales_invoice_id from deliveries where delivery_id=?",UUID.class,d.deliveryId())).isEqualTo(invoice);
        assertThat(service.invoiceCandidates(invoice).items().getFirst().remainingQty()).isEqualByComparingTo("0");
    }
    @Test void invoiceQuantityIsRecheckedAtConfirmation() {
        UUID id=draft("5","0");
        jdbc.update("update sales_invoice_items set quantity=4 where invoice_id=?",invoice);
        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(AppException.class);
        assertThat(movements()).isZero();
    }
    @Test void orderWithoutInvoiceCannotCreateDeliveryAndDoesNotInventInvoice() {
        jdbc.update("delete from sales_invoice_items where invoice_id=?",invoice);
        jdbc.update("delete from sales_invoices where invoice_id=?",invoice);
        assertThatThrownBy(() -> draft("1","0")).isInstanceOf(AppException.class);
        assertThat(jdbc.queryForObject("select count(*) from sales_invoices where order_id=?",Integer.class,order)).isZero();
    }
    @Test void multipleInvoicesCannotShipMoreThanOrderAndRequireExplicitInvoice() {
        UUID second=addInvoice(order,new BigDecimal("7"),"NORMAL");
        assertThatThrownBy(() -> draft("1","0")).isInstanceOf(AppException.class);
        var a=service.createFromInvoice(invoice,request(UUID.randomUUID(),"6","0"));
        var b=service.createFromInvoice(second,request(UUID.randomUUID(),"6","0"));
        service.confirm(a.deliveryId());
        assertThatThrownBy(() -> service.confirm(b.deliveryId())).isInstanceOf(AppException.class);
        assertThat(number("on_hand_qty")).isEqualByComparingTo("14");
    }
    @Test void foreignInvoiceOrderCannotBeLinkedInDatabase() {
        UUID id=draft("1","0");
        UUID other=UUID.randomUUID();
        jdbc.update("insert into orders(order_id,order_code,customer_id,order_channel,order_status,payment_status,order_date,total_amount) values(?,?,?,'IN_STORE','PROCESSING','UNPAID',current_timestamp,100)",other,"O-"+other,customer);
        assertThatThrownBy(() -> jdbc.update("update deliveries set order_id=? where delivery_id=?",other,id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void splitInvoicesFourAndSixCanEachDeliverTheirOwnPart() {
        UUID first=addInvoice(order,new BigDecimal("4"),"NORMAL");
        UUID second=addInvoice(order,new BigDecimal("6"),"NORMAL");
        var a=service.createFromInvoice(first,request(UUID.randomUUID(),"4","0"));
        var b=service.createFromInvoice(second,request(UUID.randomUUID(),"6","0"));
        service.confirm(a.deliveryId());service.confirm(b.deliveryId());
        assertThat(service.invoiceCandidates(first).items().getFirst().remainingQty()).isEqualByComparingTo("0");
        assertThat(service.invoiceCandidates(second).items().getFirst().remainingQty()).isEqualByComparingTo("0");
        assertThat(number("on_hand_qty")).isEqualByComparingTo("10");
    }
}
