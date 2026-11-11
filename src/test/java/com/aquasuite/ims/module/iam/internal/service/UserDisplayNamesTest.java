package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserDisplayNamesTest {
    @Test void prefersFullNameFallsBackToUsernameAndOmitsMissingUsers() {
        var repository = mock(UserRepository.class);
        var service = new CurrentIamApi(repository);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), missing = UUID.randomUUID();
        var named = new User(); named.setId(first); named.setFullName("  Nguyen Van A  "); named.setUsername("user-a");
        var fallback = new User(); fallback.setId(second); fallback.setFullName("  "); fallback.setUsername("  user-b  ");
        Set<UUID> ids = Set.of(first, second, missing);
        when(repository.findAllById(ids)).thenReturn(List.of(named, fallback));
        assertThat(service.findUserDisplayNames(ids)).containsEntry(first, "Nguyen Van A")
                .containsEntry(second, "user-b").doesNotContainKey(missing).hasSize(2);
        verify(repository, times(1)).findAllById(ids);
    }
    @Test void emptyIdsDoNotQueryAccounts() {
        var repository = mock(UserRepository.class);
        assertThat(new CurrentIamApi(repository).findUserDisplayNames(Set.of())).isEmpty();
        verifyNoInteractions(repository);
    }
}
