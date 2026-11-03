package com.example.shop.service;

import com.example.shop.dto.request.AddressRequest;
import com.example.shop.dto.request.UpdateProfileRequest;
import com.example.shop.dto.response.AddressResponse;
import com.example.shop.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    UserResponse me(Long userId);

    /** Name and phone only. Email, role and password have their own rules and are not editable here. */
    UserResponse updateProfile(Long userId, UpdateProfileRequest request);

    /**
     * Closes the account: it can no longer log in and its refresh tokens die. Orders are kept (they are records).
     * Refused while an order is still on its way.
     */
    void deactivate(Long userId);

    Page<AddressResponse> addresses(Long userId, Pageable pageable);

    AddressResponse address(Long userId, Long addressId);

    /** The first address is the default automatically; marking another as default un-marks the rest. */
    AddressResponse addAddress(Long userId, AddressRequest request);

    AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request);

    /** If the default is deleted, the newest remaining address becomes the default. Past orders keep their own copy. */
    void deleteAddress(Long userId, Long addressId);
}
