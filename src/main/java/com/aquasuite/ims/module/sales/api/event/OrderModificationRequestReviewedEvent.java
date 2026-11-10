package com.aquasuite.ims.module.sales.api.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OrderModificationRequestReviewedEvent(UUID eventId, UUID actorId, UUID requestId,
                                                    UUID orderId, String action,
                                                    Map<String, Object> oldData,
                                                    Map<String, Object> newData,
                                                    String reason, Instant occurredAt) {}
