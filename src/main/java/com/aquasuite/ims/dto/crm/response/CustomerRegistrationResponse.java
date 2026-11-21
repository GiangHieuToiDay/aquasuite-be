package com.aquasuite.ims.dto.crm.response;

import java.util.UUID;

public record CustomerRegistrationResponse(UUID customerId, String customerCode) {}
