package com.aquasuite.ims.enums.finance;

/** Lifecycle of an allocation row; verified receipt transactions are never mutated. */
public enum PaymentAllocationStatus {
    ACTIVE,
    REVERSED
}
