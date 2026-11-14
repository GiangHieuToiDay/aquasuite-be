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

    /**
     * Returns Store invoices of an order whose external electronic-invoice integration has succeeded.
     * A non-blank provider invoice ID and signed timestamp are the integration proof; no local e-invoice status exists.
     * @param orderId sales order that owns both the receipt and candidate invoices
     * @return operationally issued/dispatched Store invoices with a completed electronic-invoice integration
     */
    List<SalesInvoiceDebtDto> findElectronicallyIssuedStoreInvoicesByOrderId(UUID orderId);

    /**
     * Reads a bounded set of invoices for Finance validation. The caller must still validate order/customer ownership.
     * @param invoiceIds candidate invoice IDs
     * @return matching invoices; callers detect missing IDs from the result size
     */
    List<SalesInvoiceDebtDto> findInvoicesByIds(Collection<UUID> invoiceIds);

    Optional<SalesOrderDebtDto> findOrderById(UUID orderId);
}
