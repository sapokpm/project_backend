package com.example.shop.mapper;

import com.example.shop.dto.response.*;
import com.example.shop.model.entity.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Touches lazy relations: call from inside a service transaction. */
@Component
public class OrderMapper {

    public OrderResponse toResponse(Order o) {
        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getStatus(), toResponse(o.getShippingAddress()),
                o.getDeliveryMethod(), o.getDeliveryTrackingNumber(), o.getEstimatedDeliveryDate(),
                o.getActualDeliveryDate(), o.getSubtotal(), o.getShippingCost(), o.getDiscount(),
                o.getBonusPointsUsed(), o.getTotalAmount(), o.getPromoCode(), o.getCustomerNote(),
                o.getOrderItems().stream().map(this::toResponse).toList(),
                o.getPayment() == null ? null : toResponse(o.getPayment()),
                o.getCreatedAt(), o.getPaidAt(), o.getShippedAt(), o.getDeliveredAt());
    }

    public AdminOrderResponse toAdminResponse(Order o) {
        return new AdminOrderResponse(toResponse(o), o.getUser().getId(), o.getUser().getEmail(), o.getAdminNote());
    }

    public OrderItemResponse toResponse(OrderItem i) {
        return new OrderItemResponse(i.getId(), i.getProduct().getId(), i.getProduct().getName(),
                i.getVariant() == null ? null : i.getVariant().getId(), i.getQuantity(), i.getPriceAtPurchase(),
                i.getPriceAtPurchase().multiply(BigDecimal.valueOf(i.getQuantity())));
    }

    public ShippingAddressResponse toResponse(ShippingAddress a) {
        if (a == null) return null;
        return new ShippingAddressResponse(a.getCountry(), a.getCity(), a.getStreet(), a.getBuilding(),
                a.getApartment(), a.getPostalCode(), a.getRecipientName(), a.getRecipientPhone());
    }

    /** Snapshot an Address into the order's embedded copy. */
    public ShippingAddress snapshot(Address a) {
        ShippingAddress s = new ShippingAddress();
        s.setCountry(a.getCountry());
        s.setCity(a.getCity());
        s.setStreet(a.getStreet());
        s.setBuilding(a.getBuilding());
        s.setApartment(a.getApartment());
        s.setPostalCode(a.getPostalCode());
        s.setRecipientName(a.getRecipientName());
        s.setRecipientPhone(a.getRecipientPhone());
        return s;
    }

    public PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrder().getId(), p.getTransactionId(), p.getPaymentMethod(),
                p.getStatus(), p.getAmount(), p.getCurrency(), p.getCardLastFourDigits(), p.getPaidAt(),
                p.getErrorMessage(), p.getRefundedAmount());
    }

    public DeliveryOptionResponse toResponse(DeliveryOption d) {
        return new DeliveryOptionResponse(d.getId(), d.getName(), d.getCode(), d.getDescription(), d.getBasePrice(),
                d.getFreeThreshold(), d.getEstimatedDaysMin(), d.getEstimatedDaysMax());
    }
}
