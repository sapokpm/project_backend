package com.example.shop.service.impl;

import com.example.shop.dto.request.AddCartItemRequest;
import com.example.shop.dto.request.UpdateCartItemRequest;
import com.example.shop.dto.response.CartResponse;
import com.example.shop.exception.BadRequestException;
import com.example.shop.exception.InsufficientStockException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.mapper.CartMapper;
import com.example.shop.mapper.ProductMapper;
import com.example.shop.model.entity.*;
import com.example.shop.repository.*;
import com.example.shop.service.CartOwner;
import com.example.shop.service.CartPricing;
import com.example.shop.service.CartService;
import com.example.shop.service.PromoCodeService;
import com.example.shop.service.PromoResult;
import com.example.shop.exception.PromoCodeInvalidException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class CartServiceImpl implements CartService {
    private static final int MAX_LINE_QUANTITY = 100;

    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final UserRepository users;
    private final CartMapper mapper;
    private final PromoCodeService promoService;
    private final CartPricing pricing;

    public CartServiceImpl(CartRepository carts, CartItemRepository items, ProductRepository products,
                           ProductVariantRepository variants, UserRepository users, CartMapper mapper,
                           PromoCodeService promoService, CartPricing pricing) {
        this.promoService = promoService;
        this.pricing = pricing;
        this.carts = carts;
        this.items = items;
        this.products = products;
        this.variants = variants;
        this.users = users;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse get(CartOwner owner) {
        return find(owner).map(c -> present(c)).orElseGet(CartServiceImpl::emptyCart);
    }

    @Override
    @Transactional
    public CartResponse addItem(CartOwner owner, AddCartItemRequest r) {
        Cart cart = findOrCreate(owner);
        Product product = products.findByIdAndIsActiveTrue(r.productId())
                .orElseThrow(() -> new ResourceNotFoundException("No product with id " + r.productId()));
        ProductVariant variant = resolveVariant(product, r.variantId());

        CartItem line = cart.getCartItems().stream()
                .filter(i -> i.getProduct().getId().equals(product.getId())
                        && Objects.equals(variantId(i), variant == null ? null : variant.getId()))
                .findFirst().orElse(null);
        int newQuantity = (line == null ? 0 : line.getQuantity()) + r.quantity();
        checkQuantity(product, variant, newQuantity);

        if (line == null) {
            line = new CartItem();
            line.setCart(cart);
            line.setProduct(product);
            line.setVariant(variant);
            line.setPriceAtAdd(unitPrice(product, variant));
            cart.getCartItems().add(line);
        }
        line.setQuantity(newQuantity); // priceAtAdd of an existing line stays: it records when the customer first saw it
        return save(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItem(CartOwner owner, Long itemId, UpdateCartItemRequest r) {
        Cart cart = find(owner).orElseThrow(() -> new ResourceNotFoundException("No cart item with id " + itemId));
        CartItem line = items.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No cart item with id " + itemId));
        checkQuantity(line.getProduct(), line.getVariant(), r.quantity());
        line.setQuantity(r.quantity());
        return save(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(CartOwner owner, Long itemId) {
        Cart cart = find(owner).orElseThrow(() -> new ResourceNotFoundException("No cart item with id " + itemId));
        CartItem line = items.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No cart item with id " + itemId));
        cart.getCartItems().remove(line); // orphanRemoval deletes the row
        return save(cart);
    }

    @Override
    @Transactional
    public CartResponse clear(CartOwner owner) {
        Optional<Cart> cart = find(owner);
        if (cart.isEmpty()) return emptyCart();
        cart.get().getCartItems().clear();
        cart.get().setAppliedPromoCode(null);
        return save(cart.get());
    }

    @Override
    @Transactional
    public CartResponse merge(Long userId, String guestSessionId) {
        Cart mine = findOrCreate(CartOwner.user(userId));
        Optional<Cart> guest = carts.findBySessionIdAndUserIsNull(guestSessionId);
        if (guest.isEmpty()) return present(mine);

        for (CartItem g : new ArrayList<>(guest.get().getCartItems())) {
            Product product = g.getProduct();
            ProductVariant variant = g.getVariant();
            int sellable = sellableQuantity(product, variant);
            if (sellable <= 0) continue; // gone or sold out since it was added: drop it silently

            CartItem existing = mine.getCartItems().stream()
                    .filter(i -> i.getProduct().getId().equals(product.getId())
                            && Objects.equals(variantId(i), variant == null ? null : variant.getId()))
                    .findFirst().orElse(null);
            int wanted = (existing == null ? 0 : existing.getQuantity()) + g.getQuantity();
            int quantity = Math.min(Math.min(wanted, sellable), MAX_LINE_QUANTITY); // never fail a login over stock
            if (existing == null) {
                existing = new CartItem();
                existing.setCart(mine);
                existing.setProduct(product);
                existing.setVariant(variant);
                existing.setPriceAtAdd(g.getPriceAtAdd());
                mine.getCartItems().add(existing);
            }
            existing.setQuantity(quantity);
        }
        carts.delete(guest.get());
        carts.flush();
        return save(mine);
    }

    @Override
    @Transactional
    public List<String> addAvailable(Long userId, List<ReorderLine> lines) {
        List<String> skipped = new ArrayList<>();
        for (ReorderLine l : lines) {
            try {
                // direct call on this bean (no proxy), so a refused line cannot mark the transaction rollback-only
                addItem(CartOwner.user(userId), new AddCartItemRequest(l.productId(), l.variantId(),
                        Math.min(l.quantity(), MAX_LINE_QUANTITY)));
            } catch (ResourceNotFoundException | InsufficientStockException | BadRequestException e) {
                skipped.add(e.getMessage());
            }
        }
        return skipped;
    }

    @Override
    @Transactional
    public CartResponse applyPromo(Long userId, String code) {
        Cart cart = carts.findByUserId(userId).filter(c -> !c.getCartItems().isEmpty())
                .orElseThrow(() -> new BadRequestException("Your cart is empty"));
        PromoResult result = promoService.evaluate(code, userId, pricing.lines(cart)); // throws with the reason
        cart.setAppliedPromoCode(result.promo().getCode());
        return save(cart);
    }

    @Override
    @Transactional
    public CartResponse removePromo(Long userId) {
        Optional<Cart> cart = carts.findByUserId(userId);
        if (cart.isEmpty()) return emptyCart();
        cart.get().setAppliedPromoCode(null);
        return save(cart.get());
    }

    @Override
    @Transactional
    public CartResponse addProduct(Long userId, Long productId, int quantity) {
        return addItem(CartOwner.user(userId), new AddCartItemRequest(productId, null, quantity));
    }

    // ---------------------------------------------------------------- helpers

    private Optional<Cart> find(CartOwner owner) {
        if (!owner.isGuest()) return carts.findByUserId(owner.userId());
        if (owner.sessionId() == null) return Optional.empty();
        return carts.findBySessionIdAndUserIsNull(owner.sessionId());
    }

    private Cart findOrCreate(CartOwner owner) {
        return find(owner).orElseGet(() -> {
            Cart c = new Cart();
            if (owner.isGuest()) {
                if (owner.sessionId() == null) throw new BadRequestException("A guest cart needs a session id");
                c.setSessionId(owner.sessionId());
            } else {
                c.setUser(users.getReferenceById(owner.userId()));
            }
            return carts.save(c);
        });
    }

    private ProductVariant resolveVariant(Product product, Long variantId) {
        List<ProductVariant> active = variants.findByProductIdAndIsActiveTrue(product.getId());
        if (variantId == null) {
            if (!active.isEmpty()) throw new BadRequestException("This product has options; choose a variant");
            return null;
        }
        return active.stream().filter(v -> v.getId().equals(variantId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No variant " + variantId + " on product " + product.getId()));
    }

    private static Long variantId(CartItem i) {
        return i.getVariant() == null ? null : i.getVariant().getId();
    }

    private static BigDecimal unitPrice(Product p, ProductVariant v) {
        return v != null ? v.getPrice() : ProductMapper.currentPrice(p);
    }

    private static int sellableQuantity(Product p, ProductVariant v) {
        if (!Boolean.TRUE.equals(p.getIsActive()) || (v != null && !Boolean.TRUE.equals(v.getIsActive()))) return 0;
        return v != null ? Math.max(0, v.getStockQuantity() - v.getReservedQuantity()) : ProductMapper.available(p);
    }

    private static void checkQuantity(Product p, ProductVariant v, int quantity) {
        if (quantity > MAX_LINE_QUANTITY) {
            throw new BadRequestException("You can have at most " + MAX_LINE_QUANTITY + " of one item in the cart");
        }
        int sellable = sellableQuantity(p, v);
        if (quantity > sellable) {
            throw new InsufficientStockException(sellable == 0
                    ? p.getName() + " is out of stock"
                    : "Only " + sellable + " of " + p.getName() + " available");
        }
    }

    private CartResponse save(Cart cart) {
        cart.setUpdatedAt(LocalDateTime.now());
        carts.saveAndFlush(cart);
        return present(cart);
    }

    /** The discount is recomputed on every view: a code that expired overnight stops discounting at once. */
    private CartResponse present(Cart cart) {
        String code = cart.getAppliedPromoCode();
        if (code == null || cart.getUser() == null) return mapper.toResponse(cart, BigDecimal.ZERO, false, null);
        try {
            PromoResult r = promoService.evaluate(code, cart.getUser().getId(), pricing.lines(cart));
            return mapper.toResponse(cart, r.discount(), r.freeShipping(), null);
        } catch (PromoCodeInvalidException e) {
            return mapper.toResponse(cart, BigDecimal.ZERO, false, e.getMessage());
        }
    }

    private static CartResponse emptyCart() {
        return new CartResponse(null, List.of(), 0, BigDecimal.ZERO, null, BigDecimal.ZERO, false, null, BigDecimal.ZERO);
    }
}
