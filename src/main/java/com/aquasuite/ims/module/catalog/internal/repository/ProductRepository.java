package com.aquasuite.ims.module.catalog.internal.repository;

import com.aquasuite.ims.module.catalog.internal.entity.Product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository
        extends JpaRepository<Product, UUID>,
                org.springframework.data.jpa.repository.JpaSpecificationExecutor<Product> {

    Optional<Product> findByProductCode(String productCode);

    List<Product> findByProductCodeIn(Collection<String> productCodes);
}
