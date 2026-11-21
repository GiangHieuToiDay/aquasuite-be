package com.aquasuite.ims.service.crm;

import com.aquasuite.ims.dto.crm.request.CreateDeliveryAddressRequest;
import com.aquasuite.ims.dto.crm.request.UpdateDeliveryAddressRequest;
import com.aquasuite.ims.dto.crm.response.DeliveryAddressResponse;

import java.util.List;
import java.util.UUID;

public interface DeliveryAddressService {

    List<DeliveryAddressResponse> getAddressesByUserId(UUID userId);

    DeliveryAddressResponse getAddressDetail(UUID userId, UUID addressId);

    DeliveryAddressResponse createAddress(UUID userId, CreateDeliveryAddressRequest request);

    DeliveryAddressResponse updateAddress(UUID userId, UUID addressId, UpdateDeliveryAddressRequest request);

    void deleteAddress(UUID userId, UUID addressId);

    DeliveryAddressResponse setDefaultAddress(UUID userId, UUID addressId);
}
