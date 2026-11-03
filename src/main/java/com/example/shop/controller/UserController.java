package com.example.shop.controller;

import com.example.shop.dto.request.AddressRequest;
import com.example.shop.dto.request.ChangePasswordRequest;
import com.example.shop.dto.request.UpdateProfileRequest;
import com.example.shop.dto.response.AddressResponse;
import com.example.shop.dto.response.UserResponse;
import com.example.shop.security.service.UserPrincipal;
import com.example.shop.service.AuthService;
import com.example.shop.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** Everything here is about the caller. There is no user id in any URL, so there is nobody else's data to reach. */
@RestController
@RequestMapping("/api/users/me")
public class UserController {
    private final UserService users;
    private final AuthService auth;

    public UserController(UserService users, AuthService auth) {
        this.users = users;
        this.auth = auth;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal UserPrincipal me) {
        return users.me(me.getId());
    }

    @PutMapping
    public UserResponse update(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody UpdateProfileRequest request) {
        return users.updateProfile(me.getId(), request);
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserPrincipal me,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(me.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal UserPrincipal me) {
        users.deactivate(me.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/addresses")
    public Page<AddressResponse> addresses(@AuthenticationPrincipal UserPrincipal me,
                                           @PageableDefault(size = 20) Pageable pageable) {
        return users.addresses(me.getId(), pageable);
    }

    @GetMapping("/addresses/{id}")
    public AddressResponse address(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return users.address(me.getId(), id);
    }

    @PostMapping("/addresses")
    public ResponseEntity<AddressResponse> addAddress(@AuthenticationPrincipal UserPrincipal me,
                                                      @Valid @RequestBody AddressRequest request) {
        AddressResponse created = users.addAddress(me.getId(), request);
        return ResponseEntity.created(URI.create("/api/users/me/addresses/" + created.id())).body(created);
    }

    @PutMapping("/addresses/{id}")
    public AddressResponse updateAddress(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                         @Valid @RequestBody AddressRequest request) {
        return users.updateAddress(me.getId(), id, request);
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        users.deleteAddress(me.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
