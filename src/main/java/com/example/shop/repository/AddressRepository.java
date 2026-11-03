package com.example.shop.repository;

import com.example.shop.model.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserId(Long userId);

    Page<Address> findByUserId(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    /** Scoped to the owner: someone else's address id is simply "not found". */
    Optional<Address> findByIdAndUserId(Long id, Long userId);
}
