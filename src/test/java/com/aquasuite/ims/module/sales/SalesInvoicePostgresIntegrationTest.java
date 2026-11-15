package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.sales.api.SalesInvoiceApi;
import com.aquasuite.ims.testsupport.AuthIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack HTTP + PostgreSQL (Flyway V1..V31) + Redis + JWT Security integration test
 * gửi HTTP requests thực tế qua TestRestTemplate giống như Postman.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SalesInvoicePostgresIntegrationTest extends AuthIntegrationTestSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SalesInvoiceApi salesInvoiceApi;

    private UUID salesStaffId;
    private UUID warehouseKeeperId;
    private UUID customerId;
    private UUID categoryId;
    private UUID brandId;
    private UUID productId;
    private UUID variant1Id;
    private UUID variant2Id;
    private UUID quotationId;

    private String salesToken;
    private String warehouseToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanTestRows();

        salesStaffId = UUID.randomUUID();
        warehouseKeeperId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        brandId = UUID.randomUUID();
        productId = UUID.randomUUID();
        variant1Id = UUID.randomUUID();
        variant2Id = UUID.randomUUID();
        quotationId = UUID.randomUUID();

        String hash = passwordEncoder.encode("Password123!");

        jdbc.update("INSERT INTO public.users (id, username, email, full_name, password_hash, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                salesStaffId, "inv_sales_e2e", "inv_sales_e2e@aquasuite.local", "Lâm Sales E2E", hash);
        jdbc.update("INSERT INTO public.user_roles (user_id, role_id) SELECT ?, role_id FROM public.roles WHERE role_name = 'SALES_STAFF'",
                salesStaffId);

        jdbc.update("INSERT INTO public.users (id, username, email, full_name, password_hash, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                warehouseKeeperId, "inv_wh_e2e", "inv_wh_e2e@aquasuite.local", "Kế toán E2E", hash);
        jdbc.update("INSERT INTO public.user_roles (user_id, role_id) SELECT ?, role_id FROM public.roles WHERE role_name = 'ACCOUNTANT'",
                warehouseKeeperId);

        jdbc.update("INSERT INTO public.customers (customer_id, customer_code, customer_name, phone, customer_type, status, credit_limit, credit_term_days) VALUES (?, ?, ?, ?, 'PROJECT_CUSTOMER', 'ACTIVE', 200000000, 15)",
                customerId, "CUS-INV-E2E", "Khách hàng E2E Công Trình", "0988777666");

        jdbc.update("INSERT INTO public.categories (category_id, category_code, category_name, status) VALUES (?, 'CAT_INV_E2E', 'Thiết bị vệ sinh E2E', 'ACTIVE')",
                categoryId);
        jdbc.update("INSERT INTO public.brands (brand_id, brand_code, brand_name, status) VALUES (?, 'BRD_INV_E2E', 'TOTO E2E', 'ACTIVE')",
                brandId);
        jdbc.update("INSERT INTO public.products (product_id, product_code, category_id, brand_id, product_name, product_type, status) VALUES (?, 'PRD-INV-E2E', ?, ?, 'Bộ thiết bị TOTO E2E', 'NORMAL', 'ACTIVE')",
                productId, categoryId, brandId);

        jdbc.update("INSERT INTO public.product_variants (variant_id, product_id, sku, variant_name, unit, cost_price, selling_price, status) VALUES (?, ?, 'E2E-SKU-001', 'Bồn cầu E2E', 'cái', 3000000, 5000000, 'ACTIVE')",
                variant1Id, productId);
        jdbc.update("INSERT INTO public.product_variants (variant_id, product_id, sku, variant_name, unit, cost_price, selling_price, status) VALUES (?, ?, 'E2E-SKU-002', 'Lavabo E2E', 'cái', 1200000, 2000000, 'ACTIVE')",
                variant2Id, productId);

        jdbc.update("INSERT INTO public.inventories (inventory_id, variant_id, warehouse_code, on_hand_qty, reserved_qty, minimum_qty, updated_at) VALUES (?, ?, 'WH_MAIN', 100, 0, 5, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), variant1Id);
        jdbc.update("INSERT INTO public.inventories (inventory_id, variant_id, warehouse_code, on_hand_qty, reserved_qty, minimum_qty, updated_at) VALUES (?, ?, 'WH_MAIN', 100, 0, 5, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), variant2Id);

        jdbc.update("""
                INSERT INTO public.quotations (
                    quotation_id, quotation_code, revision_no, customer_id, project_name,
                    delivery_schedule, vat_rate, shipping_fee, credit_term_days,
                    status, quotation_date, valid_until, total_amount, created_by
                ) VALUES (?, 'BG-E2E-001', 1, ?, 'Dự án E2E AquaSuite', 'Giao 2 đợt', 10.00, 0, 15, 'APPROVED', CURRENT_DATE, ?, 66000000, ?)
                """, quotationId, customerId, Date.valueOf(LocalDate.now().plusDays(15)), salesStaffId);

        jdbc.update("INSERT INTO public.quotation_items (quotation_item_id, quotation_id, variant_id, sku, unit, quantity, unit_price, discount_rate, line_total, sort_order) VALUES (?, ?, ?, 'E2E-SKU-001', 'cái', 10, 5000000, 0, 50000000, 1)",
                UUID.randomUUID(), quotationId, variant1Id);
        jdbc.update("INSERT INTO public.quotation_items (quotation_item_id, quotation_id, variant_id, sku, unit, quantity, unit_price, discount_rate, line_total, sort_order) VALUES (?, ?, ?, 'E2E-SKU-002', 'cái', 5, 2000000, 0, 10000000, 2)",
                UUID.randomUUID(), quotationId, variant2Id);

        salesToken = login("inv_sales_e2e@aquasuite.local", "Password123!");
        warehouseToken = login("inv_wh_e2e@aquasuite.local", "Password123!");
    }

    @AfterEach
    void tearDown() {
        cleanTestRows();
    }

    @Test
    @DisplayName("E2E HTTP + PostgreSQL V31: RETAIL STORE Invoice & PROJECT Multi-batch Workflow")
    void endToEndHttpRetailAndProjectInvoiceLifecycleOnRealPostgres() throws Exception {
        // =====================================================================
        // 1. LUỒNG ĐƠN BÁN LẺ (RETAIL POS) -> STORE DRAFT -> EDIT -> ISSUE
        // =====================================================================
        Map<String, Object> retailOrderReq = Map.of(
                "customerId", customerId.toString(),
                "orderChannel", "POS",
                "orderType", "RETAIL",
                "fulfillmentMethod", "IN_STORE_PICKUP",
                "paymentMethod", "CASH",
                "discountAmount", 500000,
                "items", List.of(Map.of("variantId", variant1Id.toString(), "quantity", 2))
        );

        ResponseEntity<String> createRetailRes = exchange("/api/v1/sales/orders", HttpMethod.POST, retailOrderReq, salesToken);
        assertThat(createRetailRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode retailData = objectMapper.readTree(createRetailRes.getBody()).get("data");
        String retailOrderId = retailData.get("orderId").asText();
        String retailInvoiceId = retailData.get("storeInvoiceId").asText();
        assertThat(retailData.get("totalInvoiceCount").asInt()).isEqualTo(1);
        assertThat(retailData.get("draftInvoiceCount").asInt()).isEqualTo(1);

        // Kiểm tra DB thực tế đã giữ 2 sản phẩm SKU-001
        BigDecimal reservedV1AfterCreate = jdbc.queryForObject(
                "SELECT reserved_qty FROM public.inventories WHERE variant_id = ?", BigDecimal.class, variant1Id);
        assertThat(reservedV1AfterCreate).isEqualByComparingTo("2.00");

        // Chỉnh sửa hóa đơn STORE khi đang DRAFT: tăng SKU-001 lên 3, thêm SKU-002 slg 1, discount = 1M => 16M
        Map<String, Object> editRetailReq = Map.of(
                "batchName", "Hóa đơn bán lẻ E2E (Đã sửa)",
                "note", "Cập nhật số lượng trước khi xuất kho",
                "discountAmount", 1000000,
                "items", List.of(
                        Map.of("variantId", variant1Id.toString(), "quantity", 3),
                        Map.of("variantId", variant2Id.toString(), "quantity", 1)
                )
        );
        ResponseEntity<String> editRetailRes = exchange("/api/v1/sales/invoices/" + retailInvoiceId, HttpMethod.PUT, editRetailReq, salesToken);
        assertThat(editRetailRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode editedInv = objectMapper.readTree(editRetailRes.getBody()).get("data");
        assertThat(editedInv.get("totalAmount").decimalValue()).isEqualByComparingTo("16000000");

        // Verify PostgreSQL tables synced (orders.total_amount & inventories.reserved_qty)
        BigDecimal orderTotalDb = jdbc.queryForObject("SELECT total_amount FROM public.orders WHERE order_id = ?::uuid",
                BigDecimal.class, retailOrderId);
        assertThat(orderTotalDb).isEqualByComparingTo("16000000.00");
        BigDecimal reservedV1AfterEdit = jdbc.queryForObject(
                "SELECT reserved_qty FROM public.inventories WHERE variant_id = ?", BigDecimal.class, variant1Id);
        BigDecimal reservedV2AfterEdit = jdbc.queryForObject(
                "SELECT reserved_qty FROM public.inventories WHERE variant_id = ?", BigDecimal.class, variant2Id);
        assertThat(reservedV1AfterEdit).isEqualByComparingTo("3.00");
        assertThat(reservedV2AfterEdit).isEqualByComparingTo("1.00");

        // WAREHOUSE_KEEPER chỉ được xem GET, không được sửa PUT (403 Forbidden)
        ResponseEntity<String> whGetRes = exchange("/api/v1/sales/orders/" + retailOrderId + "/invoices", HttpMethod.GET, null, warehouseToken);
        assertThat(whGetRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> whPutRes = exchange("/api/v1/sales/invoices/" + retailInvoiceId, HttpMethod.PUT, editRetailReq, warehouseToken);
        assertThat(whPutRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Chốt & Phát hành hóa đơn RETAIL (DRAFT -> ISSUED)
        ResponseEntity<String> issueRetailRes = exchange("/api/v1/sales/invoices/" + retailInvoiceId + "/issue", HttpMethod.POST, null, salesToken);
        assertThat(issueRetailRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode issuedRetail = objectMapper.readTree(issueRetailRes.getBody()).get("data");
        assertThat(issuedRetail.get("status").asText()).isEqualTo("ISSUED");
        assertThat(issuedRetail.get("editable").asBoolean()).isFalse();

        // Sửa lại hóa đơn ISSUED phải trả 409 CONFLICT
        ResponseEntity<String> editAfterIssueRes = exchange("/api/v1/sales/invoices/" + retailInvoiceId, HttpMethod.PUT, editRetailReq, salesToken);
        assertThat(editAfterIssueRes.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(editAfterIssueRes.getBody()).get("code").asText()).isEqualTo("SALES_020");

        // =====================================================================
        // 2. LUỒNG ĐƠN CÔNG TRÌNH (PROJECT) CHIA 2 ĐỢT + TÁCH ĐỢT 3 + UC-26 + PHÁT HÀNH
        // =====================================================================
        Map<String, Object> convertReq = Map.of(
                "batches", List.of(
                        Map.of(
                                "batchName", "Đợt 1 - Phần thô",
                                "items", List.of(Map.of("variantId", variant1Id.toString(), "quantity", 6))
                        ),
                        Map.of(
                                "batchName", "Đợt 2 - Hoàn thiện",
                                "items", List.of(
                                        Map.of("variantId", variant1Id.toString(), "quantity", 4),
                                        Map.of("variantId", variant2Id.toString(), "quantity", 5)
                                )
                        )
                )
        );
        ResponseEntity<String> convertRes = exchange("/api/v1/sales/orders/from-quotation/" + quotationId, HttpMethod.POST, convertReq, salesToken);
        assertThat(convertRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode projData = objectMapper.readTree(convertRes.getBody()).get("data");
        String projectOrderId = projData.get("orderId").asText();
        assertThat(projData.get("totalInvoiceCount").asInt()).isEqualTo(2);
        assertThat(projData.get("draftInvoiceCount").asInt()).isEqualTo(2);

        ResponseEntity<String> listProjInvRes = exchange("/api/v1/sales/orders/" + projectOrderId + "/invoices", HttpMethod.GET, null, salesToken);
        assertThat(listProjInvRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode projInvoices = objectMapper.readTree(listProjInvRes.getBody()).get("data");
        assertThat(projInvoices.size()).isEqualTo(2);
        assertThat(projInvoices.get(0).get("invoiceType").asText()).isEqualTo("PROJECT");
        String batch1Id = projInvoices.get(0).get("invoiceId").asText();
        String batch2Id = projInvoices.get(1).get("invoiceId").asText();

        // Tách thêm Đợt 3 từ Đợt 2
        Map<String, Object> splitReq = Map.of(
                "batchName", "Đợt 3 - Tách riêng",
                "splitFromInvoiceId", batch2Id,
                "items", List.of(Map.of("variantId", variant1Id.toString(), "quantity", 2))
        );
        ResponseEntity<String> splitRes = exchange("/api/v1/sales/orders/" + projectOrderId + "/invoices", HttpMethod.POST, splitReq, salesToken);
        assertThat(splitRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String batch3Id = objectMapper.readTree(splitRes.getBody()).get("data").get("invoiceId").asText();

        // Mô phỏng UC-26 PENDING_REVIEW đang tồn tại trong DB -> khóa sửa & phát hành (409 SALES_022)
        UUID uc26Id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.order_modification_requests (
                    modification_request_id, order_id, customer_id, request_type, request_status, customer_reason, requested_by
                ) VALUES (?, ?::uuid, ?::uuid, 'MODIFY', 'PENDING_REVIEW', 'Khách xin đổi lịch giao', ?::uuid)
                """, uc26Id, projectOrderId, customerId.toString(), salesStaffId.toString());

        ResponseEntity<String> blockedByUc26Res = exchange("/api/v1/sales/invoices/" + batch1Id + "/issue", HttpMethod.POST, null, salesToken);
        assertThat(blockedByUc26Res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(blockedByUc26Res.getBody()).get("code").asText()).isEqualTo("SALES_022");

        // Xử lý xong UC-26 (APPROVED) -> mở khóa
        jdbc.update("UPDATE public.order_modification_requests SET request_status = 'APPROVED' WHERE modification_request_id = ?", uc26Id);

        // Hủy Đợt 3 nháp (DELETE)
        ResponseEntity<String> deleteBatch3Res = exchange("/api/v1/sales/invoices/" + batch3Id, HttpMethod.DELETE, null, salesToken);
        assertThat(deleteBatch3Res.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Phát hành Đợt 1 & Đợt 2 -> Kiểm tra SalesInvoiceApi cho Quảng
        assertThat(exchange("/api/v1/sales/invoices/" + batch1Id + "/issue", HttpMethod.POST, null, salesToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        UUID projectOrderUuid = UUID.fromString(projectOrderId);
        assertThat(salesInvoiceApi.areAllActiveInvoicesIssued(projectOrderUuid)).isFalse();
        assertThat(salesInvoiceApi.getIssuedInvoicesByOrderId(projectOrderUuid)).hasSize(1);

        assertThat(exchange("/api/v1/sales/invoices/" + batch2Id + "/issue", HttpMethod.POST, null, salesToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(salesInvoiceApi.areAllActiveInvoicesIssued(projectOrderUuid)).isTrue();
        assertThat(salesInvoiceApi.getIssuedInvoicesByOrderId(projectOrderUuid)).hasSize(2);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, String bearerToken) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        String json = body == null ? null : objectMapper.writeValueAsString(body);
        return restTemplate.exchange("http://localhost:" + port + path, method, new HttpEntity<>(json, headers), String.class);
    }

    private String login(String email, String password) throws Exception {
        ResponseEntity<String> res = exchange("/api/v1/auth/login", HttpMethod.POST,
                Map.of("email", email, "password", password), null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = objectMapper.readTree(res.getBody()).path("data").path("access_token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private void cleanTestRows() {
        try {
            jdbc.update("DELETE FROM public.order_modification_requests WHERE customer_id IN (SELECT customer_id FROM public.customers WHERE customer_code = 'CUS-INV-E2E')");
            jdbc.update("DELETE FROM public.sales_invoice_items WHERE invoice_id IN (SELECT invoice_id FROM public.sales_invoices WHERE customer_id IN (SELECT customer_id FROM public.customers WHERE customer_code = 'CUS-INV-E2E'))");
            jdbc.update("DELETE FROM public.sales_invoices WHERE customer_id IN (SELECT customer_id FROM public.customers WHERE customer_code = 'CUS-INV-E2E')");
            jdbc.update("DELETE FROM public.order_payment_milestones WHERE order_id IN (SELECT order_id FROM public.orders WHERE customer_id IN (SELECT customer_id FROM public.customers WHERE customer_code = 'CUS-INV-E2E'))");
            jdbc.update("DELETE FROM public.order_items WHERE order_id IN (SELECT order_id FROM public.orders WHERE customer_id IN (SELECT customer_id FROM public.customers WHERE customer_code = 'CUS-INV-E2E'))");
            jdbc.update("DELETE FROM public.orders WHERE customer_id IN (SELECT customer_id FROM public.customers WHERE customer_code = 'CUS-INV-E2E')");
            jdbc.update("DELETE FROM public.quotation_items WHERE quotation_id IN (SELECT quotation_id FROM public.quotations WHERE quotation_code = 'BG-E2E-001')");
            jdbc.update("DELETE FROM public.quotations WHERE quotation_code = 'BG-E2E-001'");
            jdbc.update("DELETE FROM public.inventories WHERE variant_id IN (SELECT variant_id FROM public.product_variants WHERE sku IN ('E2E-SKU-001', 'E2E-SKU-002'))");
            jdbc.update("DELETE FROM public.product_variants WHERE sku IN ('E2E-SKU-001', 'E2E-SKU-002')");
            jdbc.update("DELETE FROM public.products WHERE product_code = 'PRD-INV-E2E'");
            jdbc.update("DELETE FROM public.categories WHERE category_code = 'CAT_INV_E2E'");
            jdbc.update("DELETE FROM public.brands WHERE brand_code = 'BRD_INV_E2E'");
            jdbc.update("DELETE FROM public.customers WHERE customer_code = 'CUS-INV-E2E'");
            jdbc.update("DELETE FROM public.user_roles WHERE user_id IN (SELECT id FROM public.users WHERE username IN ('inv_sales_e2e', 'inv_wh_e2e'))");
            jdbc.update("DELETE FROM public.users WHERE username IN ('inv_sales_e2e', 'inv_wh_e2e')");
        } catch (Exception ignored) {
        }
        try {
            Set<String> keys = redisTemplate.keys("auth:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
        }
    }
}

