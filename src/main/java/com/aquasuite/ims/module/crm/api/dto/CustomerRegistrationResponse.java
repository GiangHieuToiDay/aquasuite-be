package com.aquasuite.ims.module.crm.api.dto;

import java.util.UUID;

public record CustomerRegistrationResponse(UUID customerId, String customerCode) {}
