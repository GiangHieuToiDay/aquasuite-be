package com.aquasuite.ims.module.sales.api;

import com.aquasuite.ims.module.sales.api.dto.SalesInvoiceDebtDto;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderDebtDto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public, entity-free debt-document query contract for Finance. */
public interface SalesReceivableApi {
    List<SalesOrderDebtDto> findOrdersByCustomerIds(Collection<UUID> customerIds);

    List<SalesInvoiceDebtDto> findInvoicesByCustomerIds(Collection<UUID> customerIds);

    Optional<SalesOrderDebtDto> findOrderById(UUID orderId);
}
