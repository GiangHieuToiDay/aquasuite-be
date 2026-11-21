package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.dto.sales.response.SalesInvoiceDebtDto;
import com.aquasuite.ims.dto.sales.response.SalesOrderDebtDto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public, entity-free debt-document query contract for Finance. */
public interface SalesReceivableService {
    List<SalesOrderDebtDto> findOrdersByCustomerIds(Collection<UUID> customerIds);

    List<SalesInvoiceDebtDto> findInvoicesByCustomerIds(Collection<UUID> customerIds);

    /**
     * Returns invoices of an order whose external electronic-invoice integration has succeeded.
     * A non-blank provider invoice ID and signed timestamp are the integration proof; no local e-invoice status exists.
     * This deliberately supports both retail Store invoices and Project invoices created for delivery batches.
     * @param orderId sales order that owns both the receipt and candidate invoices
     * @return operationally issued/dispatched invoices with a completed electronic-invoice integration
     */
    List<SalesInvoiceDebtDto> findElectronicallyIntegratedInvoicesByOrderId(UUID orderId);

    /**
     * Compatibility alias retained for callers compiled against the first UC-63 contract.
     * New code must call {@link #findElectronicallyIntegratedInvoicesByOrderId(UUID)}.
     */
    @Deprecated(forRemoval = false)
    default List<SalesInvoiceDebtDto> findElectronicallyIssuedStoreInvoicesByOrderId(UUID orderId) {
        return findElectronicallyIntegratedInvoicesByOrderId(orderId);
    }

    /**
     * Reads a bounded set of invoices for Finance validation. The caller must still validate order/customer ownership.
     * @param invoiceIds candidate invoice IDs
     * @return matching invoices; callers detect missing IDs from the result size
     */
    List<SalesInvoiceDebtDto> findInvoicesByIds(Collection<UUID> invoiceIds);

    Optional<SalesOrderDebtDto> findOrderById(UUID orderId);
}
