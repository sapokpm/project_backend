package com.example.shop.order;

import com.example.shop.dto.request.AddCartItemRequest;
import com.example.shop.dto.request.CreateOrderRequest;
import com.example.shop.exception.InsufficientStockException;
import com.example.shop.model.enums.DeliveryMethod;
import com.example.shop.service.CartOwner;
import com.example.shop.service.CartService;
import com.example.shop.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deliberately NOT @Transactional: the point is what survives a failed order in the real database, which a
 * test-managed transaction would hide. It makes its own user and restores the stock it touches.
 */
@SpringBootTest
class OrderAtomicityTest {
    @Autowired OrderService orders;
    @Autowired CartService carts;
    @Autowired JdbcTemplate jdbc;

    private Long userId;
    private Long addressId;

    @BeforeEach
    void setUp() {
        String email = "atomic-" + UUID.randomUUID() + "@example.com";
        jdbc.update("insert into users (email, password, first_name, last_name, role, created_at) values (?, 'x', 'A', 'B', 'CUSTOMER', now())", email);
        userId = jdbc.queryForObject("select id from users where email = ?", Long.class, email);
        jdbc.update("insert into addresses (country, city, street, recipient_name, recipient_phone, is_default, user_id) "
                + "values ('KZ','Almaty','Street 1','A B','+7000',true,?)", userId);
        addressId = jdbc.queryForObject("select id from addresses where user_id = ?", Long.class, userId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from orders where user_id = ?", userId); // cascades to items; frees the user for deletion
        jdbc.update("delete from users where id = ?", userId);        // cascades to address and cart
        jdbc.update("update products set reserved_quantity = 0 where sku in ('AP20W', 'GWSTRAP') and reserved_quantity < 0");
    }

    private Long pid(String sku) {
        return jdbc.queryForObject("select id from products where sku = ?", Long.class, sku);
    }

    private int reserved(String sku) {
        return jdbc.queryForObject("select reserved_quantity from products where sku = ?", Integer.class, sku);
    }

    @Test
    void whenTheSecondLineIsOutOfStockNothingFromTheFirstLineIsKept() {
        carts.addItem(CartOwner.user(userId), new AddCartItemRequest(pid("AP20W"), null, 1));
        carts.addItem(CartOwner.user(userId), new AddCartItemRequest(pid("GWSTRAP"), null, 3)); // stock 3
        int chargerBefore = reserved("AP20W");
        int strapBefore = reserved("GWSTRAP");
        Integer ordersBefore = jdbc.queryForObject("select count(*) from orders", Integer.class);

        jdbc.update("update products set reserved_quantity = reserved_quantity + 1 where sku = 'GWSTRAP'"); // someone else grabs one
        try {
            assertThatThrownBy(() -> orders.create(userId, new CreateOrderRequest(addressId, DeliveryMethod.COURIER, null, null)))
                    .isInstanceOf(InsufficientStockException.class);

            assertThat(reserved("AP20W")).as("first line must not stay reserved").isEqualTo(chargerBefore);
            assertThat(reserved("GWSTRAP")).isEqualTo(strapBefore + 1);
            assertThat(jdbc.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(ordersBefore);
            assertThat(jdbc.queryForObject("select count(*) from cart_items ci join carts c on c.id = ci.cart_id where c.user_id = ?",
                    Integer.class, userId)).as("cart is kept so the customer can fix it").isEqualTo(2);
        } finally {
            jdbc.update("update products set reserved_quantity = reserved_quantity - 1 where sku = 'GWSTRAP'");
        }
    }

    @Test
    void aSuccessfulOrderIsFullyWrittenAndTheCartIsEmpty() {
        carts.addItem(CartOwner.user(userId), new AddCartItemRequest(pid("AP20W"), null, 2));
        int before = reserved("AP20W");
        var created = orders.create(userId, new CreateOrderRequest(addressId, DeliveryMethod.COURIER, null, "leave at door"));
        assertThat(created.customerNote()).isEqualTo("leave at door");
        assertThat(reserved("AP20W")).isEqualTo(before + 2);
        assertThat(jdbc.queryForObject("select count(*) from cart_items ci join carts c on c.id = ci.cart_id where c.user_id = ?",
                Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from order_items where order_id = ?", Integer.class, created.id())).isEqualTo(1);
        orders.cancel(userId, created.id());
        assertThat(reserved("AP20W")).isEqualTo(before);
    }
}
