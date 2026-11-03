package com.example.shop.repository;

import com.example.shop.model.entity.Order;
import com.example.shop.model.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Optional<Order> findByOrderNumberAndUserId(String orderNumber, Long userId);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndStatusIn(Long userId, java.util.Collection<OrderStatus> statuses);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /** Row lock for status changes, so two simultaneous cancels or payments cannot both act on the same order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"user"})
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    @Query(value = "select nextval('order_number_seq')", nativeQuery = true)
    long nextOrderSequence();

    // used for the verified-purchase check on reviews
    @Query("select count(oi) > 0 from OrderItem oi where oi.order.user.id = :userId and oi.product.id = :productId and oi.order.status = com.example.shop.model.enums.OrderStatus.DELIVERED")
    boolean hasDeliveredProduct(@Param("userId") Long userId, @Param("productId") Long productId);
}
