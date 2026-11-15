package com.aquasuite.ims.module.servicedesk.internal.repository;

import com.aquasuite.ims.module.servicedesk.internal.entity.WarrantyTicket;
import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyTicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarrantyTicketRepository
        extends JpaRepository<WarrantyTicket, UUID>, JpaSpecificationExecutor<WarrantyTicket> {

    Optional<WarrantyTicket> findByTicketIdAndCustomerId(UUID ticketId, UUID customerId);

    Optional<WarrantyTicket> findFirstByWarrantyRecordIdAndStatusIn(
            UUID warrantyRecordId,
            Collection<WarrantyTicketStatus> statuses
    );

    Page<WarrantyTicket> findByCustomerId(UUID customerId, Pageable pageable);

    boolean existsByWarrantyRecordIdAndStatusIn(
            UUID warrantyRecordId,
            Collection<WarrantyTicketStatus> statuses
    );

    @org.springframework.data.jpa.repository.Query("""
        SELECT t FROM WarrantyTicket t
        WHERE t.warrantyRecordId IN :recordIds
          AND t.status IN :statuses
    """)
    java.util.List<WarrantyTicket> findTicketsByWarrantyRecordIdsAndStatuses(
            @org.springframework.data.repository.query.Param("recordIds") Collection<UUID> recordIds,
            @org.springframework.data.repository.query.Param("statuses") Collection<WarrantyTicketStatus> statuses
    );
}
