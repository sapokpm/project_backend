package com.example.shop.service.impl;

import com.example.shop.dto.request.WishlistRequest;
import com.example.shop.dto.response.CartResponse;
import com.example.shop.dto.response.WishlistItemResponse;
import com.example.shop.exception.DuplicateResourceException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.mapper.CartMapper;
import com.example.shop.mapper.ProductMapper;
import com.example.shop.model.entity.Product;
import com.example.shop.model.entity.Wishlist;
import com.example.shop.repository.ProductRepository;
import com.example.shop.repository.UserRepository;
import com.example.shop.repository.WishlistRepository;
import com.example.shop.service.CartService;
import com.example.shop.service.WishlistService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistServiceImpl implements WishlistService {
    private final WishlistRepository wishlist;
    private final ProductRepository products;
    private final UserRepository users;
    private final CartService cart;
    private final CartMapper mapper;

    public WishlistServiceImpl(WishlistRepository wishlist, ProductRepository products, UserRepository users,
                               CartService cart, CartMapper mapper) {
        this.wishlist = wishlist;
        this.products = products;
        this.users = users;
        this.cart = cart;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WishlistItemResponse> list(Long userId, Pageable pageable) {
        Pageable p = PageRequest.of(pageable.getPageNumber(), Math.min(Math.max(pageable.getPageSize(), 1), 100),
                Sort.by(Sort.Direction.DESC, "addedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return wishlist.findByUserId(userId, p).map(mapper::toResponse);
    }

    @Override
    @Transactional
    public WishlistItemResponse add(Long userId, Long productId, WishlistRequest r) {
        Product product = products.findByIdAndIsActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("No product with id " + productId));
        if (wishlist.existsByUserIdAndProductId(userId, productId)) {
            throw new DuplicateResourceException("Already in your wishlist");
        }
        Wishlist w = new Wishlist();
        w.setUser(users.getReferenceById(userId));
        w.setProduct(product);
        w.setPriceAtAdd(ProductMapper.currentPrice(product));
        w.setNotifyOnPriceDrop(r != null && Boolean.TRUE.equals(r.notifyOnPriceDrop()));
        w.setNotifyOnBackInStock(r != null && Boolean.TRUE.equals(r.notifyOnBackInStock()));
        wishlist.saveAndFlush(w);
        return mapper.toResponse(w);
    }

    @Override
    @Transactional
    public void remove(Long userId, Long productId) {
        Wishlist w = wishlist.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " is not in your wishlist"));
        wishlist.delete(w);
    }

    @Override
    @Transactional
    public CartResponse moveToCart(Long userId, Long productId) {
        Wishlist w = wishlist.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " is not in your wishlist"));
        CartResponse result = cart.addProduct(userId, productId, 1); // throws before the delete if stock is short
        wishlist.delete(w);
        return result;
    }
}
