package com.aquasuite.ims.module.finance.internal.repository;

import com.aquasuite.ims.module.finance.internal.payload.CodPayload.*;
import com.aquasuite.ims.module.finance.internal.error.CodErrorCode;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Only COD-owned tables are accessed here; existing payments remain JPA Transactions. */
@Repository
@RequiredArgsConstructor
public class CodRepository {
    private final JdbcTemplate jdbc;
    private static final String SELECT = """
        SELECT c.collection_id,c.delivery_id,c.order_id,c.customer_id,c.shipper_id,c.delivery_code,
        c.expected_amount,c.amount,c.review_status,c.collected_at,c.submitted_at,c.reviewed_by,c.reviewed_at,
        c.reject_reason,c.note,c.version, COALESCE((SELECT sum(i.amount) FROM cod_remittance_items i
        WHERE i.collection_id=c.collection_id),0) AS remitted FROM cod_collections c
        """;
    private static final RowMapper<Collection> MAPPER = (rs,n) -> {
        BigDecimal amount=rs.getBigDecimal("amount"), remitted=rs.getBigDecimal("remitted");
        boolean accepted="ACCEPTED".equals(rs.getString("review_status"));
        BigDecimal held=accepted ? amount.subtract(remitted) : BigDecimal.ZERO;
        return new Collection(rs.getObject("collection_id",UUID.class),rs.getObject("delivery_id",UUID.class),
                rs.getObject("order_id",UUID.class),rs.getObject("customer_id",UUID.class),rs.getObject("shipper_id",UUID.class),
                rs.getString("delivery_code"),rs.getBigDecimal("expected_amount"),amount,rs.getString("review_status"),
                rs.getObject("collected_at",OffsetDateTime.class),rs.getObject("submitted_at",OffsetDateTime.class),
                rs.getObject("reviewed_by",UUID.class),rs.getObject("reviewed_at",OffsetDateTime.class),
                rs.getString("reject_reason"),rs.getString("note"),rs.getLong("version"),remitted,held,
                !accepted ? null : held.signum()==0 ? "SETTLED" : remitted.signum()==0 ? "PENDING_REMITTANCE" : "PARTIAL_REMITTANCE");
    };

    public Collection find(UUID id) {
        return jdbc.query(SELECT+" WHERE c.collection_id=?",MAPPER,id).stream().findFirst()
                .orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND));
    }
    public List<Collection> byKey(UUID key) { return jdbc.query(SELECT+" WHERE c.idempotency_key=?",MAPPER,key); }
    public String claimHash(UUID id) {
        return jdbc.queryForObject("SELECT request_hash FROM cod_collections WHERE collection_id=?",String.class,id);
    }
    public BigDecimal held(UUID order) {
        return jdbc.queryForObject("""
            SELECT COALESCE(sum(c.amount-COALESCE((SELECT sum(i.amount) FROM cod_remittance_items i
            WHERE i.collection_id=c.collection_id),0)),0) FROM cod_collections c
            WHERE c.order_id=? AND c.review_status='ACCEPTED'
            """,BigDecimal.class,order);
    }
    public BigDecimal pending(UUID order) {
        return jdbc.queryForObject("SELECT COALESCE(sum(amount),0) FROM cod_collections WHERE order_id=? AND review_status='SUBMITTED'",
                BigDecimal.class,order);
    }
    public BigDecimal claimed(UUID delivery) {
        return jdbc.queryForObject("SELECT COALESCE(sum(amount),0) FROM cod_collections WHERE delivery_id=? AND review_status<>'REJECTED'",
                BigDecimal.class,delivery);
    }
    public boolean customerHasOpenCod(UUID customer) {
        return jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM cod_collections c WHERE c.customer_id=? AND
            (c.review_status='SUBMITTED' OR (c.review_status='ACCEPTED' AND c.amount>
            COALESCE((SELECT sum(i.amount) FROM cod_remittance_items i WHERE i.collection_id=c.collection_id),0))))
            """,Boolean.class,customer);
    }
    public void requireCustomerReconciled(UUID customer) {
        // Serialize outgoing debt actions with collection/review/remittance on these orders.
        jdbc.query("SELECT order_id FROM orders WHERE customer_id=? ORDER BY order_id FOR UPDATE",
                (rs,n)->rs.getObject(1,UUID.class),customer);
        if (customerHasOpenCod(customer)) throw new AppException(CodErrorCode.RECONCILE_FIRST);
    }
    public Page<Collection> list(UUID shipper, UUID order, String status, int page, int size) {
        String where=" WHERE (?::uuid IS NULL OR c.shipper_id=?) AND (?::uuid IS NULL OR c.order_id=?) "
                +"AND (?::varchar IS NULL OR c.review_status=?)";
        Object[] args={shipper,shipper,order,order,status,status};
        long total=jdbc.queryForObject("SELECT count(*) FROM cod_collections c"+where,Long.class,args);
        List<Collection> rows=jdbc.query(SELECT+where+" ORDER BY c.submitted_at DESC,c.collection_id LIMIT ? OFFSET ?",MAPPER,
                shipper,shipper,order,order,status,status,size,(long)page*size);
        return new Page<>(rows,page,size,total,(int)((total+size-1)/size));
    }
    public Receipt receipt(UUID id) {
        List<ReceiptItem> items=jdbc.query("""
            SELECT i.collection_id,c.order_id,i.transaction_id,i.amount FROM cod_remittance_items i
            JOIN cod_collections c USING(collection_id) WHERE i.remittance_id=? ORDER BY i.collection_id
            """,(rs,n)->new ReceiptItem(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),
                rs.getObject(3,UUID.class),rs.getBigDecimal(4)),id);
        return jdbc.query("SELECT * FROM cod_remittances WHERE remittance_id=?",(rs,n)->new Receipt(id,
                rs.getObject("shipper_id",UUID.class),rs.getObject("received_by",UUID.class),
                rs.getObject("received_at",OffsetDateTime.class),rs.getBigDecimal("amount"),rs.getString("note"),items),id)
                .stream().findFirst().orElseThrow(()->new AppException(CommonErrorCode.RESOURCE_NOT_FOUND));
    }
    public List<Receipt> receipts(UUID collection) {
        return jdbc.query("SELECT remittance_id FROM cod_remittance_items WHERE collection_id=? ORDER BY remittance_id",
                (rs,n)->rs.getObject(1,UUID.class),collection).stream().map(this::receipt).toList();
    }
    public void insertClaim(UUID id, Claim claim, UUID order, UUID customer, UUID shipper,
                            String code, BigDecimal expected, String hash) {
        jdbc.update("""
            INSERT INTO cod_collections(collection_id,delivery_id,order_id,customer_id,shipper_id,delivery_code,
            expected_amount,amount,collected_at,note,idempotency_key,request_hash)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,claim.deliveryId(),order,customer,shipper,code,expected,claim.amount(),claim.collectedAt(),
                claim.note(),claim.idempotencyKey(),hash);
    }
    public void review(UUID id, String status, UUID actor, String reason) {
        jdbc.update("UPDATE cod_collections SET review_status=?,reviewed_by=?,reviewed_at=now(),reject_reason=?,version=version+1 WHERE collection_id=?",
                status,actor,reason,id);
    }
    public UUID receiptByKey(UUID key, String hash, UUID actor) {
        return jdbc.query("SELECT remittance_id,request_hash,received_by FROM cod_remittances WHERE idempotency_key=?",
                (rs,n)-> {
                    if (!hash.equals(rs.getString(2)) || !actor.equals(rs.getObject(3,UUID.class)))
                        throw new AppException(CodErrorCode.DUPLICATE_KEY);
                    return rs.getObject(1,UUID.class);
                },key).stream().findFirst().orElse(null);
    }
    public void insertReceipt(UUID id, Remit remit, UUID actor, String hash) {
        jdbc.update("INSERT INTO cod_remittances(remittance_id,shipper_id,received_by,amount,note,idempotency_key,request_hash) VALUES (?,?,?,?,?,?,?)",
                id,remit.shipperId(),actor,remit.amount(),remit.note(),remit.idempotencyKey(),hash);
    }
    public void allocate(UUID receipt, UUID collection, UUID transaction, BigDecimal amount) {
        jdbc.update("INSERT INTO cod_remittance_items(remittance_id,collection_id,transaction_id,amount) VALUES (?,?,?,?)",
                receipt,collection,transaction,amount);
        jdbc.update("UPDATE cod_collections SET version=version+1 WHERE collection_id=?",collection);
    }
}
