package com.example.shop.order;

import com.example.shop.dto.request.CreateOrderRequest;
import com.example.shop.dto.request.UpdateOrderNoteRequest;
import com.example.shop.dto.request.UpdateTrackingRequest;
import com.example.shop.dto.request.AddCartItemRequest;
import com.example.shop.exception.*;
import com.example.shop.model.enums.DeliveryMethod;
import com.example.shop.model.enums.OrderStatus;
import com.example.shop.service.CartOwner;
import com.example.shop.service.CartService;
import com.example.shop.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static com.example.shop.model.enums.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Staff status changes and their side effects on stock, payment and promo usage. Rolled back after each test. */
@SpringBootTest
@Transactional
class OrderStatusServiceTest {
    @Autowired OrderService orders;
    @Autowired CartService carts;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;

    private Long aidaId() {
        return jdbc.queryForObject("select id from users where email = 'customer@shop.local'", Long.class);
    }

    private Long aidaAddress() {
        return jdbc.queryForObject("select id from addresses where user_id = ?", Long.class, aidaId());
    }

    private Long pid(String sku) {
        return jdbc.queryForObject("select id from products where sku = ?", Long.class, sku);
    }

    private int stock(String sku) { return jdbc.queryForObject("select stock_quantity from products where sku = ?", Integer.class, sku); }

    private int reserved(String sku) { return jdbc.queryForObject("select reserved_quantity from products where sku = ?", Integer.class, sku); }

    private long sales(String sku) { return jdbc.queryForObject("select sales_count from products where sku = ?", Long.class, sku); }

    private Long orderIdOf(String number) {
        return jdbc.queryForObject("select id from orders where order_number = ?", Long.class, number);
    }

    /** Aida orders one Galaxy Watch Strap (stock 3). */
    private Long placeOrder() {
        carts.addItem(CartOwner.user(aidaId()), new AddCartItemRequest(pid("GWSTRAP"), null, 1));
        return orders.create(aidaId(), new CreateOrderRequest(aidaAddress(), DeliveryMethod.COURIER, null, null)).id();
    }

    private OrderStatus status(Long id) {
        em.flush();
        em.clear();
        return OrderStatus.valueOf(jdbc.queryForObject("select status from orders where id = ?", String.class, id));
    }

    @Test
    void seedStockMatchesTheSeededOrders() {
        assertThat(reserved("GS24")).isEqualTo(1);        // ORD-2026-0001 PENDING
        assertThat(reserved("MBA13")).isEqualTo(1);       // ORD-2026-0002 PAID
        assertThat(reserved("AP20W")).isEqualTo(2);
        assertThat(stock("WH1000XM5")).isEqualTo(44);     // ORD-2026-0003 SHIPPED: left stock
        assertThat(sales("IPH15")).isEqualTo(1);          // ORD-2026-0004 DELIVERED
        assertThat(reserved("WH1000XM5")).isZero();
    }

    @Test
    void anOrderWalksFromPendingToDeliveredAndStockLeavesWhenItShips() {
        Long id = placeOrder();
        assertThat(reserved("GWSTRAP")).isEqualTo(1);
        int stockBefore = stock("GWSTRAP");
        long salesBefore = sales("GWSTRAP");

        for (OrderStatus next : new OrderStatus[]{CONFIRMED, PAID, PROCESSING}) {
            assertThat(orders.updateStatus(id, next).order().status()).isEqualTo(next);
        }
        assertThat(reserved("GWSTRAP")).isEqualTo(1); // still only reserved
        assertThat(orders.updateStatus(id, SHIPPED).order().shippedAt()).isNotNull();
        assertThat(reserved("GWSTRAP")).isZero();     // reserved -> gone
        assertThat(stock("GWSTRAP")).isEqualTo(stockBefore - 1);
        assertThat(sales("GWSTRAP")).isEqualTo(salesBefore + 1);

        orders.updateStatus(id, IN_TRANSIT);
        var delivered = orders.updateStatus(id, DELIVERED).order();
        assertThat(delivered.status()).isEqualTo(DELIVERED);
        assertThat(delivered.deliveredAt()).isNotNull();
        assertThat(delivered.actualDeliveryDate()).isEqualTo(LocalDate.now());
        assertThat(delivered.paidAt()).isNotNull();
        // delivery earns the customer the verified-purchase badge on this product
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from order_items oi join orders o on o.id = oi.order_id "
                + "where o.status = 'DELIVERED' and oi.product_id = ? and o.user_id = ?", Integer.class, pid("GWSTRAP"), aidaId())).isEqualTo(1);
    }

    @Test
    void illegalMovesAreRefusedAndChangeNothing() {
        Long id = placeOrder();
        assertThatThrownBy(() -> orders.updateStatus(id, SHIPPED)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> orders.updateStatus(id, DELIVERED)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> orders.updateStatus(id, PENDING)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(status(id)).isEqualTo(PENDING);
        assertThat(reserved("GWSTRAP")).isEqualTo(1);

        Long delivered = orderIdOf("ORD-2026-0004");
        assertThatThrownBy(() -> orders.updateStatus(delivered, PENDING)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> orders.updateStatus(delivered, CANCELLED)).isInstanceOf(OrderCancellationException.class);
        // refunding a delivered order only happens through a return
        assertThatThrownBy(() -> orders.updateStatus(delivered, REFUNDED))
                .isInstanceOf(InvalidStatusTransitionException.class).hasMessageContaining("return");
        assertThatThrownBy(() -> orders.updateStatus(999999L, PAID)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void terminalStatesStayTerminal() {
        Long id = placeOrder();
        orders.updateStatus(id, CANCELLED);
        for (OrderStatus s : OrderStatus.values()) {
            assertThatThrownBy(() -> orders.updateStatus(id, s)).isInstanceOf(RuntimeException.class);
        }
        assertThat(status(id)).isEqualTo(CANCELLED);
    }

    @Test
    void staffCancellingAProcessingOrderReleasesTheReservation() {
        Long id = placeOrder();
        orders.updateStatus(id, CONFIRMED);
        orders.updateStatus(id, PAID);
        orders.updateStatus(id, PROCESSING);
        assertThat(reserved("GWSTRAP")).isEqualTo(1);
        orders.updateStatus(id, CANCELLED);
        assertThat(reserved("GWSTRAP")).isZero();
    }

    @Test
    void refundingAPaidOrderReleasesStockAndMarksThePaymentRefunded() {
        Long id = orderIdOf("ORD-2026-0002"); // PAID, holds MBA13 x1 and AP20W x2, payment COMPLETED 1152.00
        orders.updateStatus(id, REFUNDED);
        assertThat(reserved("MBA13")).isZero();
        assertThat(reserved("AP20W")).isZero();
        em.flush();
        assertThat(jdbc.queryForObject("select status from payments where order_id = ?", String.class, id)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("select refunded_amount from payments where order_id = ?", java.math.BigDecimal.class, id))
                .isEqualByComparingTo("1152.00");
    }

    @Test
    void trackingAndNotesAreStaffFieldsButTrackingNeedsALiveOrder() {
        Long id = placeOrder();
        var withTracking = orders.updateTracking(id, new UpdateTrackingRequest(" KZ123456789 ", LocalDate.now().plusDays(3)));
        assertThat(withTracking.order().deliveryTrackingNumber()).isEqualTo("KZ123456789");
        assertThat(withTracking.order().estimatedDeliveryDate()).isEqualTo(LocalDate.now().plusDays(3));
        assertThat(orders.updateNote(id, new UpdateOrderNoteRequest("call before delivery")).adminNote()).isEqualTo("call before delivery");
        orders.updateStatus(id, CANCELLED);
        assertThatThrownBy(() -> orders.updateTracking(id, new UpdateTrackingRequest("X1", null)))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void adminListFiltersAndSorts() {
        var all = orders.adminList(null, null, null, null, PageRequest.of(0, 20));
        assertThat(all.getTotalElements()).isEqualTo(5);
        assertThat(all.getContent().get(0).customerEmail()).isEqualTo("customer@shop.local");
        assertThat(orders.adminList(SHIPPED, null, null, null, PageRequest.of(0, 20)).getContent())
                .extracting(o -> o.order().orderNumber()).containsExactly("ORD-2026-0003");
        assertThat(orders.adminList(null, "0004", null, null, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
        assertThat(orders.adminList(null, "CUSTOMER@shop", null, null, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(5);
        assertThat(orders.adminList(null, "100%", null, null, PageRequest.of(0, 20)).getTotalElements()).isZero(); // % is literal
        assertThat(orders.adminList(null, null, LocalDate.now().plusDays(1), null, PageRequest.of(0, 20)).getTotalElements()).isZero();
        assertThat(orders.adminList(null, null, null, LocalDate.now(), PageRequest.of(0, 20)).getTotalElements()).isEqualTo(5);
        var byTotal = orders.adminList(null, null, null, null, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "totalAmount")));
        assertThat(byTotal.getContent().get(0).order().orderNumber()).isEqualTo("ORD-2026-0002"); // 1152.00, then 1017.00
        assertThatThrownBy(() -> orders.adminList(null, null, null, null, PageRequest.of(0, 2, Sort.by("user.password"))))
                .isInstanceOf(BadRequestException.class);
    }
}
