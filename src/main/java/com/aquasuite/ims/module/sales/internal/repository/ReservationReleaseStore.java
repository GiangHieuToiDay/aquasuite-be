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
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public Optional<ReleaseResult> find(UUID decisionId) {
        return jdbc.query("select result::text from public.sales_reservation_releases where decision_id=?",
                (rs, n) -> decode(rs.getString(1)), decisionId).stream().findFirst();
    }

    public void save(ReleaseResult result) {
        try {
            jdbc.update("""
                    insert into public.sales_reservation_releases(decision_id,order_id,executed_by,executed_at,result)
                    values(?,?,?,?,?::jsonb)
                    """, result.decisionId(), result.orderId(), result.executedBy(),
                    java.sql.Timestamp.from(result.executedAt()), json.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize reservation release result", e);
        }
    }

    private ReleaseResult decode(String value) {
        try { return json.readValue(value, ReleaseResult.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid reservation release result", e); }
    }
}
