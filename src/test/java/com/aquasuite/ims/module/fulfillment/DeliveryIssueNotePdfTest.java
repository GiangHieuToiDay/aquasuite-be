package com.aquasuite.ims.module.fulfillment;

import com.aquasuite.ims.module.fulfillment.internal.payload.response.DeliveryView;
import com.aquasuite.ims.module.fulfillment.internal.service.*;
import com.aquasuite.ims.module.sales.api.OrderDispatchApi;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryIssueNotePdfTest {
    DeliveryService deliveries = mock(DeliveryService.class);
    OrderDispatchApi orders = mock(OrderDispatchApi.class);
    DeliveryIssueNotePdfService pdf = new DeliveryIssueNotePdfService(deliveries, orders);
    UUID id = UUID.randomUUID(), orderId = UUID.randomUUID(), invoiceId = UUID.randomUUID();

    DeliveryView.Detail detail(boolean confirmed, int count) {
        var now = OffsetDateTime.parse("2026-11-14T10:00:00+07:00");
        var summary = new DeliveryView.Summary(id, "DL-202611-0001", orderId, 1, "WH_MAIN", null,
                confirmed ? "UNASSIGNED" : "DRAFT", "Nguyễn Văn An", "0900000000",
                "123 Nguyễn Trãi, Hà Nội", now, null, null, null, true, new BigDecimal("17900000"),
                "Kiểm tra hàng trước khi bàn giao", now, invoiceId, confirmed ? now : null, now);
        return new DeliveryView.Detail(summary, null, "Trần Văn Bình", "0911111111",
                IntStream.rangeClosed(1,count).mapToObj(i -> new DeliveryView.Item(UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID(), "TOTO-"+i, "Bồn cầu thông minh TOTO",
                        "Màu trắng / kích thước 305 mm", BigDecimal.valueOf(i), List.of())).toList(), List.of());
    }
    @Test void rejectsUnconfirmedWithoutLookingUpSales() {
        when(deliveries.detail(id)).thenReturn(detail(false, 1));
        assertThrows(AppException.class, () -> pdf.generate(id)); verifyNoInteractions(orders);
    }
    @Test void preservesObjectAuthorization() {
        when(deliveries.detail(id)).thenThrow(new SecurityException("Other shipper"));
        assertThrows(SecurityException.class, () -> pdf.generate(id)); verifyNoInteractions(orders);
    }
    @Test void generatesVietnameseMultiPageDocument() throws Exception {
        when(deliveries.detail(id)).thenReturn(detail(true, 45));
        when(orders.read(orderId)).thenReturn(new OrderDispatchApi.Snapshot(orderId,"ORD-001","PROCESSING",null,false,List.of(),BigDecimal.ZERO));
        when(orders.invoice(invoiceId)).thenReturn(new OrderDispatchApi.Invoice(invoiceId,"INV-001",orderId,"PROJECT","ISSUED",List.of()));
        byte[] bytes = pdf.generate(id);
        try(var reader = new PdfReader(bytes)) { assertTrue(reader.getNumberOfPages() >= 3); }
        String preview = System.getProperty("delivery.pdf.preview");
        if (preview != null) Files.write(Path.of(preview), bytes);
        verify(deliveries).detail(id);
    }
}
