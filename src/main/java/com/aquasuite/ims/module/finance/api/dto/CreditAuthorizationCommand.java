package com.aquasuite.ims.module.finance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreditAuthorizationCommand(UUID customerId, UUID orderId, String orderChannel, BigDecimal orderAmount) {}
