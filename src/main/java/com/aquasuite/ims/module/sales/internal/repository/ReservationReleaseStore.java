package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.payload.response.ReservationManagementView.ReleaseResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReservationReleaseStore {
    private static final String PREFIX = "UC36_RELEASE:";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public Optional<ReleaseResult> find(UUID decisionId) {
        return jdbc.query("select payload from public.sales_reservation_operations where request_id=?",
                (rs, n) -> {
                    String payload = rs.getString(1);
                    if (!payload.startsWith(PREFIX)) {
                        throw new IllegalStateException("Reservation operation ID belongs to another operation");
                    }
                    return decode(payload.substring(PREFIX.length()));
                }, decisionId).stream().findFirst();
    }

    public void save(ReleaseResult result) {
        try {
            jdbc.update("""
                    insert into public.sales_reservation_operations(request_id,order_id,actor_id,created_at,payload)
                    values(?,?,?,?,?)
                    """, result.decisionId(), result.orderId(), result.executedBy(),
                    java.sql.Timestamp.from(result.executedAt()), PREFIX + json.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize reservation release result", e);
        }
    }

    private ReleaseResult decode(String value) {
        try { return json.readValue(value, ReleaseResult.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid reservation release result", e); }
    }
}
