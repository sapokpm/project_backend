-- Demo seed data.
-- Demo logins (change before any real deployment):
--   customer@shop.local / Customer123!
--   admin@shop.local    / Admin123!
-- Passwords below are BCrypt hashes.

INSERT INTO users (email, password, first_name, last_name, phone, role, enabled, email_verified, created_at) VALUES
('customer@shop.local', '$2y$10$dglSYJzBy7eENmo72GeCS.vOvNdQqP49oErVicMNc/tEvOlGl3D3e', 'Aida', 'Customer', '+77010000001', 'CUSTOMER', TRUE, TRUE, NOW()),
('admin@shop.local',    '$2y$10$y37Lx.HAZqj2x7YORgAaB.1x1ppVGXitXbfPuiM.Ip59sSwcBQeai', 'Alex', 'Admin',    '+77010000002', 'ADMIN',    TRUE, TRUE, NOW());

INSERT INTO addresses (title, country, city, street, building, apartment, postal_code, recipient_name, recipient_phone, is_default, user_id)
SELECT 'Home', 'Kazakhstan', 'Almaty', 'Abay Ave', '10', '25', '050000', 'Aida Customer', '+77010000001', TRUE, id
FROM users WHERE email = 'customer@shop.local';

INSERT INTO brands (name, slug, description, country) VALUES
('Apple',   'apple',   'Consumer electronics and software', 'USA'),
('Samsung', 'samsung', 'Phones, TVs and home electronics',  'South Korea'),
('Sony',    'sony',    'Audio, TVs and gaming',             'Japan');

INSERT INTO categories (name, slug, description, sort_order, parent_id) VALUES
('Phones', 'phones', 'Mobile phones', 1, NULL),
('Laptops', 'laptops', 'Laptops and notebooks', 3, NULL),
('Audio', 'audio', 'Headphones and speakers', 4, NULL),
('Accessories', 'accessories', 'Chargers, cases and cables', 5, NULL);
INSERT INTO categories (name, slug, description, sort_order, parent_id)
SELECT 'Smartphones', 'smartphones', 'Modern smartphones', 2, id FROM categories WHERE slug = 'phones';

INSERT INTO delivery_options (name, code, description, base_price, free_threshold, estimated_days_min, estimated_days_max) VALUES
('Courier',      'COURIER',      'Delivery to your door',        5.00, 100.00, 2, 4),
('Pickup point', 'PICKUP_POINT', 'Collect from a pickup point',  2.00,  50.00, 2, 5),
('Post office',  'POST_OFFICE',  'Delivery to a post office',    3.00,  80.00, 4, 8),
('Express',      'EXPRESS',      'Next day delivery',           15.00, NULL,   1, 1);

INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'iPhone 15', 'iphone-15', 'iPhone 15 - full description.', 'iPhone 15 short description', 'IPH15', 799.00, 749.00, NOW() - INTERVAL '7 days', NOW() + INTERVAL '30 days', 50, TRUE, TRUE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'smartphones' AND b.slug = 'apple';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'iPhone 15 Pro', 'iphone-15-pro', 'iPhone 15 Pro - full description.', 'iPhone 15 Pro short description', 'IPH15P', 999.00, NULL, NULL, NULL, 40, FALSE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'smartphones' AND b.slug = 'apple';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'Galaxy S24', 'galaxy-s24', 'Galaxy S24 - full description.', 'Galaxy S24 short description', 'GS24', 849.00, 799.00, NOW() - INTERVAL '7 days', NOW() + INTERVAL '30 days', 60, FALSE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'smartphones' AND b.slug = 'samsung';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'Galaxy S24 Ultra', 'galaxy-s24-ultra', 'Galaxy S24 Ultra - full description.', 'Galaxy S24 Ultra short description', 'GS24U', 1199.00, NULL, NULL, NULL, 30, FALSE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'smartphones' AND b.slug = 'samsung';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'Galaxy A55', 'galaxy-a55', 'Galaxy A55 - full description.', 'Galaxy A55 short description', 'GA55', 449.00, 399.00, NOW() - INTERVAL '7 days', NOW() + INTERVAL '30 days', 80, TRUE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'smartphones' AND b.slug = 'samsung';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'Xperia 1 V', 'xperia-1-v', 'Xperia 1 V - full description.', 'Xperia 1 V short description', 'XP1V', 1099.00, NULL, NULL, NULL, 15, FALSE, TRUE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'smartphones' AND b.slug = 'sony';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'MacBook Air 13', 'macbook-air-13', 'MacBook Air 13 - full description.', 'MacBook Air 13 short description', 'MBA13', 1099.00, NULL, NULL, NULL, 25, FALSE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'laptops' AND b.slug = 'apple';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'MacBook Pro 14', 'macbook-pro-14', 'MacBook Pro 14 - full description.', 'MacBook Pro 14 short description', 'MBP14', 1999.00, 1899.00, NOW() - INTERVAL '7 days', NOW() + INTERVAL '30 days', 12, FALSE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'laptops' AND b.slug = 'apple';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'Galaxy Book4', 'galaxy-book4', 'Galaxy Book4 - full description.', 'Galaxy Book4 short description', 'GBK4', 1099.00, 999.00, NOW() - INTERVAL '7 days', NOW() + INTERVAL '30 days', 20, TRUE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'laptops' AND b.slug = 'samsung';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'Galaxy Book4 Pro', 'galaxy-book4-pro', 'Galaxy Book4 Pro - full description.', 'Galaxy Book4 Pro short description', 'GBK4P', 1499.00, NULL, NULL, NULL, 10, FALSE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'laptops' AND b.slug = 'samsung';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'Sony WH-1000XM5', 'sony-wh-1000xm5', 'Sony WH-1000XM5 - full description.', 'Sony WH-1000XM5 short description', 'WH1000XM5', 399.00, 349.00, NOW() - INTERVAL '7 days', NOW() + INTERVAL '30 days', 45, FALSE, TRUE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'audio' AND b.slug = 'sony';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'Sony WF-1000XM5', 'sony-wf-1000xm5', 'Sony WF-1000XM5 - full description.', 'Sony WF-1000XM5 short description', 'WF1000XM5', 299.00, NULL, NULL, NULL, 55, FALSE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'audio' AND b.slug = 'sony';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'AirPods Pro 2', 'airpods-pro-2', 'AirPods Pro 2 - full description.', 'AirPods Pro 2 short description', 'APP2', 249.00, 229.00, NOW() - INTERVAL '7 days', NOW() + INTERVAL '30 days', 70, TRUE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'audio' AND b.slug = 'apple';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'AirPods Max', 'airpods-max', 'AirPods Max - full description.', 'AirPods Max short description', 'APMAX', 549.00, NULL, NULL, NULL, 18, FALSE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'audio' AND b.slug = 'apple';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'Galaxy Buds2 Pro', 'galaxy-buds2-pro', 'Galaxy Buds2 Pro - full description.', 'Galaxy Buds2 Pro short description', 'GBP2', 229.00, 199.00, NOW() - INTERVAL '7 days', NOW() + INTERVAL '30 days', 65, FALSE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'audio' AND b.slug = 'samsung';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'Sony SRS-XB100', 'sony-srs-xb100', 'Sony SRS-XB100 - full description.', 'Sony SRS-XB100 short description', 'SRSXB100', 59.00, NULL, NULL, NULL, 100, FALSE, TRUE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'audio' AND b.slug = 'sony';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'Apple 20W USB-C Charger', 'apple-20w-usb-c-charger', 'Apple 20W USB-C Charger - full description.', 'Apple 20W USB-C Charger short description', 'AP20W', 19.00, NULL, NULL, NULL, 200, TRUE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'accessories' AND b.slug = 'apple';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'MagSafe Charger', 'magsafe-charger', 'MagSafe Charger - full description.', 'MagSafe Charger short description', 'MAGSAFE', 39.00, NULL, NULL, NULL, 150, FALSE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'accessories' AND b.slug = 'apple';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'Samsung 45W Charger', 'samsung-45w-charger', 'Samsung 45W Charger - full description.', 'Samsung 45W Charger short description', 'SS45W', 35.00, 29.00, NOW() - INTERVAL '7 days', NOW() + INTERVAL '30 days', 120, FALSE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'accessories' AND b.slug = 'samsung';
INSERT INTO products (name, slug, description, short_description, sku, base_price, discount_price, discount_start_date, discount_end_date, stock_quantity, is_featured, is_new_arrival, category_id, brand_id, created_at)
SELECT 'Galaxy Watch Strap', 'galaxy-watch-strap', 'Galaxy Watch Strap - full description.', 'Galaxy Watch Strap short description', 'GWSTRAP', 25.00, NULL, NULL, NULL, 3, FALSE, FALSE, c.id, b.id, NOW()
FROM categories c, brands b WHERE c.slug = 'accessories' AND b.slug = 'samsung';

INSERT INTO product_images (image_url, thumbnail_url, alt_text, is_primary, sort_order, product_id)
SELECT '/files/products/' || slug || '.jpg', '/files/products/' || slug || '-thumb.jpg', name, TRUE, 0, id FROM products;

INSERT INTO product_specifications (name, value, group_name, sort_order, product_id)
SELECT 'Warranty', '12 months', 'General', 0, id FROM products;

INSERT INTO promo_codes (code, description, discount_type, discount_value, min_order_amount, max_discount_amount, max_usage_count, max_usage_per_user, valid_from, valid_to, is_active, created_by)
SELECT 'WELCOME10', '10% off your order', 'PERCENTAGE', 10.00, 50.00, 100.00, 1000, 1, NOW() - INTERVAL '30 days', NOW() + INTERVAL '180 days', TRUE, id FROM users WHERE email = 'admin@shop.local';
INSERT INTO promo_codes (code, description, discount_type, discount_value, min_order_amount, max_usage_count, max_usage_per_user, valid_from, valid_to, is_active, created_by)
SELECT 'SAVE20', '20 off orders over 200', 'FIXED_AMOUNT', 20.00, 200.00, 500, 2, NOW() - INTERVAL '30 days', NOW() + INTERVAL '90 days', TRUE, id FROM users WHERE email = 'admin@shop.local';
INSERT INTO promo_codes (code, description, discount_type, discount_value, valid_from, valid_to, is_active, created_by)
SELECT 'OLDSALE', 'Expired code for testing', 'PERCENTAGE', 15.00, NOW() - INTERVAL '120 days', NOW() - INTERVAL '30 days', TRUE, id FROM users WHERE email = 'admin@shop.local';

INSERT INTO orders (order_number, status, delivery_method, shipping_country, shipping_city, shipping_street, shipping_building, shipping_apartment, shipping_postal_code, shipping_recipient_name, shipping_recipient_phone, promo_code, user_id, created_at, paid_at, shipped_at, delivered_at)
SELECT 'ORD-2026-0001', 'PENDING', 'COURIER', a.country, a.city, a.street, a.building, a.apartment, a.postal_code, a.recipient_name, a.recipient_phone, NULL, u.id,
 NOW() - INTERVAL '10 days',
 NULL,
 NULL,
 NULL
FROM users u JOIN addresses a ON a.user_id = u.id WHERE u.email = 'customer@shop.local';
INSERT INTO order_items (quantity, price_at_purchase, product_snapshot, order_id, product_id)
SELECT 1, COALESCE(p.discount_price, p.base_price), jsonb_build_object('name', p.name, 'sku', p.sku), o.id, p.id
FROM orders o, products p WHERE o.order_number = 'ORD-2026-0001' AND p.sku = 'GS24';
UPDATE orders o SET subtotal = sub.s,
  shipping_cost = CASE WHEN sub.s >= COALESCE(d.free_threshold, 1e12) THEN 0 ELSE d.base_price END,
  discount = CASE WHEN o.promo_code = 'WELCOME10' THEN ROUND(LEAST(sub.s * 0.10, 100), 2) ELSE 0 END
FROM (SELECT order_id, SUM(quantity * price_at_purchase) AS s FROM order_items GROUP BY order_id) sub, delivery_options d
WHERE sub.order_id = o.id AND d.code = o.delivery_method AND o.order_number = 'ORD-2026-0001';
UPDATE orders SET total_amount = subtotal + shipping_cost - discount WHERE order_number = 'ORD-2026-0001';
INSERT INTO orders (order_number, status, delivery_method, shipping_country, shipping_city, shipping_street, shipping_building, shipping_apartment, shipping_postal_code, shipping_recipient_name, shipping_recipient_phone, promo_code, user_id, created_at, paid_at, shipped_at, delivered_at)
SELECT 'ORD-2026-0002', 'PAID', 'EXPRESS', a.country, a.city, a.street, a.building, a.apartment, a.postal_code, a.recipient_name, a.recipient_phone, NULL, u.id,
 NOW() - INTERVAL '8 days',
 NOW() - INTERVAL '7 days',
 NULL,
 NULL
FROM users u JOIN addresses a ON a.user_id = u.id WHERE u.email = 'customer@shop.local';
INSERT INTO order_items (quantity, price_at_purchase, product_snapshot, order_id, product_id)
SELECT 1, COALESCE(p.discount_price, p.base_price), jsonb_build_object('name', p.name, 'sku', p.sku), o.id, p.id
FROM orders o, products p WHERE o.order_number = 'ORD-2026-0002' AND p.sku = 'MBA13';
INSERT INTO order_items (quantity, price_at_purchase, product_snapshot, order_id, product_id)
SELECT 2, COALESCE(p.discount_price, p.base_price), jsonb_build_object('name', p.name, 'sku', p.sku), o.id, p.id
FROM orders o, products p WHERE o.order_number = 'ORD-2026-0002' AND p.sku = 'AP20W';
UPDATE orders o SET subtotal = sub.s,
  shipping_cost = CASE WHEN sub.s >= COALESCE(d.free_threshold, 1e12) THEN 0 ELSE d.base_price END,
  discount = CASE WHEN o.promo_code = 'WELCOME10' THEN ROUND(LEAST(sub.s * 0.10, 100), 2) ELSE 0 END
FROM (SELECT order_id, SUM(quantity * price_at_purchase) AS s FROM order_items GROUP BY order_id) sub, delivery_options d
WHERE sub.order_id = o.id AND d.code = o.delivery_method AND o.order_number = 'ORD-2026-0002';
UPDATE orders SET total_amount = subtotal + shipping_cost - discount WHERE order_number = 'ORD-2026-0002';
INSERT INTO payments (transaction_id, payment_method, status, amount, currency, card_last_four_digits, paid_at, error_code, error_message, order_id)
SELECT 'TXN-0002', 'CARD', 'COMPLETED', total_amount, 'USD', '4242', paid_at, NULL, NULL, id
FROM orders WHERE order_number = 'ORD-2026-0002';
INSERT INTO orders (order_number, status, delivery_method, shipping_country, shipping_city, shipping_street, shipping_building, shipping_apartment, shipping_postal_code, shipping_recipient_name, shipping_recipient_phone, promo_code, user_id, created_at, paid_at, shipped_at, delivered_at)
SELECT 'ORD-2026-0003', 'SHIPPED', 'COURIER', a.country, a.city, a.street, a.building, a.apartment, a.postal_code, a.recipient_name, a.recipient_phone, 'WELCOME10', u.id,
 NOW() - INTERVAL '6 days',
 NOW() - INTERVAL '5 days',
 NOW() - INTERVAL '4 days',
 NULL
FROM users u JOIN addresses a ON a.user_id = u.id WHERE u.email = 'customer@shop.local';
INSERT INTO order_items (quantity, price_at_purchase, product_snapshot, order_id, product_id)
SELECT 1, COALESCE(p.discount_price, p.base_price), jsonb_build_object('name', p.name, 'sku', p.sku), o.id, p.id
FROM orders o, products p WHERE o.order_number = 'ORD-2026-0003' AND p.sku = 'WH1000XM5';
UPDATE orders o SET subtotal = sub.s,
  shipping_cost = CASE WHEN sub.s >= COALESCE(d.free_threshold, 1e12) THEN 0 ELSE d.base_price END,
  discount = CASE WHEN o.promo_code = 'WELCOME10' THEN ROUND(LEAST(sub.s * 0.10, 100), 2) ELSE 0 END
FROM (SELECT order_id, SUM(quantity * price_at_purchase) AS s FROM order_items GROUP BY order_id) sub, delivery_options d
WHERE sub.order_id = o.id AND d.code = o.delivery_method AND o.order_number = 'ORD-2026-0003';
UPDATE orders SET total_amount = subtotal + shipping_cost - discount WHERE order_number = 'ORD-2026-0003';
INSERT INTO payments (transaction_id, payment_method, status, amount, currency, card_last_four_digits, paid_at, error_code, error_message, order_id)
SELECT 'TXN-0003', 'CARD', 'COMPLETED', total_amount, 'USD', '4242', paid_at, NULL, NULL, id
FROM orders WHERE order_number = 'ORD-2026-0003';
INSERT INTO promo_code_usages (discount_applied, promo_code_id, user_id, order_id, used_at)
SELECT o.discount, pc.id, o.user_id, o.id, o.created_at FROM orders o, promo_codes pc WHERE o.order_number = 'ORD-2026-0003' AND pc.code = 'WELCOME10';
UPDATE promo_codes SET current_usage_count = current_usage_count + 1 WHERE code = 'WELCOME10';
INSERT INTO orders (order_number, status, delivery_method, shipping_country, shipping_city, shipping_street, shipping_building, shipping_apartment, shipping_postal_code, shipping_recipient_name, shipping_recipient_phone, promo_code, user_id, created_at, paid_at, shipped_at, delivered_at)
SELECT 'ORD-2026-0004', 'DELIVERED', 'PICKUP_POINT', a.country, a.city, a.street, a.building, a.apartment, a.postal_code, a.recipient_name, a.recipient_phone, NULL, u.id,
 NOW() - INTERVAL '4 days',
 NOW() - INTERVAL '3 days',
 NOW() - INTERVAL '2 days',
 NOW() - INTERVAL '4 days'
FROM users u JOIN addresses a ON a.user_id = u.id WHERE u.email = 'customer@shop.local';
INSERT INTO order_items (quantity, price_at_purchase, product_snapshot, order_id, product_id)
SELECT 1, COALESCE(p.discount_price, p.base_price), jsonb_build_object('name', p.name, 'sku', p.sku), o.id, p.id
FROM orders o, products p WHERE o.order_number = 'ORD-2026-0004' AND p.sku = 'IPH15';
INSERT INTO order_items (quantity, price_at_purchase, product_snapshot, order_id, product_id)
SELECT 1, COALESCE(p.discount_price, p.base_price), jsonb_build_object('name', p.name, 'sku', p.sku), o.id, p.id
FROM orders o, products p WHERE o.order_number = 'ORD-2026-0004' AND p.sku = 'MAGSAFE';
INSERT INTO order_items (quantity, price_at_purchase, product_snapshot, order_id, product_id)
SELECT 1, COALESCE(p.discount_price, p.base_price), jsonb_build_object('name', p.name, 'sku', p.sku), o.id, p.id
FROM orders o, products p WHERE o.order_number = 'ORD-2026-0004' AND p.sku = 'APP2';
UPDATE orders o SET subtotal = sub.s,
  shipping_cost = CASE WHEN sub.s >= COALESCE(d.free_threshold, 1e12) THEN 0 ELSE d.base_price END,
  discount = CASE WHEN o.promo_code = 'WELCOME10' THEN ROUND(LEAST(sub.s * 0.10, 100), 2) ELSE 0 END
FROM (SELECT order_id, SUM(quantity * price_at_purchase) AS s FROM order_items GROUP BY order_id) sub, delivery_options d
WHERE sub.order_id = o.id AND d.code = o.delivery_method AND o.order_number = 'ORD-2026-0004';
UPDATE orders SET total_amount = subtotal + shipping_cost - discount WHERE order_number = 'ORD-2026-0004';
INSERT INTO payments (transaction_id, payment_method, status, amount, currency, card_last_four_digits, paid_at, error_code, error_message, order_id)
SELECT 'TXN-0004', 'CARD', 'COMPLETED', total_amount, 'USD', '4242', paid_at, NULL, NULL, id
FROM orders WHERE order_number = 'ORD-2026-0004';
INSERT INTO orders (order_number, status, delivery_method, shipping_country, shipping_city, shipping_street, shipping_building, shipping_apartment, shipping_postal_code, shipping_recipient_name, shipping_recipient_phone, promo_code, user_id, created_at, paid_at, shipped_at, delivered_at)
SELECT 'ORD-2026-0005', 'CANCELLED', 'POST_OFFICE', a.country, a.city, a.street, a.building, a.apartment, a.postal_code, a.recipient_name, a.recipient_phone, NULL, u.id,
 NOW() - INTERVAL '2 days',
 NULL,
 NULL,
 NULL
FROM users u JOIN addresses a ON a.user_id = u.id WHERE u.email = 'customer@shop.local';
INSERT INTO order_items (quantity, price_at_purchase, product_snapshot, order_id, product_id)
SELECT 1, COALESCE(p.discount_price, p.base_price), jsonb_build_object('name', p.name, 'sku', p.sku), o.id, p.id
FROM orders o, products p WHERE o.order_number = 'ORD-2026-0005' AND p.sku = 'GBK4';
UPDATE orders o SET subtotal = sub.s,
  shipping_cost = CASE WHEN sub.s >= COALESCE(d.free_threshold, 1e12) THEN 0 ELSE d.base_price END,
  discount = CASE WHEN o.promo_code = 'WELCOME10' THEN ROUND(LEAST(sub.s * 0.10, 100), 2) ELSE 0 END
FROM (SELECT order_id, SUM(quantity * price_at_purchase) AS s FROM order_items GROUP BY order_id) sub, delivery_options d
WHERE sub.order_id = o.id AND d.code = o.delivery_method AND o.order_number = 'ORD-2026-0005';
UPDATE orders SET total_amount = subtotal + shipping_cost - discount WHERE order_number = 'ORD-2026-0005';
INSERT INTO payments (transaction_id, payment_method, status, amount, currency, card_last_four_digits, paid_at, error_code, error_message, order_id)
SELECT NULL, 'CARD', 'FAILED', total_amount, 'USD', '4242', NULL, 'CARD_DECLINED', 'Card was declined', id
FROM orders WHERE order_number = 'ORD-2026-0005';
INSERT INTO reviews (rating, title, comment, is_verified_purchase, status, product_id, user_id, created_at)
SELECT 5, 'Rated 5/5', 'Demo review for ' || p.name, TRUE, 'APPROVED', p.id, u.id, NOW() - INTERVAL '5 days'
FROM products p, users u WHERE p.sku = 'IPH15' AND u.email = 'customer@shop.local';
INSERT INTO reviews (rating, title, comment, is_verified_purchase, status, product_id, user_id, created_at)
SELECT 4, 'Rated 4/5', 'Demo review for ' || p.name, FALSE, 'APPROVED', p.id, u.id, NOW() - INTERVAL '4 days'
FROM products p, users u WHERE p.sku = 'GS24' AND u.email = 'customer@shop.local';
INSERT INTO reviews (rating, title, comment, is_verified_purchase, status, product_id, user_id, created_at)
SELECT 5, 'Rated 5/5', 'Demo review for ' || p.name, FALSE, 'APPROVED', p.id, u.id, NOW() - INTERVAL '5 days'
FROM products p, users u WHERE p.sku = 'MBA13' AND u.email = 'customer@shop.local';
INSERT INTO reviews (rating, title, comment, is_verified_purchase, status, product_id, user_id, created_at)
SELECT 5, 'Rated 5/5', 'Demo review for ' || p.name, FALSE, 'APPROVED', p.id, u.id, NOW() - INTERVAL '5 days'
FROM products p, users u WHERE p.sku = 'WH1000XM5' AND u.email = 'customer@shop.local';
INSERT INTO reviews (rating, title, comment, is_verified_purchase, status, product_id, user_id, created_at)
SELECT 4, 'Rated 4/5', 'Demo review for ' || p.name, TRUE, 'APPROVED', p.id, u.id, NOW() - INTERVAL '4 days'
FROM products p, users u WHERE p.sku = 'APP2' AND u.email = 'customer@shop.local';
INSERT INTO reviews (rating, title, comment, is_verified_purchase, status, product_id, user_id, created_at)
SELECT 3, 'Rated 3/5', 'Demo review for ' || p.name, FALSE, 'APPROVED', p.id, u.id, NOW() - INTERVAL '3 days'
FROM products p, users u WHERE p.sku = 'GBK4' AND u.email = 'customer@shop.local';
INSERT INTO reviews (rating, title, comment, is_verified_purchase, status, product_id, user_id, created_at)
SELECT 4, 'Rated 4/5', 'Demo review for ' || p.name, TRUE, 'APPROVED', p.id, u.id, NOW() - INTERVAL '4 days'
FROM products p, users u WHERE p.sku = 'MAGSAFE' AND u.email = 'customer@shop.local';
INSERT INTO reviews (rating, title, comment, is_verified_purchase, status, product_id, user_id, created_at)
SELECT 5, 'Rated 5/5', 'Demo review for ' || p.name, FALSE, 'APPROVED', p.id, u.id, NOW() - INTERVAL '5 days'
FROM products p, users u WHERE p.sku = 'AP20W' AND u.email = 'customer@shop.local';
INSERT INTO reviews (rating, title, comment, is_verified_purchase, status, product_id, user_id, created_at)
SELECT 2, 'Rated 2/5', 'Demo review for ' || p.name, FALSE, 'PENDING', p.id, u.id, NOW() - INTERVAL '2 days'
FROM products p, users u WHERE p.sku = 'GS24U' AND u.email = 'customer@shop.local';
INSERT INTO reviews (rating, title, comment, is_verified_purchase, status, product_id, user_id, created_at)
SELECT 1, 'Rated 1/5', 'Demo review for ' || p.name, FALSE, 'REJECTED', p.id, u.id, NOW() - INTERVAL '1 days'
FROM products p, users u WHERE p.sku = 'SRSXB100' AND u.email = 'customer@shop.local';

UPDATE products p SET average_rating = r.a, review_count = r.c
FROM (SELECT product_id, ROUND(AVG(rating), 2) AS a, COUNT(*) AS c FROM reviews WHERE status = 'APPROVED' GROUP BY product_id) r
WHERE r.product_id = p.id;

