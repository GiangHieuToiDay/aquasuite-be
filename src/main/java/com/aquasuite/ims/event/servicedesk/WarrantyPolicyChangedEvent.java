package com.aquasuite.ims.event.servicedesk;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Published after a warranty policy transaction commits; Audit owns audit_logs persistence. */
public record WarrantyPolicyChangedEvent(UUID eventId, UUID actorId, UUID warrantyPolicyId,
                                         String action, Map<String, Object> oldData,
                                         Map<String, Object> newData, Instant occurredAt) {
}
