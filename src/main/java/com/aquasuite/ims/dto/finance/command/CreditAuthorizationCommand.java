package com.aquasuite.ims.dto.finance.command;

import java.math.BigDecimal;
import java.util.UUID;

public record CreditAuthorizationCommand(UUID customerId, UUID orderId, String orderChannel, BigDecimal orderAmount) {}
