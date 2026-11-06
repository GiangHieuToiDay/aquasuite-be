package com.aquasuite.ims.module.purchasing;

import com.aquasuite.ims.module.catalog.internal.entity.Product;
import com.aquasuite.ims.module.catalog.internal.repository.ProductRepository;
import com.aquasuite.ims.module.inventory.internal.entity.Supplier;
import com.aquasuite.ims.module.inventory.internal.entity.SupplierProduct;
import com.aquasuite.ims.module.inventory.internal.repository.SupplierProductRepository;
import com.aquasuite.ims.module.inventory.internal.repository.SupplierRepository;
import com.aquasuite.ims.module.purchasing.internal.config.PurchasingDataInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchasingDataInitializerTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private SupplierProductRepository supplierProductRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private PurchasingDataInitializer dataInitializer;

    @Test
    @DisplayName("Skip initialization if sample supplier already exists")
    void shouldSkipIfAlreadyInitialized() {
        when(supplierRepository.existsBySupplierCode("SUP-INAX-01")).thenReturn(true);

        dataInitializer.run();

        verify(supplierRepository, never()).saveAll(anyList());
        verify(supplierProductRepository, never()).save(any());
    }

    @Test
    @DisplayName("Initialize suppliers and map supplier products when not yet initialized")
    void shouldInitializeSuppliersAndProducts() {
        when(supplierRepository.existsBySupplierCode("SUP-INAX-01")).thenReturn(false);

        Supplier saved1 = Supplier.builder().supplierId(UUID.randomUUID()).supplierCode("SUP-INAX-01").build();
        Supplier saved2 = Supplier.builder().supplierId(UUID.randomUUID()).supplierCode("SUP-TOTO-02").build();
        Supplier saved3 = Supplier.builder().supplierId(UUID.randomUUID()).supplierCode("SUP-VIG-03").build();

        when(supplierRepository.saveAll(anyList())).thenReturn(List.of(saved1, saved2, saved3));

        Product p1 = Product.builder().productId(UUID.randomUUID()).productCode("PROD-01").build();
        when(productRepository.findAll()).thenReturn(List.of(p1));

        dataInitializer.run();

        ArgumentCaptor<List<Supplier>> captor = ArgumentCaptor.forClass(List.class);
        verify(supplierRepository).saveAll(captor.capture());

        List<Supplier> captured = captor.getValue();
        assertThat(captured).hasSize(3);
        // Ensure supplierId was NOT manually set (must be null so JPA persists it)
        assertThat(captured.get(0).getSupplierId()).isNull();
        assertThat(captured.get(1).getSupplierId()).isNull();
        assertThat(captured.get(2).getSupplierId()).isNull();

        verify(supplierProductRepository).save(any(SupplierProduct.class));
    }
}

