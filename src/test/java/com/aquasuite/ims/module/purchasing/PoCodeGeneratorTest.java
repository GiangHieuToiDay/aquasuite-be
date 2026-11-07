package com.aquasuite.ims.module.purchasing;

import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderRepository;
import com.aquasuite.ims.module.purchasing.internal.service.PoCodeGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PoCodeGeneratorTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Test
    @DisplayName("Generate PO Code format PO-yyyyMM-xxxx")
    void testGeneratePoCode_Format() {
        PoCodeGenerator generator = new PoCodeGenerator(purchaseOrderRepository);
        String monthPart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String expectedPrefix = "PO-" + monthPart + "-";

        when(purchaseOrderRepository.countByPoCodeStartingWith(startsWith(expectedPrefix))).thenReturn(0L);
        when(purchaseOrderRepository.existsByPoCode(expectedPrefix + "0001")).thenReturn(false);

        String code = generator.generatePoCode();
        assertEquals(expectedPrefix + "0001", code);
    }
}