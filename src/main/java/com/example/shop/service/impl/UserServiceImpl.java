package com.example.shop.service.impl;

import com.example.shop.dto.request.AddressRequest;
import com.example.shop.dto.request.UpdateProfileRequest;
import com.example.shop.dto.response.AddressResponse;
import com.example.shop.dto.response.UserResponse;
import com.example.shop.exception.BadRequestException;
import com.example.shop.exception.ResourceInUseException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.mapper.UserMapper;
import com.example.shop.model.entity.Address;
import com.example.shop.model.entity.User;
import com.example.shop.model.enums.OrderStatus;
import com.example.shop.repository.AddressRepository;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.RefreshTokenRepository;
import com.example.shop.repository.UserRepository;
import com.example.shop.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {
    private static final int MAX_ADDRESSES = 20;
    private static final Set<OrderStatus> IN_PROGRESS = EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED,
            OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.IN_TRANSIT);

    private final UserRepository users;
    private final AddressRepository addresses;
    private final OrderRepository orders;
    private final RefreshTokenRepository refreshTokens;
    private final UserMapper mapper;

    public UserServiceImpl(UserRepository users, AddressRepository addresses, OrderRepository orders,
                           RefreshTokenRepository refreshTokens, UserMapper mapper) {
        this.users = users;
        this.addresses = addresses;
        this.orders = orders;
        this.refreshTokens = refreshTokens;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return mapper.toResponse(user(userId));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest r) {
        User u = user(userId);
        u.setFirstName(r.firstName().trim());
        u.setLastName(r.lastName().trim());
        u.setPhone(r.phone());
        return mapper.toResponse(u);
    }

    @Override
    @Transactional
    public void deactivate(Long userId) {
        User u = user(userId);
        if (orders.existsByUserIdAndStatusIn(userId, IN_PROGRESS)) {
            throw new ResourceInUseException("You have orders that are still in progress; wait for them to finish or cancel them first");
        }
        u.setEnabled(false);
        refreshTokens.revokeAllForUser(userId);
    }

    // ------------------------------------------------------------- addresses

    @Override
    @Transactional(readOnly = true)
    public Page<AddressResponse> addresses(Long userId, Pageable pageable) {
        Pageable p = PageRequest.of(pageable.getPageNumber(), Math.min(Math.max(pageable.getPageSize(), 1), 100),
                Sort.by(Sort.Direction.DESC, "isDefault").and(Sort.by(Sort.Direction.DESC, "id")));
        return addresses.findByUserId(userId, p).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse address(Long userId, Long addressId) {
        return mapper.toResponse(owned(userId, addressId));
    }

    @Override
    @Transactional
    public AddressResponse addAddress(Long userId, AddressRequest r) {
        List<Address> mine = addresses.findByUserId(userId);
        if (mine.size() >= MAX_ADDRESSES) {
            throw new BadRequestException("You can save at most " + MAX_ADDRESSES + " addresses");
        }
        Address a = new Address();
        mapper.apply(r, a);
        a.setUser(user(userId));
        boolean makeDefault = mine.isEmpty() || Boolean.TRUE.equals(r.isDefault());
        a.setIsDefault(makeDefault);
        if (makeDefault) mine.forEach(x -> x.setIsDefault(false));
        return mapper.toResponse(addresses.save(a));
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest r) {
        Address a = owned(userId, addressId);
        boolean wasDefault = Boolean.TRUE.equals(a.getIsDefault());
        mapper.apply(r, a);
        if (Boolean.TRUE.equals(r.isDefault())) {
            addresses.findByUserId(userId).stream().filter(x -> !x.getId().equals(a.getId()))
                    .forEach(x -> x.setIsDefault(false));
            a.setIsDefault(true);
        } else {
            // there is always one default: it moves when another address is made default, not by un-ticking this one
            a.setIsDefault(wasDefault);
        }
        return mapper.toResponse(a);
    }

    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address a = owned(userId, addressId);
        boolean wasDefault = Boolean.TRUE.equals(a.getIsDefault());
        addresses.delete(a);
        if (wasDefault) {
            addresses.findByUserId(userId).stream().filter(x -> !x.getId().equals(addressId))
                    .max(Comparator.comparing(Address::getId)).ifPresent(x -> x.setIsDefault(true));
        }
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Address owned(Long userId, Long addressId) {
        return addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No address with id " + addressId));
    }
}
