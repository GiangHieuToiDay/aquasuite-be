package com.aquasuite.ims.module.finance.internal.enums;

/** Lifecycle of an allocation row; verified receipt transactions are never mutated. */
public enum PaymentAllocationStatus {
    ACTIVE,
    REVERSED
}
