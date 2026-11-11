package com.aquasuite.ims.module.fulfillment.internal.service;

import com.aquasuite.ims.module.fulfillment.api.DeliveryCodQueryApi;
import com.aquasuite.ims.module.fulfillment.api.dto.DeliveryCodSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryCodQueryService implements DeliveryCodQueryApi {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public Optional<DeliveryCodSnapshot> find(UUID id) { return query(id, ""); }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<DeliveryCodSnapshot> lock(UUID id) { return query(id, " FOR UPDATE"); }

    private Optional<DeliveryCodSnapshot> query(UUID id, String suffix) {
        return jdbc.query("SELECT delivery_id,order_id,shipper_id,delivery_code,delivery_status,cod_required,cod_amount "
                + "FROM deliveries WHERE delivery_id=?" + suffix,
                (rs, n) -> new DeliveryCodSnapshot(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getBoolean(6), rs.getBigDecimal(7)), id)
                .stream().findFirst();
    }
}
