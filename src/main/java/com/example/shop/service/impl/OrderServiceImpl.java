package com.example.shop.service.impl;

import com.example.shop.dto.request.CreateOrderRequest;
import com.example.shop.dto.request.UpdateOrderNoteRequest;
import com.example.shop.dto.request.UpdateTrackingRequest;
import com.example.shop.dto.response.AdminOrderResponse;
import com.example.shop.dto.response.OrderResponse;
import com.example.shop.dto.response.ReorderResponse;
import com.example.shop.exception.*;
import com.example.shop.mapper.OrderMapper;
import com.example.shop.model.entity.*;
import com.example.shop.model.enums.OrderStatus;
import com.example.shop.model.enums.PaymentStatus;
import com.example.shop.repository.*;
import com.example.shop.service.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class OrderServiceImpl implements OrderService {
    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt", "totalAmount", "totalAmount", "status", "status");

    private final OrderRepository orders;
    private final CartRepository carts;
    private final AddressRepository addresses;
    private final UserRepository users;
    private final ProductService products;
    private final PromoCodeService promos;
    private final DeliveryService delivery;
    private final CartService cartService;
    private final CartPricing pricing;
    private final OrderStateService state;
    private final OrderMapper mapper;
    private final ObjectMapper json;

    public OrderServiceImpl(OrderRepository orders, CartRepository carts, AddressRepository addresses,
                            UserRepository users, ProductService products, PromoCodeService promos,
                            DeliveryService delivery, CartService cartService, CartPricing pricing,
                            OrderStateService state, OrderMapper mapper, ObjectMapper json) {
        this.orders = orders;
        this.carts = carts;
        this.addresses = addresses;
        this.users = users;
        this.products = products;
        this.promos = promos;
        this.delivery = delivery;
        this.cartService = cartService;
        this.pricing = pricing;
        this.state = state;
        this.mapper = mapper;
        this.json = json;
    }

    // ------------------------------------------------------------------ create

    @Override
    @Transactional
    public OrderResponse create(Long userId, CreateOrderRequest r) {
        Cart cart = carts.findByUserId(userId).filter(c -> !c.getCartItems().isEmpty())
                .orElseThrow(() -> new BadRequestException("Your cart is empty"));
        Address address = addresses.findByIdAndUserId(r.addressId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("No address with id " + r.addressId()));

        // 1. lines at today's prices
        Order order = new Order();
        order.setUser(users.getReferenceById(userId));
        order.setShippingAddress(mapper.snapshot(address));
        order.setDeliveryMethod(r.deliveryMethod());
        order.setCustomerNote(r.customerNote());
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem ci : cart.getCartItems()) {
            Product p = ci.getProduct();
            ProductVariant v = ci.getVariant();
            if (!Boolean.TRUE.equals(p.getIsActive()) || (v != null && !Boolean.TRUE.equals(v.getIsActive()))) {
                throw new BadRequestException(p.getName() + " is no longer available; remove it from your cart");
            }
            BigDecimal unit = pricing.unitPrice(ci);
            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setProduct(p);
            oi.setVariant(v);
            oi.setQuantity(ci.getQuantity());
            oi.setPriceAtPurchase(unit);
            oi.setProductSnapshot(snapshot(p, v));
            order.getOrderItems().add(oi);
            subtotal = subtotal.add(unit.multiply(BigDecimal.valueOf(ci.getQuantity())));
        }

        // 2. promo code, checked strictly now (the cart view only warned)
        PromoResult promo = cart.getAppliedPromoCode() == null ? null
                : promos.evaluate(cart.getAppliedPromoCode(), userId, pricing.lines(cart));

        // 3. shipping, then the total in the documented order
        DeliveryQuote quote = delivery.quote(r.deliveryMethod(), subtotal);
        BigDecimal shipping = promo != null && promo.freeShipping() ? BigDecimal.ZERO : quote.cost();
        int requestedPoints = r.bonusPointsToUse() == null ? 0 : r.bonusPointsToUse();
        int availablePoints = availableBonusPoints(userId);
        if (requestedPoints > availablePoints) {
            throw new BadRequestException("You can use at most " + availablePoints + " bonus points");
        }
        OrderCalculator.Totals t = OrderCalculator.calculate(subtotal, promo == null ? BigDecimal.ZERO : promo.discount(),
                shipping, requestedPoints, availablePoints);
        order.setSubtotal(t.subtotal());
        order.setDiscount(t.discount());
        order.setShippingCost(t.shippingCost());
        order.setBonusPointsUsed(t.bonusPointsUsed());
        order.setTotalAmount(t.totalAmount());
        order.setPromoCode(promo == null ? null : promo.promo().getCode());
        order.setEstimatedDeliveryDate(quote.estimatedTo());

        // 4. stock: all or nothing, because any failure rolls this whole transaction back
        for (OrderItem oi : order.getOrderItems()) {
            products.reserveStock(oi.getProduct().getId(), oi.getVariant() == null ? null : oi.getVariant().getId(),
                    oi.getQuantity());
        }

        // 5. number, save, promo usage, empty the cart
        order.setOrderNumber(String.format("ORD-%d-%04d", LocalDate.now().getYear(), orders.nextOrderSequence()));
        orders.saveAndFlush(order);
        if (promo != null) {
            promos.recordUsage(promo.promo().getId(), userId, order.getId(), t.discount());
        }
        // the stock and promo updates above are bulk updates that detach earlier loads, so empty the cart afresh
        Cart freshCart = carts.findByUserId(userId).orElseThrow();
        freshCart.getCartItems().clear();
        freshCart.setAppliedPromoCode(null);
        freshCart.setUpdatedAt(LocalDateTime.now());
        return mapper.toResponse(orders.findById(order.getId()).orElseThrow());
    }

    /** The bonus system is an optional module and has no balances yet, so no customer has points to spend. */
    private int availableBonusPoints(Long userId) {
        return 0;
    }

    private String snapshot(Product p, ProductVariant v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("sku", v != null ? v.getSku() : p.getSku());
        if (v != null) m.put("variant", v.getName());
        m.put("image", p.getImages().stream().map(ProductImage::getImageUrl).findFirst().orElse(null));
        try {
            return json.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------- reads

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> list(Long userId, Pageable pageable) {
        Pageable p = PageRequest.of(pageable.getPageNumber(), Math.min(Math.max(pageable.getPageSize(), 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return orders.findByUserId(userId, p).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findById(Long userId, Long orderId) {
        return mapper.toResponse(owned(userId, orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findByNumber(Long userId, String orderNumber) {
        return mapper.toResponse(orders.findByOrderNumberAndUserId(orderNumber, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No order " + orderNumber)));
    }

    /** The ownership rule: an order that is not yours does not exist, as far as you can tell. */
    private Order owned(Long userId, Long orderId) {
        return orders.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No order with id " + orderId));
    }

    // ----------------------------------------------------------------- changes

    @Override
    @Transactional
    public OrderResponse cancel(Long userId, Long orderId) {
        owned(userId, orderId); // 404 for other people's orders, before taking any lock
        Order o = locked(orderId);
        if (o.getStatus() != OrderStatus.PENDING && o.getStatus() != OrderStatus.CONFIRMED) {
            throw new OrderCancellationException("An order that is " + o.getStatus() + " can no longer be cancelled by you");
        }
        if (o.getPayment() != null && o.getPayment().getStatus() == PaymentStatus.PROCESSING) {
            throw new OrderCancellationException("A payment is being processed for this order; try again in a moment");
        }
        return mapper.toResponse(state.apply(orderId, OrderStatus.CANCELLED));
    }

    @Override
    @Transactional
    public ReorderResponse reorder(Long userId, Long orderId) {
        Order o = owned(userId, orderId);
        List<CartService.ReorderLine> lines = o.getOrderItems().stream()
                .map(i -> new CartService.ReorderLine(i.getProduct().getId(),
                        i.getVariant() == null ? null : i.getVariant().getId(), i.getQuantity())).toList();
        List<String> skipped = cartService.addAvailable(userId, lines);
        return new ReorderResponse(cartService.get(CartOwner.user(userId)), skipped);
    }

    // ------------------------------------------------------------------- staff

    @Override
    @Transactional(readOnly = true)
    public Page<AdminOrderResponse> adminList(OrderStatus status, String search, LocalDate from, LocalDate to,
                                              Pageable pageable) {
        Specification<Order> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                        .replace("_", "\\_") + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("orderNumber")), like, '\\'),
                        cb.like(cb.lower(root.get("user").get("email")), like, '\\')));
            }
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), from.atStartOfDay()));
            if (to != null) ps.add(cb.lessThan(root.<LocalDateTime>get("createdAt"), to.plusDays(1).atStartOfDay()));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return orders.findAll(spec, safe(pageable)).map(mapper::toAdminResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrderResponse adminFindById(Long orderId) {
        return mapper.toAdminResponse(orders.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No order with id " + orderId)));
    }

    @Override
    @Transactional
    public AdminOrderResponse updateStatus(Long orderId, OrderStatus status) {
        Order o = locked(orderId);
        if (o.getStatus() == OrderStatus.DELIVERED && status == OrderStatus.REFUNDED) {
            throw new InvalidStatusTransitionException("A delivered order can only be refunded through a return");
        }
        return mapper.toAdminResponse(state.apply(orderId, status));
    }

    @Override
    @Transactional
    public AdminOrderResponse updateTracking(Long orderId, UpdateTrackingRequest r) {
        Order o = locked(orderId);
        if (o.getStatus() == OrderStatus.CANCELLED || o.getStatus() == OrderStatus.REFUNDED) {
            throw new InvalidStatusTransitionException("Tracking cannot be set on a " + o.getStatus() + " order");
        }
        o.setDeliveryTrackingNumber(r.trackingNumber().trim());
        if (r.estimatedDeliveryDate() != null) o.setEstimatedDeliveryDate(r.estimatedDeliveryDate());
        return mapper.toAdminResponse(o);
    }

    @Override
    @Transactional
    public AdminOrderResponse updateNote(Long orderId, UpdateOrderNoteRequest r) {
        Order o = locked(orderId);
        o.setAdminNote(r.adminNote());
        return mapper.toAdminResponse(o);
    }

    private Order locked(Long orderId) {
        return orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No order with id " + orderId));
    }

    private static Pageable safe(Pageable pageable) {
        List<Sort.Order> list = new ArrayList<>();
        for (Sort.Order o : pageable.getSort()) {
            String property = SORTABLE.get(o.getProperty());
            if (property == null) {
                throw new BadRequestException("Cannot sort by '" + o.getProperty() + "'. Allowed: "
                        + new TreeSet<>(SORTABLE.keySet()));
            }
            list.add(new Sort.Order(o.getDirection(), property));
        }
        if (list.isEmpty()) list.add(Sort.Order.desc("createdAt"));
        list.add(Sort.Order.desc("id"));
        return PageRequest.of(pageable.getPageNumber(), Math.min(Math.max(pageable.getPageSize(), 1), 100), Sort.by(list));
    }
}
