package com.aquasuite.ims.dto.finance.response;

/** Truthful result of a statement email attempt; a failed side effect never changes receivables. */
public record StatementEmailResponse(String emailResult, String message) {}
