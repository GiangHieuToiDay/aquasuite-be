package com.aquasuite.ims.service.crm;

import com.aquasuite.ims.repository.crm.CustomerRepository;
import com.aquasuite.ims.service.crm.impl.CustomerServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceKeywordTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerCreditProfileService creditProfileService;

    @InjectMocks
    private CustomerServiceImpl customerService;

    @Test
    @DisplayName("findCustomerIdsByKeyword returns empty list without querying repository when keyword is blank")
    void findCustomerIdsByKeyword_blankInput_returnsEmpty() {
        assertThat(customerService.findCustomerIdsByKeyword(null)).isEmpty();
        assertThat(customerService.findCustomerIdsByKeyword("   ")).isEmpty();
        verify(customerRepository, never()).findCustomerIdsBySearchKeyword(anyString());
    }

    @Test
    @DisplayName("findCustomerIdsByKeyword trims and escapes LIKE wildcards before calling repository")
    void findCustomerIdsByKeyword_escapesWildcardsAndDelegates() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findCustomerIdsBySearchKeyword("0987!%a!_b!!")).thenReturn(List.of(id));

        List<UUID> result = customerService.findCustomerIdsByKeyword("  0987%a_b!  ");

        assertThat(result).containsExactly(id);
        verify(customerRepository).findCustomerIdsBySearchKeyword("0987!%a!_b!!");
    }
}

