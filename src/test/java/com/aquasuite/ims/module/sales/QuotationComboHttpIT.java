package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties="aquasuite.reservations.retry-enabled=false")
@AutoConfigureMockMvc
class QuotationComboHttpIT extends PostgreSqlIntegrationTestSupport {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockBean IamApi iam;
    UUID actor,customer,bundle;
    List<UUID> variants;
    static final String BASE="/api/v1/sales/quotations";

    @BeforeEach void seed(TestInfo info) {
        actor=UUID.randomUUID(); customer=UUID.randomUUID();
        bundle=info.getTestMethod().orElseThrow().getName().equals("reportedComboSurvivesQuotationLifecycleAndVerifiedDepositHolding")
                ? UUID.fromString("1a733086-366b-4fa3-ac6c-c81d406f5822") : UUID.randomUUID();
        variants=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        when(iam.getCurrentUserId()).thenReturn(actor);
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("SALES_STAFF")).build());
        jdbc.update("insert into users(id,username,email,password_hash,status) values(?,?,?,?,'ACTIVE')",actor,"qc-"+actor,actor+"@example.invalid","{disabled-test-account}");
        jdbc.update("insert into customers(customer_id,customer_code,customer_name,phone) values(?,?,'Combo customer',?)",customer,"C-"+customer,String.format("09%08d",Math.floorMod(customer.hashCode(),100000000)));
        UUID category=UUID.randomUUID();
        jdbc.update("insert into categories(category_id,category_code,category_name) values(?,?,?)",category,"C_"+category.toString().replace("-","").toUpperCase(),"Combo test "+category);
        jdbc.update("insert into products(product_id,product_code,product_name,category_id,product_type,status) values(?,?,'Snapshot bundle',?,'BUNDLE','ACTIVE')",bundle,"B-"+bundle,category);
        jdbc.update("insert into bundle_configurations(bundle_product_id,package_price,publication_status,created_at,updated_at) values(?,35000000,'DRAFT',now(),now())",bundle);
        int[] prices={15000000,10000000,12000000};
        for (int i=0; i<3; i++) {
            UUID product=UUID.randomUUID(),variant=variants.get(i);
            jdbc.update("insert into products(product_id,product_code,product_name,category_id,product_type,status) values(?,?,'Component',?,'NORMAL','ACTIVE')",product,"P-"+product,category);
            jdbc.update("insert into product_variants(variant_id,product_id,sku,variant_name,unit,selling_price,cost_price,status) values(?,?,?,'Component','piece',?,1000000,'ACTIVE')",variant,product,"S-"+variant,prices[i]);
            jdbc.update("insert into bundle_items(bundle_item_id,bundle_product_id,variant_id,quantity) values(?,?,?,1)",UUID.randomUUID(),bundle,variant);
            jdbc.update("insert into inventories(inventory_id,variant_id,warehouse_code,on_hand_qty,reserved_qty,minimum_qty,updated_at) values(?,?,'WH_MAIN',2,0,0,now())",UUID.randomUUID(),variant);
        }
    }

    private Map<String,Object> request() {
        return Map.of("customerId",customer,"projectName","Combo project","validUntil",LocalDate.now().plusDays(30).toString(),
                "vatRate",10,"shippingFee",500000,"depositPercent",20,"items",List.of(
                        Map.of("variantId",bundle,"quantity",2,"discountRate",5),
                        Map.of("variantId",variants.getFirst(),"quantity",1,"discountRate",0)));
    }
    private JsonNode call(MockHttpServletRequestBuilder request,Object body,int expected) throws Exception {
        request.with(user("sales").roles("SALES_STAFF"));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        var result=http.perform(request).andExpect(status().is(expected)).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }
    private JsonNode combo(JsonNode response) {
        for (var item : response.path("items")) if (item.path("comboId").asText().equals(bundle.toString())) return item;
        throw new AssertionError("Missing combo root");
    }
    private void assertPackage(JsonNode response) {
        var root=combo(response);
        assertThat(root.path("variantId").asText()).isEqualTo(bundle.toString());
        assertThat(root.path("variantName").asText()).isEqualTo("Snapshot bundle");
        assertThat(root.path("sku").asText()).isEqualTo("B-"+bundle);
        assertThat(root.path("components").size()).isEqualTo(3);
        assertThat(root.path("unitPrice").decimalValue()).isEqualByComparingTo("35000000");
        assertThat(root.path("lineTotal").decimalValue()).isEqualByComparingTo("66500000");
    }

    @Test void reportedComboSurvivesQuotationLifecycleAndVerifiedDepositHolding() throws Exception {
        var preview=call(post(BASE+"/preview"),request(),200);
        assertPackage(preview);
        assertThat(preview.path("summary").path("grandTotal").decimalValue()).isEqualByComparingTo("90150000");
        var created=call(post(BASE),request(),201);
        UUID quote=UUID.fromString(created.path("quotationId").asText());
        assertPackage(created);
        assertPackage(call(put(BASE+"/"+quote),request(),200));
        assertPackage(call(get(BASE+"/"+quote),null,200));
        assertThat(jdbc.queryForObject("select count(*) from quotation_items where quotation_id=?",Integer.class,quote)).isEqualTo(5);
        call(post(BASE+"/"+quote+"/send"),null,200);
        jdbc.update("update bundle_configurations set package_price=1 where bundle_product_id=?",bundle);
        jdbc.update("update products set product_name='Changed catalog' where product_id=?",bundle);
        for (UUID variant : variants) jdbc.update("update product_variants set selling_price=1,cost_price=1 where variant_id=?",variant);
        assertPackage(call(get(BASE+"/"+quote),null,200));
        call(post(BASE+"/"+quote+"/accept"),null,200);
        jdbc.update("delete from bundle_items where bundle_product_id=?",bundle);
        var converted=call(post("/api/v1/sales/orders/from-quotation/"+quote),null,201);
        UUID order=UUID.fromString(converted.path("orderId").asText());
        assertThat(converted.path("items").size()).isEqualTo(4);
        assertThat(converted.path("displayItems").size()).isEqualTo(2);
        JsonNode comboDisplay = null;
        for (var row : converted.path("displayItems")) {
            if (row.path("itemType").asText().equals("COMBO")) comboDisplay = row;
        }
        assertThat(comboDisplay).isNotNull();
        assertThat(comboDisplay.path("quantity").decimalValue()).isEqualByComparingTo("2");
        assertThat(comboDisplay.path("sku").asText()).isEqualTo("B-"+bundle);
        assertThat(comboDisplay.path("unitPrice").decimalValue()).isEqualByComparingTo("35000000");
        assertThat(comboDisplay.path("discountAmount").decimalValue()).isEqualByComparingTo("3500000");
        assertThat(comboDisplay.path("components").size()).isEqualTo(3);
        var detail = call(get("/api/v1/sales/orders/"+order), null, 200);
        assertThat(detail.path("displayItems").equals((a, b) -> a.isNumber() && b.isNumber()
                ? a.decimalValue().compareTo(b.decimalValue()) : a.equals(b) ? 0 : 1,
                converted.path("displayItems"))).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from order_items where order_id=? and combo_quantity=2 and combo_group_id is not null",Integer.class,order)).isEqualTo(3);
        assertThat(converted.path("totalAmount").decimalValue()).isEqualByComparingTo("90150000");
        assertThat(converted.path("depositAmount").decimalValue()).isEqualByComparingTo("18030000");
        assertThat(jdbc.queryForObject("select sum(line_total) from order_items where order_id=?",BigDecimal.class,order)).isEqualByComparingTo("81500000");
        assertThat(jdbc.queryForObject("select count(*) from order_items where order_id=? and combo_id=?",Integer.class,order,bundle)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from order_items where order_id=? and variant_id is null",Integer.class,order)).isZero();
        assertThat(jdbc.queryForObject("select sum(reserved_qty) from inventories where variant_id in (?,?,?)",BigDecimal.class,variants.get(0),variants.get(1),variants.get(2))).isZero();
        var payment=call(post("/api/v1/finance/orders/"+order+"/payments"),Map.of("idempotencyKey",UUID.randomUUID(),"amount",18030000,"paymentMethod","BANK_TRANSFER"),200);
        UUID paymentId=UUID.fromString(payment.path("transactionId").asText());
        http.perform(post("/api/v1/finance/payment-verifications/"+paymentId+"/verify")
                .with(user("accountant").roles("ACCOUNTANT"))).andExpect(status().isOk());
        var reservation=call(get("/api/v1/sales/orders/"+order+"/stock-reservations"),null,200);
        assertThat(reservation.path("payment").path("depositSatisfied").asBoolean()).isTrue();
        assertThat(reservation.path("status").asText()).isEqualTo("PARTIALLY_HELD");
        assertThat(jdbc.queryForObject("select sum(held_qty) from stock_reservations where source_id=?",BigDecimal.class,order)).isEqualByComparingTo("6");
    }

    @Test void rejectsFractionalInactiveAndMissingCombosWithoutWritingQuotes() throws Exception {
        var body=new HashMap<>(request());
        body.put("items",List.of(Map.of("variantId",bundle,"quantity",0.5)));
        call(post(BASE+"/preview"),body,400);
        jdbc.update("update product_variants set status='INACTIVE' where variant_id=?",variants.getFirst());
        call(post(BASE),request(),400);
        body.put("items",List.of(Map.of("variantId",UUID.randomUUID(),"quantity",1)));
        call(post(BASE+"/preview"),body,404);
        assertThat(jdbc.queryForObject("select count(*) from quotations where customer_id=?",Integer.class,customer)).isZero();
    }

    @Test void revisionPreservesComboSnapshotAndLinksNewComponentsToNewRoot() throws Exception {
        var created=call(post(BASE),request(),201);
        UUID quote=UUID.fromString(created.path("quotationId").asText());
        call(post(BASE+"/"+quote+"/send"),null,200);
        jdbc.update("update bundle_configurations set package_price=1 where bundle_product_id=?",bundle);
        jdbc.update("update products set product_name='Changed catalog' where product_id=?",bundle);
        for (UUID variant : variants) jdbc.update("update product_variants set selling_price=1,cost_price=1 where variant_id=?",variant);
        var revision=call(post(BASE+"/"+quote+"/revisions"),null,201);
        assertPackage(revision);
        assertThat(revision.path("revisionNo").asInt()).isEqualTo(2);
        assertThat(revision.path("quotationCode").asText()).isEqualTo(created.path("quotationCode").asText());
        assertThat(combo(revision).path("quotationItemId").asText()).isNotEqualTo(combo(created).path("quotationItemId").asText());
        UUID revised=UUID.fromString(revision.path("quotationId").asText());
        assertThat(jdbc.queryForObject("select count(*) from quotation_items child join quotation_items parent on child.parent_item_id=parent.quotation_item_id where child.quotation_id=? and parent.quotation_id<>child.quotation_id",Integer.class,revised)).isZero();
        assertThat(jdbc.queryForObject("select sum(quantity*unit_price) from quotation_items where quotation_id=? and parent_item_id is not null",BigDecimal.class,revised)).isEqualByComparingTo("74000000");
        assertThat(jdbc.queryForObject("select unit_cost from quotation_items where quotation_id=? and combo_id=?",BigDecimal.class,revised,bundle)).isEqualByComparingTo("3000000");
    }
}
