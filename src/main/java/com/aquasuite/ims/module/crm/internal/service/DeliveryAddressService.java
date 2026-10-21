package com.aquasuite.ims.module.crm.internal.service;

import com.aquasuite.ims.module.crm.internal.payload.req.CreateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.req.UpdateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.DeliveryAddressResponse;

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
