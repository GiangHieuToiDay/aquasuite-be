package com.aquasuite.ims.module.sales.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@RequiredArgsConstructor
public enum OrderModificationErrorCode implements BaseErrorCode {
    REQUEST_NOT_FOUND("ORDER_MOD_001", "Order modification request was not found.", HttpStatus.NOT_FOUND),
    INVALID_REQUEST_STATE("ORDER_MOD_002", "Order modification request is not in a reviewable state.", HttpStatus.CONFLICT),
    ORDER_NOT_ELIGIBLE("ORDER_MOD_003", "Only pre-dispatch Processing orders can be reviewed for modification or cancellation.", HttpStatus.CONFLICT),
    OWNER_APPROVAL_REQUIRED("ORDER_MOD_004", "This request requires Shop Owner approval.", HttpStatus.FORBIDDEN),
    ADDRESS_REQUIRED("ORDER_MOD_005", "A new delivery address is required for an address-change request.", HttpStatus.BAD_REQUEST),
    RESULT_INVOICE_NOT_FOUND("ORDER_MOD_006", "The tax result invoice was not found.", HttpStatus.NOT_FOUND),
    RESULT_INVOICE_MISMATCH("ORDER_MOD_007", "The tax result invoice does not belong to the original or replacement order.", HttpStatus.CONFLICT),
    REFUND_NOT_FOUND("ORDER_MOD_008", "The pending customer refund was not found.", HttpStatus.CONFLICT),
    REPLACEMENT_SKU_NOT_FOUND("ORDER_MOD_009", "The requested replacement SKU was not found.", HttpStatus.NOT_FOUND),
    DRAFT_INVOICE_REQUIRED("ORDER_MOD_010", "A store-only item change requires only Draft store invoices.", HttpStatus.CONFLICT),
    INVALID_ITEM_CHANGE("ORDER_MOD_011", "The requested item change no longer matches the current order lines.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatusCode status;
}
