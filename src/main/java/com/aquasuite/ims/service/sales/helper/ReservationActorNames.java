package com.aquasuite.ims.service.sales.helper;

import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Resolve only history actors through the existing public IAM contract. */
@Service
@RequiredArgsConstructor
public class ReservationActorNames {
    private final IamQueryService iam;

    // Missing historical users must not mark the history snapshot transaction rollback-only.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<UUID,String> resolve(Set<UUID> ids) {
        Map<UUID,String> names = new HashMap<>();
        for (UUID id : ids) {
            try {
                var user = iam.getUserById(id);
                if (user == null) continue;
                String name = user.fullName() != null && !user.fullName().isBlank() ? user.fullName() : user.username();
                if (name != null && !name.isBlank()) names.put(id,name);
            } catch (AppException missing) {
                if (missing.getErrorCode() != CommonErrorCode.UNAUTHORIZED
                        && missing.getErrorCode() != CommonErrorCode.RESOURCE_NOT_FOUND) throw missing;
            }
        }
        return Map.copyOf(names);
    }
}
