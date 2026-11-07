package com.aquasuite.ims.module.purchasing.internal.config;

import com.aquasuite.ims.module.catalog.internal.entity.Product;
import com.aquasuite.ims.module.catalog.internal.repository.ProductRepository;
import com.aquasuite.ims.module.inventory.internal.entity.Supplier;
import com.aquasuite.ims.module.inventory.internal.entity.SupplierProduct;
import com.aquasuite.ims.module.inventory.internal.entity.SupplierProductId;
import com.aquasuite.ims.module.inventory.internal.enums.SupplierStatus;
import com.aquasuite.ims.module.inventory.internal.repository.SupplierProductRepository;
import com.aquasuite.ims.module.inventory.internal.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class PurchasingDataInitializer implements CommandLineRunner {

    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (supplierRepository.existsBySupplierCode("SUP-INAX-01")) {
            return;
        }

        log.info("Initializing sample suppliers and supplier products...");

        Supplier inax = Supplier.builder()
                .supplierCode("SUP-INAX-01")
                .supplierName("Inax LIXIL Vietnam")
                .contactName("Nguyễn Đức Thắng")
                .phone("024 3863 6055")
                .email("contact@lixil.com.vn")
                .address("Tòa nhà LIXIL, 46 Bích Câu, Đống Đa, Hà Nội")
                .taxCode("0100109923")
                .status(SupplierStatus.ACTIVE)
                .build();

        Supplier toto = Supplier.builder()
                .supplierCode("SUP-TOTO-02")
                .supplierName("TOTO Vietnam Co., Ltd")
                .contactName("Trần Thanh Hà")
                .phone("024 3771 4354")
                .email("sales@toto.com.vn")
                .address("Tầng 16, Tòa tháp Đông, Lotte Center Hanoi, Ba Đình, Hà Nội")
                .taxCode("0101234567")
                .status(SupplierStatus.ACTIVE)
                .build();

        Supplier viglacera = Supplier.builder()
                .supplierCode("SUP-VIG-03")
                .supplierName("Tổng Công ty Viglacera - CTCP")
                .contactName("Lê Văn Phúc")
                .phone("024 3553 6660")
                .email("kinhdoanh@viglacera.com.vn")
                .address("Tòa nhà Viglacera, Số 1 Đại lộ Thăng Long, Mễ Trì, Nam Từ Liêm, Hà Nội")
                .taxCode("0100110833")
                .status(SupplierStatus.ACTIVE)
                .build();

        List<Supplier> savedSuppliers = supplierRepository.saveAll(List.of(inax, toto, viglacera));
        Supplier savedInax = savedSuppliers.get(0);
        Supplier savedToto = savedSuppliers.get(1);

        List<Product> products = productRepository.findAll();
        if (!products.isEmpty()) {
            for (int i = 0; i < products.size(); i++) {
                Product p = products.get(i);
                Supplier supp = (i % 2 == 0) ? savedInax : savedToto;
                SupplierProduct sp = SupplierProduct.builder()
                        .id(SupplierProductId.builder()
                                .supplierId(supp.getSupplierId())
                                .productId(p.getProductId())
                                .build())
                        .supplierSku("SUP-" + p.getProductCode())
                        .lastSupplyPrice(new BigDecimal("1200000.00"))
                        .createdAt(Instant.now())
                        .build();
                supplierProductRepository.save(sp);
            }
        }

        log.info("Sample suppliers initialized successfully");
    }
}
