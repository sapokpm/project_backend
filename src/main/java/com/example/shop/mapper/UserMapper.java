package com.example.shop.mapper;

import com.example.shop.dto.request.AddressRequest;
import com.example.shop.dto.response.AddressResponse;
import com.example.shop.dto.response.UserResponse;
import com.example.shop.model.entity.Address;
import com.example.shop.model.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getPhone(),
                u.getRole(), Boolean.TRUE.equals(u.getEmailVerified()), u.getCreatedAt());
    }

    public AddressResponse toResponse(Address a) {
        return new AddressResponse(a.getId(), a.getTitle(), a.getCountry(), a.getCity(), a.getStreet(),
                a.getBuilding(), a.getApartment(), a.getPostalCode(), a.getRecipientName(), a.getRecipientPhone(),
                Boolean.TRUE.equals(a.getIsDefault()), a.getLatitude(), a.getLongitude());
    }

    /** Copies request fields onto an address; the owner and id are set by the service. */
    public void apply(AddressRequest r, Address a) {
        a.setTitle(r.title());
        a.setCountry(r.country());
        a.setCity(r.city());
        a.setStreet(r.street());
        a.setBuilding(r.building());
        a.setApartment(r.apartment());
        a.setPostalCode(r.postalCode());
        a.setRecipientName(r.recipientName());
        a.setRecipientPhone(r.recipientPhone());
        a.setIsDefault(Boolean.TRUE.equals(r.isDefault()));
        a.setLatitude(r.latitude());
        a.setLongitude(r.longitude());
    }
}
