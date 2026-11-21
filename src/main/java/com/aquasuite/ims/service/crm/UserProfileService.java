package com.aquasuite.ims.service.crm;

import com.aquasuite.ims.dto.crm.request.CreateDeliveryAddressRequest;
import com.aquasuite.ims.dto.crm.request.UpdateDeliveryAddressRequest;
import com.aquasuite.ims.dto.crm.request.UpdateProfileRequest;
import com.aquasuite.ims.dto.crm.response.AvatarUploadResponse;
import com.aquasuite.ims.dto.crm.response.DeliveryAddressResponse;
import com.aquasuite.ims.dto.crm.response.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface UserProfileService {

    UserProfileResponse getProfile(UUID userId);

    UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);

    AvatarUploadResponse uploadAvatar(UUID userId, MultipartFile file);

    // ── Delivery Addresses ───────────────────────────────────────────────────

    List<DeliveryAddressResponse> getAddressesByUserId(UUID userId);

    DeliveryAddressResponse getAddressDetail(UUID userId, UUID addressId);

    DeliveryAddressResponse createAddress(UUID userId, CreateDeliveryAddressRequest request);

    DeliveryAddressResponse updateAddress(UUID userId, UUID addressId, UpdateDeliveryAddressRequest request);

    void deleteAddress(UUID userId, UUID addressId);

    DeliveryAddressResponse setDefaultAddress(UUID userId, UUID addressId);
}
