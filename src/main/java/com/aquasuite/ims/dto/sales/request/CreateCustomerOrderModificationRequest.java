package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.aquasuite.ims.dto.sales.request.CreateOrderModificationRequest.CreateOrderModificationItemRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCustomerOrderModificationRequest {

    @NotBlank
    @Size(max = 50)
    private String requestType;

    @NotBlank
    @Size(max = 1000)
    private String customerReason;

    @Size(max = 500)
    private String requestedDeliveryAddress;

    @Size(max = 200)
    private List<@NotNull @Valid CreateOrderModificationItemRequest> items;
}
