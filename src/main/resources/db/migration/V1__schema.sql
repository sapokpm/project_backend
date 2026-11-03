-- Electronics store schema. Must match the JPA entities (ddl-auto: validate).

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password       VARCHAR(100) NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    phone          VARCHAR(32),
    role           VARCHAR(20)  NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at  TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP,
    CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'ADMIN', 'MANAGER'))
);

CREATE TABLE addresses (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(100),
    country         VARCHAR(100) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    street          VARCHAR(200) NOT NULL,
    building        VARCHAR(50),
    apartment       VARCHAR(50),
    postal_code     VARCHAR(20),
    recipient_name  VARCHAR(200) NOT NULL,
    recipient_phone VARCHAR(32)  NOT NULL,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    latitude        NUMERIC(9, 6),
    longitude       NUMERIC(9, 6),
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_address_user ON addresses (user_id);

CREATE TABLE brands (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    logo_url    VARCHAR(500),
    website_url VARCHAR(500),
    country     VARCHAR(100),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    image_url   VARCHAR(500),
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    parent_id   BIGINT REFERENCES categories (id)
);
CREATE INDEX idx_category_parent ON categories (parent_id);

CREATE TABLE products (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200)  NOT NULL,
    slug                VARCHAR(220)  NOT NULL UNIQUE,
    description         TEXT,
    short_description   VARCHAR(500),
    sku                 VARCHAR(64)   NOT NULL UNIQUE,
    base_price          NUMERIC(12, 2) NOT NULL,
    discount_price      NUMERIC(12, 2),
    discount_start_date TIMESTAMP,
    discount_end_date   TIMESTAMP,
    stock_quantity      INTEGER       NOT NULL DEFAULT 0,
    reserved_quantity   INTEGER       NOT NULL DEFAULT 0,
    low_stock_threshold INTEGER       NOT NULL DEFAULT 5,
    weight              NUMERIC(8, 3),
    dimensions          VARCHAR(100),
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    is_featured         BOOLEAN       NOT NULL DEFAULT FALSE,
    is_new_arrival      BOOLEAN       NOT NULL DEFAULT FALSE,
    average_rating      NUMERIC(3, 2) NOT NULL DEFAULT 0,
    review_count        INTEGER       NOT NULL DEFAULT 0,
    view_count          BIGINT        NOT NULL DEFAULT 0,
    sales_count         BIGINT        NOT NULL DEFAULT 0,
    meta_title          VARCHAR(200),
    meta_description    VARCHAR(500),
    category_id         BIGINT        NOT NULL REFERENCES categories (id),
    brand_id            BIGINT REFERENCES brands (id),
    created_at          TIMESTAMP     NOT NULL,
    updated_at          TIMESTAMP,
    CONSTRAINT chk_product_price CHECK (base_price >= 0),
    CONSTRAINT chk_product_stock CHECK (stock_quantity >= 0 AND reserved_quantity >= 0)
);
CREATE INDEX idx_product_category ON products (category_id);
CREATE INDEX idx_product_brand ON products (brand_id);

CREATE TABLE product_variants (
    id                BIGSERIAL PRIMARY KEY,
    sku               VARCHAR(64)    NOT NULL UNIQUE,
    name              VARCHAR(200)   NOT NULL,
    attributes        JSONB,
    price             NUMERIC(12, 2) NOT NULL,
    stock_quantity    INTEGER        NOT NULL DEFAULT 0,
    reserved_quantity INTEGER        NOT NULL DEFAULT 0,
    image_url         VARCHAR(500),
    is_active         BOOLEAN        NOT NULL DEFAULT TRUE,
    product_id        BIGINT         NOT NULL REFERENCES products (id) ON DELETE CASCADE
);
CREATE INDEX idx_variant_product ON product_variants (product_id);

CREATE TABLE product_images (
    id            BIGSERIAL PRIMARY KEY,
    image_url     VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500),
    alt_text      VARCHAR(200),
    is_primary    BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    product_id    BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE
);
CREATE INDEX idx_image_product ON product_images (product_id);

CREATE TABLE product_specifications (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    value      VARCHAR(500) NOT NULL,
    group_name VARCHAR(100),
    sort_order INTEGER      NOT NULL DEFAULT 0,
    product_id BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE
);
CREATE INDEX idx_spec_product ON product_specifications (product_id);

CREATE TABLE reviews (
    id                   BIGSERIAL PRIMARY KEY,
    rating               INTEGER     NOT NULL,
    title                VARCHAR(200),
    comment              TEXT,
    pros                 TEXT,
    cons                 TEXT,
    images               JSONB,
    is_verified_purchase BOOLEAN     NOT NULL DEFAULT FALSE,
    helpful_count        INTEGER     NOT NULL DEFAULT 0,
    not_helpful_count    INTEGER     NOT NULL DEFAULT 0,
    admin_reply          TEXT,
    admin_reply_at       TIMESTAMP,
    status               VARCHAR(20) NOT NULL,
    product_id           BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    user_id              BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at           TIMESTAMP   NOT NULL,
    updated_at           TIMESTAMP,
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_review_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT uq_review_user_product UNIQUE (user_id, product_id)
);
CREATE INDEX idx_review_product ON reviews (product_id);
CREATE INDEX idx_review_user ON reviews (user_id);

CREATE TABLE carts (
    id                 BIGSERIAL PRIMARY KEY,
    session_id         VARCHAR(100),
    applied_promo_code VARCHAR(50),
    user_id            BIGINT REFERENCES users (id) ON DELETE CASCADE,
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP
);
CREATE INDEX idx_cart_user ON carts (user_id);
CREATE INDEX idx_cart_session ON carts (session_id);

CREATE TABLE cart_items (
    id             BIGSERIAL PRIMARY KEY,
    quantity       INTEGER        NOT NULL,
    price_at_add   NUMERIC(12, 2) NOT NULL,
    cart_id        BIGINT         NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    product_id     BIGINT         NOT NULL REFERENCES products (id),
    variant_id     BIGINT REFERENCES product_variants (id),
    CONSTRAINT chk_cart_item_qty CHECK (quantity > 0)
);
CREATE INDEX idx_cart_item_cart ON cart_items (cart_id);
CREATE INDEX idx_cart_item_product ON cart_items (product_id);

CREATE TABLE wishlists (
    id                     BIGSERIAL PRIMARY KEY,
    price_at_add           NUMERIC(12, 2) NOT NULL,
    notify_on_price_drop   BOOLEAN        NOT NULL DEFAULT FALSE,
    notify_on_back_in_stock BOOLEAN       NOT NULL DEFAULT FALSE,
    user_id                BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id             BIGINT         NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    added_at               TIMESTAMP      NOT NULL,
    CONSTRAINT uq_wishlist_user_product UNIQUE (user_id, product_id)
);
CREATE INDEX idx_wishlist_user ON wishlists (user_id);

CREATE TABLE delivery_options (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(100)   NOT NULL,
    code               VARCHAR(32)    NOT NULL UNIQUE,
    description        VARCHAR(500),
    base_price         NUMERIC(12, 2) NOT NULL,
    free_threshold     NUMERIC(12, 2),
    estimated_days_min INTEGER        NOT NULL,
    estimated_days_max INTEGER        NOT NULL,
    is_active          BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE orders (
    id                       BIGSERIAL PRIMARY KEY,
    order_number             VARCHAR(32)    NOT NULL UNIQUE,
    status                   VARCHAR(20)    NOT NULL,
    shipping_country         VARCHAR(100),
    shipping_city            VARCHAR(100),
    shipping_street          VARCHAR(200),
    shipping_building        VARCHAR(50),
    shipping_apartment       VARCHAR(50),
    shipping_postal_code     VARCHAR(20),
    shipping_recipient_name  VARCHAR(200),
    shipping_recipient_phone VARCHAR(32),
    delivery_method          VARCHAR(20)    NOT NULL,
    delivery_tracking_number VARCHAR(100),
    estimated_delivery_date  DATE,
    actual_delivery_date     DATE,
    subtotal                 NUMERIC(12, 2) NOT NULL DEFAULT 0,
    shipping_cost            NUMERIC(12, 2) NOT NULL DEFAULT 0,
    discount                 NUMERIC(12, 2) NOT NULL DEFAULT 0,
    bonus_points_used        INTEGER        NOT NULL DEFAULT 0,
    bonus_points_earned      INTEGER        NOT NULL DEFAULT 0,
    total_amount             NUMERIC(12, 2) NOT NULL DEFAULT 0,
    promo_code               VARCHAR(50),
    customer_note            TEXT,
    admin_note               TEXT,
    user_id                  BIGINT         NOT NULL REFERENCES users (id),
    created_at               TIMESTAMP      NOT NULL,
    updated_at               TIMESTAMP,
    paid_at                  TIMESTAMP,
    shipped_at               TIMESTAMP,
    delivered_at             TIMESTAMP,
    CONSTRAINT chk_order_status CHECK (status IN
        ('PENDING', 'CONFIRMED', 'PAID', 'PROCESSING', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT chk_order_delivery CHECK (delivery_method IN ('COURIER', 'PICKUP_POINT', 'POST_OFFICE', 'EXPRESS'))
);
CREATE INDEX idx_order_user ON orders (user_id);
CREATE INDEX idx_order_status ON orders (status);

CREATE TABLE order_items (
    id                BIGSERIAL PRIMARY KEY,
    quantity          INTEGER        NOT NULL,
    price_at_purchase NUMERIC(12, 2) NOT NULL,
    product_snapshot  JSONB,
    order_id          BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id        BIGINT         NOT NULL REFERENCES products (id),
    variant_id        BIGINT REFERENCES product_variants (id),
    CONSTRAINT chk_order_item_qty CHECK (quantity > 0)
);
CREATE INDEX idx_order_item_order ON order_items (order_id);
CREATE INDEX idx_order_item_product ON order_items (product_id);

CREATE TABLE payments (
    id                    BIGSERIAL PRIMARY KEY,
    transaction_id        VARCHAR(100),
    payment_method        VARCHAR(20)    NOT NULL,
    status                VARCHAR(24)    NOT NULL,
    amount                NUMERIC(12, 2) NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    bonus_points_used     INTEGER        NOT NULL DEFAULT 0,
    card_last_four_digits VARCHAR(4),
    paid_at               TIMESTAMP,
    error_message         VARCHAR(500),
    error_code            VARCHAR(50),
    refunded_amount       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    metadata              JSONB,
    order_id              BIGINT         NOT NULL UNIQUE REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT chk_payment_method CHECK (payment_method IN ('CARD', 'PAYPAL', 'CASH_ON_DELIVERY', 'BONUS_POINTS')),
    CONSTRAINT chk_payment_status CHECK (status IN
        ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED'))
);
CREATE INDEX idx_payment_transaction ON payments (transaction_id);

CREATE TABLE promo_codes (
    id                   BIGSERIAL PRIMARY KEY,
    code                 VARCHAR(50)    NOT NULL UNIQUE,
    description          VARCHAR(500),
    discount_type        VARCHAR(20)    NOT NULL,
    discount_value       NUMERIC(12, 2) NOT NULL,
    min_order_amount     NUMERIC(12, 2),
    max_discount_amount  NUMERIC(12, 2),
    max_usage_count      INTEGER,
    current_usage_count  INTEGER        NOT NULL DEFAULT 0,
    max_usage_per_user   INTEGER,
    valid_from           TIMESTAMP,
    valid_to             TIMESTAMP,
    is_active            BOOLEAN        NOT NULL DEFAULT TRUE,
    created_by           BIGINT REFERENCES users (id),
    CONSTRAINT chk_promo_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT', 'FREE_SHIPPING'))
);

CREATE TABLE promo_code_categories (
    promo_code_id BIGINT NOT NULL REFERENCES promo_codes (id) ON DELETE CASCADE,
    category_id   BIGINT NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    PRIMARY KEY (promo_code_id, category_id)
);

CREATE TABLE promo_code_products (
    promo_code_id BIGINT NOT NULL REFERENCES promo_codes (id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    PRIMARY KEY (promo_code_id, product_id)
);

CREATE TABLE promo_code_usages (
    id               BIGSERIAL PRIMARY KEY,
    discount_applied NUMERIC(12, 2) NOT NULL,
    promo_code_id    BIGINT         NOT NULL REFERENCES promo_codes (id),
    user_id          BIGINT         NOT NULL REFERENCES users (id),
    order_id         BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    used_at          TIMESTAMP      NOT NULL
);
CREATE INDEX idx_promo_usage_promo_user ON promo_code_usages (promo_code_id, user_id);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(500) NOT NULL UNIQUE,
    expiry_date TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMP    NOT NULL
);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);
