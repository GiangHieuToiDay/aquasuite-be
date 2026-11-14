package com.aquasuite.ims.module.fulfillment.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable, entity-free delivery facts used by Finance's COD workflow. */
public record DeliveryCodSnapshot(UUID deliveryId, UUID orderId, UUID shipperId, String deliveryCode,
                                  String status, boolean codRequired, BigDecimal codAmount) {}
