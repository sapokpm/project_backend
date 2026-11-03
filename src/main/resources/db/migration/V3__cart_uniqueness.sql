-- One cart per logged-in user and one per guest session (guards against double-submit races).
CREATE UNIQUE INDEX uq_cart_user ON carts (user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_cart_session ON carts (session_id) WHERE session_id IS NOT NULL;
