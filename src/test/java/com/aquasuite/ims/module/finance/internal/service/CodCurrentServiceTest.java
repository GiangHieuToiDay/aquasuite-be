package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.service.impl.CodService;
import com.aquasuite.ims.module.finance.internal.repository.*;
import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.payload.CodPayload.*;
import com.aquasuite.ims.module.fulfillment.api.*;
import com.aquasuite.ims.module.fulfillment.api.dto.*;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class CodCurrentServiceTest {
    TransactionRepository repo; FinanceLockRepository locks;
    SalesOrderApi sales; DeliveryCodQueryApi deliveries; IamApi iam; CodService service;
    UUID shipper=UUID.randomUUID(), accountant=UUID.randomUUID(), order=UUID.randomUUID(), customer=UUID.randomUUID(), delivery=UUID.randomUUID(), id=UUID.randomUUID();
    Transaction t;
    @BeforeEach void setup() {
        repo=mock(TransactionRepository.class); locks=mock(FinanceLockRepository.class);
        sales=mock(SalesOrderApi.class); deliveries=mock(DeliveryCodQueryApi.class); iam=mock(IamApi.class);
        service=new CodService(repo,locks,sales,deliveries,iam);
        t=Transaction.builder().transactionId(id).transactionCode("COD-"+id).transactionType("RECEIPT")
                .paymentType("COD").paymentMethod("CASH").deliveryId(delivery).orderId(order).customerId(customer)
                .createdBy(shipper).amount(new BigDecimal("100")).status("PENDING")
                .transactionDate(OffsetDateTime.now()).idempotencyKey(UUID.randomUUID()).build();
        var info=SalesOrderPaymentInfoDto.builder().orderId(order).customerId(customer).totalAmount(new BigDecimal("100")).validForPayment(true).build();
        when(sales.lockOrderPaymentInfo(order)).thenReturn(Optional.of(info));
        when(sales.getOrderPaymentInfo(order)).thenReturn(Optional.of(info));
        when(repo.findByOrderIdAndTransactionTypeIgnoreCase(order,"RECEIPT")).thenReturn(List.of());
        when(repo.findOrderIdById(id)).thenReturn(Optional.of(order));
        when(repo.findByIdForUpdate(id)).thenReturn(Optional.of(t));
        when(repo.findById(id)).thenReturn(Optional.of(t));
        var snapshot=new DeliveryCodSnapshot(delivery,order,shipper,"D1","DELIVERED",true,new BigDecimal("100"));
        when(deliveries.find(delivery)).thenReturn(Optional.of(snapshot));
        when(deliveries.lock(delivery)).thenReturn(Optional.of(snapshot));
        as(shipper,"SHIPPER");
    }
    void as(UUID who,String...roles) {
        when(iam.getCurrentUserId()).thenReturn(who);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(who,"unused",
                Arrays.stream(roles).map(r->new SimpleGrantedAuthority("ROLE_"+r)).toList()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    Claim claim() { return new Claim(delivery,new BigDecimal("100"),t.getTransactionDate(),null,t.getIdempotencyKey()); }
    @Test void declarationCreatesPendingTransactionWithoutPayingOrder() {
        var result=service.claim(claim());
        assertThat(result.status()).isEqualTo("PENDING");
        verify(repo).saveAndFlush(argThat(v->delivery.equals(v.getDeliveryId()) && "COD".equals(v.getPaymentType()) && "PENDING".equals(v.getStatus())));
        verify(sales,never()).updatePaymentStatus(any(),any());
    }
    @Test void identicalClaimRetryReturnsSameTransaction() {
        when(repo.findByIdempotencyKey(t.getIdempotencyKey())).thenReturn(Optional.of(t));
        assertThat(service.claim(claim()).collectionId()).isEqualTo(id);
        verify(repo,never()).saveAndFlush(any());
    }
    @Test void changedClaimRetryIsRejected() {
        when(repo.findByIdempotencyKey(t.getIdempotencyKey())).thenReturn(Optional.of(t));
        assertThatThrownBy(()->service.claim(new Claim(delivery,BigDecimal.ONE,t.getTransactionDate(),null,t.getIdempotencyKey()))).isInstanceOf(AppException.class);
    }
    @Test void ownerReceivesEntireAmountAndPaysOrder() {
        as(accountant,"SHOP_OWNER");
        var result=service.review(id,new Review(0,null),true);
        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(t.getApprovedBy()).isEqualTo(accountant);
        verify(repo).saveAndFlush(t); verify(sales).updatePaymentStatus(order,"PAID");
    }
    @Test void accountantRetainsAccess() {
        as(accountant,"ACCOUNTANT"); service.review(id,new Review(0,null),true);
        verify(sales).updatePaymentStatus(order,"PAID");
    }
    @Test void ownerCannotReceiveOwnDeclaredCash() {
        as(shipper,"SHOP_OWNER");
        assertThatThrownBy(()->service.review(id,new Review(0,null),true)).isInstanceOf(AppException.class);
        verify(repo,never()).saveAndFlush(any());
    }
    @Test void rejectionDoesNotPayOrderAndRequiresReason() {
        as(accountant,"ACCOUNTANT");
        assertThatThrownBy(()->service.review(id,new Review(0," "),false)).isInstanceOf(AppException.class);
        service.review(id,new Review(0,"Wrong amount"),false);
        assertThat(t.getStatus()).isEqualTo("REJECTED");
        verify(sales,never()).updatePaymentStatus(any(),any());
    }
    @Test void acceptRetryDoesNotApplyPaymentTwice() {
        as(accountant,"ACCOUNTANT");
        service.review(id,new Review(0,null),true); service.review(id,new Review(0,null),true);
        verify(sales,times(1)).updatePaymentStatus(order,"PAID");
        verify(repo,times(1)).saveAndFlush(t);
    }
    @Test void staleDecisionRejected() {
        as(accountant,"ACCOUNTANT");
        assertThatThrownBy(()->service.review(id,new Review(1,null),true)).isInstanceOf(AppException.class);
        verify(repo,never()).saveAndFlush(any());
    }
    @Test void sourceAmountDriftBlocksReceiving() {
        as(accountant,"ACCOUNTANT");
        when(deliveries.lock(delivery)).thenReturn(Optional.of(new DeliveryCodSnapshot(delivery,order,shipper,"D1","DELIVERED",true,BigDecimal.TEN)));
        assertThatThrownBy(()->service.review(id,new Review(0,null),true)).isInstanceOf(AppException.class);
    }
    @Test void pendingCodReducesCustomerCollectibleButNotVerifiedPaid() {
        as(accountant,"SHOP_OWNER");
        when(repo.findByOrderIdAndTransactionTypeIgnoreCase(order,"RECEIPT")).thenReturn(List.of(t));
        var result=service.balance(order);
        assertThat(result.storeOutstanding()).isEqualByComparingTo("100");
        assertThat(result.shipperHeld()).isEqualByComparingTo("100");
        assertThat(result.customerCollectible()).isZero();
    }
    @Test void verifiedDepositWithoutCodTypeReducesOutstandingAndCustomerCollectible() {
        as(accountant,"ACCOUNTANT");
        var info = SalesOrderPaymentInfoDto.builder().orderId(order).customerId(customer)
                .totalAmount(new BigDecimal("17050000")).validForPayment(true).build();
        when(sales.getOrderPaymentInfo(order)).thenReturn(Optional.of(info));
        var deposit = Transaction.builder().orderId(order).customerId(customer).transactionType("RECEIPT")
                .status("VERIFIED").amount(new BigDecimal("3410000")).build();
        when(repo.findByOrderIdAndTransactionTypeIgnoreCase(order,"RECEIPT")).thenReturn(List.of(deposit));
        var result = service.balance(order);
        assertThat(result.storeOutstanding()).isEqualByComparingTo("13640000");
        assertThat(result.customerCollectible()).isEqualByComparingTo("13640000");
        assertThat(result.shipperHeld()).isZero();
        assertThat(result.pendingClaimAmount()).isZero();
    }
    @Test void anotherShipperCannotReadDeclaration() {
        as(UUID.randomUUID(),"SHIPPER");
        assertThatThrownBy(()->service.detail(id)).isInstanceOf(AppException.class);
    }
    @Test void anotherPendingDeclarationCannotExceedOrder() {
        when(repo.findByOrderIdAndTransactionTypeIgnoreCase(order,"RECEIPT")).thenReturn(List.of(t));
        assertThatThrownBy(()->service.claim(claim())).isInstanceOf(AppException.class);
        verify(repo,never()).saveAndFlush(any());
    }
}
