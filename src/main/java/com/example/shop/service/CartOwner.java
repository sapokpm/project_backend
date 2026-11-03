package com.example.shop.service;

/** Whose cart: a logged-in user, or a guest identified by a server-issued session id (null = no cart yet). */
public record CartOwner(Long userId, String sessionId) {
    public static CartOwner user(Long userId) {
        return new CartOwner(userId, null);
    }

    public static CartOwner guest(String sessionId) {
        return new CartOwner(null, sessionId);
    }

    public boolean isGuest() {
        return userId == null;
    }
}
